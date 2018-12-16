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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Stores object factories using different scopes.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Factory {
  // contains providers that require a name and use it as a function parameter
  private final Map<Class<?>, Function<Object, ?>> namedProviders = new HashMap<>();
  // contains providers that can require a name, but in this case there will be
  // a separate provider for each name (or no name)
  private final Map<Key<?>, Supplier<?>> providers = new HashMap<>();

  /**
   * Unregisters the factory for the passed class.
   */
  public <T> void unregister(Class<T> cls) {
    unregister(cls, null);
    namedProviders.remove(cls);
  }

  /**
   * Unregisters the factory for the passed class and name.
   */
  public <T> void unregister(Class<T> cls, Object name) {
    Key<T> key = new Key<>(cls, name);
    providers.remove(key);
  }

  /**
   * Registers a singleton for the passed class.
   */
  public <T> void registerSingleton(Class<T> cls, T instance) {
    registerSingleton(cls, null, instance);
  }

  /**
   * Registers a singleton for the passed class and name.
   */
  public <T> void registerSingleton(Class<T> cls, Object name, T instance) {
    registerProvider(cls, name, () -> instance);
  }

  /**
   * Registers a singleton factory for the passed class.
   */
  public <T> void registerLazySingleton(Class<T> cls,
      Supplier<T> initialProvider) {
    registerLazySingleton(cls, null, initialProvider);
  }

  /**
   * Registers a singleton factory for the passed class and name. The singleton
   * object will be instantiated when requested.
   *
   * @param initialProvider used once to create the singleton
   */
  public <T> void registerLazySingleton(Class<T> cls, Object name,
      Supplier<T> initialProvider) {
    Key<T> key = new Key<>(cls, name);
    providers.put(key, new LazySupplier(key, initialProvider));
  }

  /**
   * Registers a provider for the passed class.
   */
  public <T> void registerProvider(Class<T> cls, Supplier<T> provider) {
    registerProvider(cls, null, provider);
  }

  /**
   * Registers a provider for the passed class and name. That provider will be
   * called each time an instance is requested.
   */
  public <T> void registerProvider(Class<T> cls, Object name,
      Supplier<T> provider) {
    providers.put(new Key<>(cls, name), provider);
  }

  /**
   * Registers a thread-scoped singleton provider for the passed class.
   */
  public <T> void registerThreadLocal(Class<T> cls, Supplier<T> provider) {
    registerThreadLocal(cls, null, provider);
  }

  /**
   * Registers a thread-scoped singleton provider for the passed class and name.
   *
   * @param provider will be called once for each thread
   */
  public <T> void registerThreadLocal(Class<T> cls, Object name,
      Supplier<T> provider) {
    // ThreadLocal does exactly what we need
    ThreadLocal<T> tl = new ThreadLocal<T>() {
      @Override
      protected T initialValue() {
        return provider.get();
      }
    };

    providers.put(new Key<>(cls, name), tl::get);
  }

  /**
   * Registers a provider that uses a name as parameter to generate instances.
   */
  public <T> void registerNamedProvider(Class<T> cls,
      Function<Object, T> provider) {
    namedProviders.put(cls, provider);
  }

  /**
   * Obtains an instance of the passed class, if a provider is available.
   */
  public <T> T obtain(Class<T> cls) {
    return obtain(cls, null);
  }

  /**
   * Obtains an instance of the passed class and name, if a provider is
   * available.
   */
  public <T> T obtain(Class<T> cls, Object name) {
    // direct providers take priority
    Supplier<T> provider = (Supplier<T>) providers.get(new Key<>(cls, name));

    if (provider != null) {
      return provider.get();
    } else if (name != null) {
      // alternatively, try to find a named provider for that class
      Function<Object, T> namedProvider
          = (Function<Object, T>) namedProviders.get(cls);

      if (namedProvider != null) {
        return namedProvider.apply(name);
      }
    }

    return null;
  }

  private static class Key<T> {
    private final Class<T> cls;
    private final Object name;

    private Key(Class<T> cls, Object name) {
      this.cls = cls;
      this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final Key other = (Key) obj;
      if (!Objects.equals(this.cls, other.cls)) {
        return false;
      }
      return Objects.equals(this.name, other.name);
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 23 * hash + Objects.hashCode(this.cls);
      hash = 23 * hash + Objects.hashCode(this.name);
      return hash;
    }
  }

  private class LazySupplier<T> implements Supplier<T> {
    private final Supplier<T> initialProvider;
    private final Key<T> key;
    private T instance;

    public LazySupplier(Key<T> key, Supplier<T> initialProvider) {
      this.key = key;
      this.initialProvider = initialProvider;
    }

    @Override
    public T get() {
      // if not synchronized, the singleton provider might be called more than
      // once before the generated instance has been stored
      synchronized (initialProvider) {
        if (instance == null) {
          instance = initialProvider.get();
          // replace the provider so future requests won't be synchronized
          providers.put(key, () -> instance);
        }

        return instance;
      }
    }
  }
}
