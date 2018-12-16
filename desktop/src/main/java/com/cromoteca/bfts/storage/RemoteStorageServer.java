/*
 * Copyright (C) 2014-2019 Luciano Vernaschi (luciano at cromoteca.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.cromoteca.bfts.storage;

import com.cromoteca.bfts.cryptography.Cryptographer;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.util.ArrayOfBytesInputStream;
import com.cromoteca.bfts.util.ArrayOfBytesOutputStream;
import com.cromoteca.bfts.util.lambdas.IOSupplier;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.net.HttpURLConnection.*;

/**
 * Publishes a {@link LocalStorage} on an HTTP port.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class RemoteStorageServer implements HttpHandler {
  private static final Logger log
      = LoggerFactory.getLogger(RemoteStorageServer.class);
  private static final byte[] ECHO_RESPONSE = "BFTS server listening".getBytes();
  private final LocalStorage localStorage;
  // contains methods of the Storage interface
  private final Map<String, Method> methods;

  /**
   * Creates a new remote storage
   *
   * @param localStorage the storage to publish
   */
  public RemoteStorageServer(LocalStorage localStorage) {
    this.localStorage = localStorage;

    methods = new HashMap<String, Method>() {
      {
        for (Method method : Storage.class.getDeclaredMethods()) {
          put(method.getName(), method);
        }
      }
    };
  }

  /**
   * Starts the HTTP server
   *
   * @param port port to use
   * @return an object that can be used to stop the server after a timeout
   */
  public IntConsumer startHTTPServer(int port) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext("/", this);
    server.start();

    return seconds -> server.stop(seconds);
  }

  @Override
  public void handle(HttpExchange e) throws IOException {
    String methodName = e.getRequestURI().getPath().replace("/", "");
    log.debug("Called method {}", methodName);

    switch (methodName) {
      // not a member of Storage: used to send encryption keys
      case "getInitialInfo":
        StorageConfiguration config = localStorage.getStorageConfiguration();
        e.sendResponseHeaders(HTTP_OK, 0);
        try (ArrayOfBytesOutputStream os
            = new ArrayOfBytesOutputStream(e.getResponseBody())) {
          os.writeArrayOfBytes(config.getEncryptedPublicKey());
          os.writeArrayOfBytes(config.getSalt());
        }

        break;

      // a simple method to test if the server is alive
      case "echo":
        e.sendResponseHeaders(HTTP_OK, 0);
        e.getResponseBody().write(ECHO_RESPONSE);
        e.close();

        break;

      default:
        Method method = methods.get(methodName);

        if (method == null) {
          e.sendResponseHeaders(HTTP_NOT_FOUND, 0);
        } else {
          handleMethod(e, method);
        }

        break;
    }
  }

  private void handleMethod(HttpExchange e, Method method) throws IOException {
    Object[] arguments;
    Cryptographer crypto;
    Object result = null;
    int returnCode = HTTP_OK;

    try (ArrayOfBytesInputStream is
        = new ArrayOfBytesInputStream(e.getRequestBody())) {
      // read key from client, that used it to encrypt method arguments
      byte[] key = is.readArrayOfBytes();

      try {
        // decrypt key using private key
        key = Cryptographer.decrypt(key, localStorage.getEncodedPrivateKey());
      } catch (GeneralSecurityException ex) {
        throw new IOException(ex);
      }

      crypto = new Cryptographer(key);
      int argumentCount = is.readInt();
      arguments = new Object[argumentCount];

      for (int i = 0; i < argumentCount; i++) {
        Type paramType = method.getGenericParameterTypes()[i];

        // last argument might be a supplier: it will read all available data
        // from the incoming connection when requested
        if (paramType.getTypeName().startsWith(IOSupplier.class.getName())) {
          IOSupplier<byte[]> supplier = () -> {
            try {
              byte[] data = is.readArrayOfBytes();
              return crypto.decrypt(data);
            } catch (EOFException ex) {
              return null;
            } catch (GeneralSecurityException ex) {
              log.error(null, ex);
              return null;
            }
          };

          arguments[i] = supplier;
        } else {
          // not a supplier: just read, decrypt and deserialize
          byte[] data = is.readArrayOfBytes();

          try {
            data = crypto.decrypt(data);
          } catch (GeneralSecurityException ex) {
            log.error(null, ex);
            returnCode = HTTP_FORBIDDEN;
          }

          arguments[i] = Serialization.deserialize(paramType, data);
        }
      }

      // we have all arguments: invoke the requested method and get the return
      // object
      try {
        result = method.invoke(localStorage, arguments);
      } catch (Exception ex) {
        log.error(null, ex);
        returnCode = HTTP_INTERNAL_ERROR;
      }
    }

    e.sendResponseHeaders(returnCode, 0);

    if (returnCode == HTTP_OK) {
      try (ArrayOfBytesOutputStream os
          = new ArrayOfBytesOutputStream(e.getResponseBody())) {
        String typeName = method.getGenericReturnType().getTypeName();

        // if method returns a supplier, write all data it can produce
        if (typeName.startsWith(IOSupplier.class.getName())) {
          IOSupplier<byte[]> supplier = (IOSupplier<byte[]>) result;
          byte[] data;

          while ((data = supplier.get()) != null) {
            try {
              data = crypto.encrypt(data);
            } catch (GeneralSecurityException ex) {
              log.error(null, ex);
              return;
            }

            os.writeArrayOfBytes(data);
          }
        } else {
          // serialize, encrypt and write a normal return object
          byte[] data = Serialization.serialize(result);

          try {
            data = crypto.encrypt(data);
          } catch (GeneralSecurityException ex) {
            log.error(null, ex);
            return;
          }

          os.writeArrayOfBytes(data);
        }
      }
    }
  }
}
