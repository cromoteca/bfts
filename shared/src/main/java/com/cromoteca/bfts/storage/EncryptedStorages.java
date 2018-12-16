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
import com.googlecode.openbeans.Introspector;
import com.googlecode.openbeans.PropertyDescriptor;
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
    Container<Cryptographer> cryptographer = new Container<>(() -> {
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
          .map(o -> doEncryptionDecryption(o, cryptographer.getValue(), true,
          encryptStrings))
          .toArray(Object[]::new);

      Object returnValue = m.invoke(storage, args);
      returnValue = doEncryptionDecryption(returnValue,
          cryptographer.getValue(), false, encryptStrings);
      return returnValue;
    });
  }

  private static <T> T doEncryptionDecryption(T t, Cryptographer c, boolean enc,
      boolean strings) {
    try {
      if (t == null) {
        return null;
      } else if (t instanceof byte[]) {
        t = (T) (enc ? c.encrypt((byte[]) t) : c.decrypt((byte[]) t));
      } else if (t instanceof String) {
        if (strings) {
          t = (T) (enc ? c.encrypt((String) t) : c.decrypt((String) t));
        }
      } else if (t instanceof List<?>) {
        Method get = List.class.getMethod("get", int.class);
        Method set = List.class.getMethod("set", int.class, Object.class);
        List<?> list = (List<?>) t;

        for (int i = 0; i < list.size(); i++) {
          Object item = get.invoke(list, i);
          item = doEncryptionDecryption(item, c, enc, strings);
          set.invoke(list, i, item);
        }
      } else if (t.getClass().isArray()) {
        for (int i = 0; i < Array.getLength(t); i++) {
          Object item = Array.get(t, i);
          item = doEncryptionDecryption(item, c, enc, strings);
          Array.set(t, i, item);
        }
      } else {
        PropertyDescriptor[] propertyDescriptors
            = Introspector.getBeanInfo(t.getClass()).getPropertyDescriptors();

        for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
          Method readMethod = propertyDescriptor.getReadMethod();
          Method writeMethod = propertyDescriptor.getWriteMethod();

          if (readMethod != null && writeMethod != null) {
            Object value = readMethod.invoke(t);
            Object treated = doEncryptionDecryption(value, c, enc, strings);
            writeMethod.invoke(t, treated);
          }
        }
      }

      return t;
    } catch (Exception ex) {
      throw new StorageException(ex);
    }
  }
}
