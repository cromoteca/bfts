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

import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows to set and retrieve a single value. Normally used to set and retrieve
 * a value while in a lambda exception, where the value can't be set directly
 * because it must be effectively final to be used in the lambda body.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Container<T> {
  private static final Logger log = LoggerFactory.getLogger(Container.class);
  private T value;
  private Supplier<T> supplier;

  /**
   * Creates a new empty container.
   */
  public Container() {
  }

  /**
   * Creates a container that stores the passed value initially.
   */
  public Container(T value) {
    this.value = value;
  }

  public Container(Supplier<T> supplier) {
    this.supplier = supplier;
  }

  /**
   * Gets the current value.
   */
  public T getValue() {
    if (supplier != null) {
      try {
        value = supplier.get();
        supplier = null;
      } catch (RuntimeException ex) {
        log.warn(null, ex);
      }
    }

    return value;
  }

  /**
   * Sets a new value.
   */
  public void setValue(T value) {
    this.value = value;
    supplier = null;
  }

  /**
   * Returns true if empty.
   */
  public boolean isEmpty() {
    return getValue() == null;
  }

  /**
   * Returns true if full.
   */
  public boolean isFull() {
    return getValue() != null;
  }

  @Override
  public String toString() {
    return Objects.toString(getValue());
  }
}
