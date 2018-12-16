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

/**
 * Helper class used to count something.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Counter {
  private long n;

  /**
   * Creates a new counter, starting from zero.
   */
  public Counter() {
  }

  /**
   * Creates a new counter, starting from the passed value.
   */
  public Counter(long initialValue) {
    n = initialValue;
  }

  /**
   * Resets to zero.
   */
  public void reset() {
    n = 0;
  }

  /**
   * Adds the passed value to the total.
   *
   * @return the updated total
   */
  public long add(long l) {
    return n += l;
  }

  /**
   * Adds one to the total
   *
   * @return the updated total
   */
  public long increment() {
    return ++n;
  }

  /**
   * Returns the current total.
   */
  public long get() {
    return n;
  }

  /**
   * Reset the counter to the passed value.
   */
  public void set(long value) {
    n = value;
  }

  /**
   * Returns the current total as int.
   *
   * @throws IllegalStateException if the current total cannot be cast to int
   */
  public int asInt() {
    if (n > Integer.MAX_VALUE || n < Integer.MIN_VALUE) {
      throw new IllegalStateException(n + " is out of int value range");
    }

    return (int) n;
  }

  @Override
  public String toString() {
    return Long.toString(n);
  }
}
