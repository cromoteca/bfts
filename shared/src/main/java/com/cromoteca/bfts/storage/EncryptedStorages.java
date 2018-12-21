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
import com.cromoteca.bfts.util.Container;
import com.cromoteca.bfts.util.lambdas.IOSupplier;
import com.googlecode.openbeans.Introspector;
import com.googlecode.openbeans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class EncryptedStorages {
  public static void main(String[] args) throws Exception {
    System.out.println(Arrays.class.getMethod("asList", Object[].class));
    Method[] methods = Arrays.class.getMethods();
    for (Method method : methods) {
      if (method.getName().equals("asList")) {
        System.out.println(Arrays.toString(method.getParameters()));
      }
    }
  }

  public static Storage getEncryptedStorage(Storage storage,
      char[] password, boolean encryptStrings) {
    Container<Cryptographer> crypto = new Container<>(() -> {
      byte[] salt = storage.getStorageConfiguration().getSalt();
      try {
        return new Cryptographer(salt, password);
      } catch (GeneralSecurityException ex) {
        throw new StorageException(ex);
      }
    });

    ClassLoader loader = EncryptedStorages.class.getClassLoader();
    Class[] storageClass = new Class[] { Storage.class };

    return (Storage) Proxy.newProxyInstance(loader, storageClass, (p, m, a) -> {
      Object[] args = Arrays.stream(a)
          .map(o -> doEncryptionDecryption(o, crypto.getValue(), true,
          encryptStrings))
          .toArray(Object[]::new);

      Object returnValue = m.invoke(storage, args);
      returnValue = doEncryptionDecryption(returnValue,
          crypto.getValue(), false, encryptStrings);
      return returnValue;
    });
  }

  private static <T> T doEncryptionDecryption(T t, Cryptographer crypto,
      boolean enc, boolean strings) {
    try {
      if (t == null) {
        return null;
      } else if (t instanceof byte[]) {
        t = (T) (enc ? crypto.encrypt((byte[]) t) : crypto.decrypt((byte[]) t));
      } else if (t instanceof String) {
        if (strings) {
          t = (T) (enc ? crypto.encrypt((String) t) : crypto.decrypt((String) t));
        }
      } else if (t instanceof List<?>) {
        Method get = List.class.getMethod("get", int.class);
        Method set = List.class.getMethod("set", int.class, Object.class);
        List<?> list = (List<?>) t;

        for (int i = 0; i < list.size(); i++) {
          Object item = get.invoke(list, i);
          item = doEncryptionDecryption(item, crypto, enc, strings);
          set.invoke(list, i, item);
        }
      } else if (t.getClass().isArray()) {
        for (int i = 0; i < Array.getLength(t); i++) {
          Object item = Array.get(t, i);
          item = doEncryptionDecryption(item, crypto, enc, strings);
          Array.set(t, i, item);
        }
      } else if (t instanceof IOSupplier<?>) {
        t = (T) new EncryptedIOSupplier((IOSupplier<byte[]>) t, crypto, enc);
      } else {
        PropertyDescriptor[] propertyDescriptors
            = Introspector.getBeanInfo(t.getClass()).getPropertyDescriptors();

        for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
          Method readMethod = propertyDescriptor.getReadMethod();
          Method writeMethod = propertyDescriptor.getWriteMethod();

          if (readMethod != null && writeMethod != null) {
            Object value = readMethod.invoke(t);
            Object treated = doEncryptionDecryption(value, crypto, enc, strings);
            writeMethod.invoke(t, treated);
          }
        }
      }

      return t;
    } catch (Exception ex) {
      throw new StorageException(ex);
    }
  }

  public static class EncryptedIOSupplier implements IOSupplier<byte[]> {
    private IOSupplier<byte[]> origin;
    private Cryptographer crypto;
    private boolean encrypt;

    public EncryptedIOSupplier(IOSupplier<byte[]> origin, Cryptographer crypto,
        boolean encrypt) {
      this.origin = origin;
      this.crypto = crypto;
      this.encrypt = encrypt;
    }

    @Override
    public byte[] get() throws IOException {
      try {
        return encrypt ? crypto.encrypt(origin.get())
            : crypto.decrypt(origin.get());
      } catch (GeneralSecurityException ex) {
        throw new StorageException(ex);
      }
    }

    @Override
    public void end() throws IOException {
      origin.end();
    }
  }
}
