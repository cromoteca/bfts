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
import com.cromoteca.bfts.util.ArrayOfBytesInputStream;
import com.cromoteca.bfts.util.ArrayOfBytesOutputStream;
import com.cromoteca.bfts.util.lambdas.IOSupplier;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote storage builder: allows to connect to storages published on other
 * machines.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class RemoteStorage {
  private static final Logger log = LoggerFactory.getLogger(RemoteStorage.class);
  private static final int TIMEOUT = 120_000;
  private String baseURL;
  private byte[] encryptedPublicKey;
  private Cryptographer keyEncrypter;

  private RemoteStorage() {
  }

  /**
   * Initializes a new remote storage.
   *
   * @param host     remote host
   * @param port     remote port
   * @param password encryption password
   * @return the storage
   */
  public static Storage create(String host, int port, char[] password)
      throws IOException {
    RemoteStorage remoteStorage = new RemoteStorage();
    remoteStorage.baseURL = "http://" + host + ':' + port + '/';

    ClassLoader loader = RemoteStorage.class.getClassLoader();
    Class[] storage = new Class[] { Storage.class };

    // return a proxy that will redirect method calls to HTTP
    Storage proxy
        = (Storage) Proxy.newProxyInstance(loader, storage, (p, m, a) -> {
          if (remoteStorage.keyEncrypter == null) {
            remoteStorage.configure(password);
          }

          return remoteStorage.call(m.getGenericReturnType(), m.getName(), a);
        });

    return proxy;
  }

  private void configure(char[] password)
      throws IOException, GeneralSecurityException {
    // call getInitialInfo to get data required for further connections
    URL url = new URL(baseURL + "getInitialInfo");
    URLConnection conn = url.openConnection();

    try (ArrayOfBytesInputStream is
        = new ArrayOfBytesInputStream(conn.getInputStream())) {
      // encrypted public key: must be decrypted using password
      // TODO: this key will be decrypted every time it will be used. It could
      // be decrypted once
      encryptedPublicKey = is.readArrayOfBytes();
      byte[] salt = is.readArrayOfBytes();
      // will encrypt connection keys
      keyEncrypter = new Cryptographer(salt, password);
    }
  }

  private Object call(Type returnType, String methodName, Object... arguments)
      throws IOException, GeneralSecurityException {
    log.debug("Calling method {}", methodName);

    if (arguments == null) {
      arguments = new Object[0];
    }

    // key used to encrypt data sent during this connection
    byte[] randomKey = new byte[Cryptographer.KEY_SIZE];
    SecureRandom secRandom = new SecureRandom();
    secRandom.nextBytes(randomKey);
    Cryptographer cryptographer = new Cryptographer(randomKey);
    // that key will be encrypted using the public key received from server
    byte[] encryptedRandomKey
        = keyEncrypter.encrypt(randomKey, encryptedPublicKey);

    URL url = new URL(baseURL + methodName);
    URLConnection conn = url.openConnection();
    conn.setConnectTimeout(TIMEOUT);
    conn.setReadTimeout(TIMEOUT);
    conn.setDoOutput(true);

    try (ArrayOfBytesOutputStream os
        = new ArrayOfBytesOutputStream(conn.getOutputStream())) {
      // send key
      os.writeArrayOfBytes(encryptedRandomKey);
      // send number of arguments
      os.writeInt(arguments.length);

      for (Object o : arguments) {
        if (o instanceof IOSupplier<?>) {
          // last argument can be a supplier of byte arrays
          IOSupplier<byte[]> supplier = (IOSupplier<byte[]>) o;
          byte[] data;

          // encrypt and send all supplied byte arrays
          while ((data = supplier.get()) != null) {
            data = cryptographer.encrypt(data);
            os.writeArrayOfBytes(data);
          }
        } else {
          // other arguments will be serialized and encrypted
          byte[] data = Serialization.serialize(o);
          data = cryptographer.encrypt(data);
          os.writeArrayOfBytes(data);
        }
      }

      log.debug("Method {}: {} bytes sent", methodName, os.size());
    }

    if (returnType.toString().startsWith(IOSupplier.class.getName())) {
      ArrayOfBytesInputStream is
          = new ArrayOfBytesInputStream(conn.getInputStream());

      // create a supplier that reads all available data from HTTP
      IOSupplier<byte[]> supplier = new IOSupplier<byte[]>() {
        long total = 0;

        @Override
        public byte[] get() throws IOException {
          try {
            byte[] data = is.readArrayOfBytes();
            total += data.length;
            log.debug("Method {}: {} bytes read (partial)", methodName,
                data.length);
            return cryptographer.decrypt(data);
          } catch (EOFException ex) {
            return null;
          } catch (GeneralSecurityException ex) {
            log.error(null, ex);
            return null;
          }
        }

        @Override
        public void end() throws IOException {
          is.close();
          log.debug("Method {}: {} bytes read", methodName, total);
        }
      };

      return supplier;
    } else {
      try (ArrayOfBytesInputStream is
          = new ArrayOfBytesInputStream(conn.getInputStream())) {
        // read return object
        byte[] data = is.readArrayOfBytes();
        log.debug("Method {}: {} bytes read", methodName, data.length);
        data = cryptographer.decrypt(data);
        return Serialization.deserialize(returnType, data);
      }
    }
  }
}
