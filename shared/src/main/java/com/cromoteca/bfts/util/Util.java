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

import com.cromoteca.bfts.util.lambdas.IOConsumer;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;

/**
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Util {
  private static final Factory durationFactory;

  static {
    durationFactory = new Factory();
    durationFactory.registerThreadLocal(TaskDuration.class, TaskDuration::new);
  }

  /**
   * Checks whether the passed string can be used as a valid name for backup
   * clients and sources.
   */
  public static boolean validName(String s) {
    Pattern p = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9_-]+[A-Za-z0-9])*");
    Matcher m = p.matcher(s);
    return m.matches();
  }

  /**
   * Constrains a number between a minimum and a maximum.
   */
  public static long constrain(long num, long minimum, long maximum) {
    return Math.max(Math.min(num, maximum), minimum);
  }

  /**
   * Writes some runtime information to console.
   */
  public static void dumpRuntime() {
    RuntimeMXBean rmxb = ManagementFactory.getRuntimeMXBean();

    for (Method method : rmxb.getClass().getMethods()) {
      String name = method.getName();

      if (name.startsWith("get") && method.getParameterCount() == 0) {
        method.setAccessible(true);

        try {
          System.out.format("%s: %s\n", name, method.invoke(rmxb));
        } catch (Exception ex) {
          System.err.format("Error invoking %s: %s", name, ex.getMessage());
        }
      }
    }
  }

  /**
   * Measures time required to execute methods of an object that implements at
   * least one interface. Only methods from implemented interfaces can be
   * measured.
   *
   * @param <T>              object type
   * @param object           object to be measured
   * @param durationConsumer will be notified about times
   * @return a proxy implementing the same interfaces implemented by the
   *         provided object instance
   */
  public static <T> T measure(T object,
      BiConsumer<String, TaskDuration> durationConsumer) {
    ClassLoader loader = object.getClass().getClassLoader();
    Class<?>[] classArray = object.getClass().getInterfaces();

    return (T) Proxy.newProxyInstance(loader, classArray, (p, m, a) -> {
      TaskDuration duration = durationFactory.obtain(TaskDuration.class);
      duration.restart();
      Object returnValue = m.invoke(object, a);
      durationConsumer.accept(m.getName(), duration);
      return returnValue;
    });
  }

  public static <T> T traceMethodCalls(T object, Logger log) {
    ClassLoader loader = object.getClass().getClassLoader();
    Class<?>[] classArray = object.getClass().getInterfaces();

    return (T) Proxy.newProxyInstance(loader, classArray, (p, m, a) -> {
      Object returnValue;

      String message = String.format("Method %s called with arguments %s",
          m.getName(), Arrays.toString(a));

      try {
        returnValue = m.invoke(object, a);
      } catch (InvocationTargetException ex) {
        log.trace("{} threw execption {}", message, ex.getMessage());
        throw ex;
      }

      log.trace("{} returned value {}", message, returnValue);
      return returnValue;
    });
  }

  /**
   * Consumes a stream without having to rethrow IOExceptions as
   * RuntimeExceptions. It uses Stream.forEachOrdered since we need the
   * happens-before contract to be sure that elements that follow an IOException
   * are not processed.
   */
  public static <T> void consumeIO(Stream<T> stream, IOConsumer<T> consumer)
      throws IOException {
    Container<IOException> ioex = new Container<>();

    stream.forEachOrdered(p -> {
      if (ioex.isEmpty()) {
        try {
          consumer.accept(p);
        } catch (IOException ex) {
          ioex.setValue(ex);
        }
      }
    });

    if (ioex.isFull()) {
      throw ioex.getValue();
    }
  }

  /**
   * Used in lambda expressions to sort according to a method of the stream
   * member class. For example, to sort a list of paths by file name, you can
   * write:
   *
   * <pre>paths.sorted(orderBy(Path::getFileName))</pre>
   */
  public static <T, R extends Comparable> Comparator<T> orderBy(CheckedFunction<T, R> f) {
    return (o1, o2) -> {
      try {
        return Objects.compare(f.apply(o1), f.apply(o2),
            Comparator.naturalOrder());
      } catch (Exception ex) {
        if (ex instanceof RuntimeException) {
          throw (RuntimeException) ex;
        } else {
          throw new RuntimeException(ex);
        }
      }
    };
  }

  @FunctionalInterface
  public interface CheckedFunction<T, R> {
    R apply(T t) throws Exception;
  }

  public static <T> Predicate<? super T> not(Predicate<? super T> predicate) {
    return predicate.negate();
  }

  public static IntPredicate not(IntPredicate predicate) {
    return predicate.negate();
  }

  public static <K, V> void updateMap(Map<K, V> mapToModify, Collection<K> keys,
      Function<K, V> valueGenerator, Consumer<V> valueRecycler) {
    Set<K> newKeys = new HashSet<>(keys);

    mapToModify.keySet().removeIf(key -> {
      boolean obsolete = !newKeys.remove(key);

      if (obsolete) {
        valueRecycler.accept(mapToModify.get(key));
      }

      return obsolete;
    });

    for (K key : newKeys) {
      V value = valueGenerator.apply(key);
      mapToModify.put(key, value);
    }
  }

  // from https://stackoverflow.com/a/32435407
  public static <T> Stream<List<T>> ofSubLists(List<T> source, int length) {
    if (length <= 0) {
      throw new IllegalArgumentException("length = " + length);
    }

    int size = source.size();

    if (size <= 0) {
      return Stream.empty();
    }

    int fullChunks = (size - 1) / length;

    return IntStream.range(0, fullChunks + 1)
        .mapToObj(n -> source.subList(n * length,
        n == fullChunks ? size : (n + 1) * length));
  }

  public static boolean isWindows() {
    return System.getProperty("os.name").startsWith("Windows");
  }
}
