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
package com.cromoteca.bfts.util;

import com.cromoteca.bfts.model.Pair;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength;
import org.apache.commons.collections4.map.ReferenceMap;

/**
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Cache {
  private static final Double NULL_VALUE_PLACEHOLDER;

  private final ReferenceMap<String, Object> cache;
  private final ObjectMapper json;

  static {
    NULL_VALUE_PLACEHOLDER = new Random().nextDouble();
  }

  /**
   * @param cachedMethods do not provide any value if you want to cache all
   *                      methods
   */
  public Cache() {
    cache = new ReferenceMap<>(ReferenceStrength.SOFT, ReferenceStrength.HARD,
        true);
    json = new ObjectMapper();
  }

  public <T> T cached(T object, String... cachedMethods) {
    Arrays.sort(cachedMethods);
    ClassLoader loader = object.getClass().getClassLoader();
    Class<?>[] classArray = object.getClass().getInterfaces();

    return (T) Proxy.newProxyInstance(loader, classArray, (p, m, a) -> {
      List<String> params = a == null ? null : Arrays.stream(a)
          .map(Objects::toString)
          .collect(Collectors.toList());
      String method = m.getName();

      if (cachedMethods.length == 0
          || Arrays.binarySearch(cachedMethods, method) >= 0) {
        String key = json.writeValueAsString(new Pair<>(method, params));

        synchronized (cache) {
          if (cache.containsKey(key)) {
            Object value = cache.get(key);
            return value == NULL_VALUE_PLACEHOLDER ? null : value;
          }
        }

        Object value = m.invoke(object, a);
        cache.put(key, value == null ? NULL_VALUE_PLACEHOLDER : value);
        return value;
      } else {
        return m.invoke(object, a);
      }
    });
  }
}
