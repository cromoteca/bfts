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

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Caches an object for a certain amount of time.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Cached<T> {
  private Supplier<T> defaultSupplier;
  private long period;
  private T instance;
  private long last;

  public Cached() {
  }

  /**
   * Sets a default supplier (not required if using methods a provide a supplier
   * each time).
   */
  public void setDefaultSupplier(Supplier<T> defaultSupplier) {
    this.defaultSupplier = defaultSupplier;
  }

  /**
   * Sets a timeout period.
   *
   * @param period   the period
   * @param timeUnit the time unit
   */
  public void setPeriod(long period, TimeUnit timeUnit) {
    this.period = TimeUnit.MILLISECONDS.convert(period, timeUnit);
  }

  /**
   * Gets the object, using the default supplier if needed.
   */
  public T get() {
    return get(defaultSupplier);
  }

  /**
   * Gets the object, using the provided supplier if needed.
   */
  public T get(Supplier<T> supplier) {
    if (instance == null
        || (period > 0 && System.currentTimeMillis() - last > period)) {
      instance = supplier.get();
      last = System.currentTimeMillis();
    }

    return instance;
  }

  /**
   * Deletes the current object.
   */
  public void clear() {
    last = 0;
    instance = null;
  }

  /**
   * Gets a new instance, regardless of the timeout, using the default supplier.
   */
  public T getNew() {
    last = 0;
    return get();
  }

  /**
   * Gets a new instance, regardless of the timeout, using the provided
   * supplier.
   */
  public T getNew(Supplier<T> supplier) {
    last = 0;
    return get(supplier);
  }
}
