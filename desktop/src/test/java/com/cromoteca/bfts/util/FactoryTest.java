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

import com.cromoteca.bfts.testutil.TestUtils;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.Test;

import static org.junit.Assert.*;

public class FactoryTest {
  @Test
  public void testSingletons() {
    Factory f = new Factory();
    assertNull(f.obtain(Integer.class));

    // test that named singletons are stored separately
    f.registerSingleton(Integer.class, 5);
    f.registerSingleton(Integer.class, "one", 1);
    f.registerSingleton(Integer.class, "two", 2);
    assertEquals(5, (int) f.obtain(Integer.class));
    assertEquals(1, (int) f.obtain(Integer.class, "one"));
    assertEquals(2, (int) f.obtain(Integer.class, "two"));

    // test calls from different threads
    long distinct = IntStream.range(0, 5).parallel()
        .mapToLong(i -> f.obtain(Integer.class)).distinct().count();
    assertEquals(1, distinct);

    // test that the retrieved object is exactly the same
    Object o = new Object();
    f.registerSingleton(Object.class, o);
    assertEquals(o, f.obtain(Object.class));
  }

  @Test
  public void testLazy() {
    Factory f = new Factory();
    assertNull(f.obtain(Integer.class));

    // test that named lazy singletons are stored separately
    f.registerLazySingleton(Integer.class, () -> 5);
    f.registerLazySingleton(Integer.class, "one", () -> 1);
    f.registerLazySingleton(Integer.class, "two", () -> 2);
    assertEquals(5, (int) f.obtain(Integer.class));
    assertEquals(1, (int) f.obtain(Integer.class, "one"));
    assertEquals(2, (int) f.obtain(Integer.class, "two"));

    // test calls from different threads
    long distinct = IntStream.range(0, 5).parallel()
        .mapToLong(i -> f.obtain(Integer.class)).distinct().count();
    assertEquals(1, distinct);

    // test that the retrieved object is exactly the same
    f.registerLazySingleton(Object.class, Object::new);
    distinct = IntStream.range(0, 5).parallel()
        .mapToObj(i -> f.obtain(Object.class)).distinct().count();
    assertEquals(1, distinct);
  }

  @Test
  public void testSuppliers() {
    Factory f = new Factory();

    f.registerProvider(Integer.class, new Supplier<Integer>() {
      private int counter;

      @Override
      public Integer get() {
        return ++counter;
      }
    });

    f.registerProvider(Integer.class, "negative", new Supplier<Integer>() {
      private int counter;

      @Override
      public Integer get() {
        return --counter;
      }
    });

    assertEquals(1, (int) f.obtain(Integer.class));
    assertEquals(-1, (int) f.obtain(Integer.class, "negative"));
    assertEquals(2, (int) f.obtain(Integer.class));
    assertEquals(-2, (int) f.obtain(Integer.class, "negative"));
  }

  @Test
  public void testThreadLocals() {
    Factory f = new Factory();
    f.registerThreadLocal(Long.class, () -> Thread.currentThread().getId());
    Long[] ids = TestUtils.runThreads(5, () -> f.obtain(Long.class), Long[]::new);
    assertEquals(5, Arrays.stream(ids).distinct().count());
  }

  @Test
  public void testNamedProviders() {
    Factory f = new Factory();
    f.registerSingleton(String.class, "Roma", "roma");
    assertEquals("roma", f.obtain(String.class, "Roma"));
    assertNull(f.obtain(String.class, "London"));
    f.registerNamedProvider(String.class, o -> o.toString().toUpperCase());
    assertEquals("LONDON", f.obtain(String.class, "London"));
    assertEquals("roma", f.obtain(String.class, "Roma"));
  }

  @Test
  public void testUnregister() {
    Factory f = new Factory();

    f.registerSingleton(Integer.class, 5);
    f.registerSingleton(Integer.class, "one", 1);
    assertEquals(5, (int) f.obtain(Integer.class));
    assertEquals(1, (int) f.obtain(Integer.class, "one"));

    f.unregister(Integer.class);
    assertNull(f.obtain(Integer.class));
    assertEquals(1, (int) f.obtain(Integer.class, "one"));

    f.unregister(Integer.class, "one");
    assertNull(f.obtain(Integer.class, "one"));
  }
}
