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

import java.util.Locale;

/**
 * Measures the duration of a task, with the option to use a timeout. This class
 * does not manage the measured task: it just provides elapsed time and timeout
 * checking.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class TaskDuration {
  private long start;
  private long timeout;

  /**
   * Starts a time measurement without timeout.
   */
  public TaskDuration() {
    restart();
  }

  /**
   * Starts a time measurement with the passed timeout.
   */
  public TaskDuration(long timeout) {
    this();
    setTimeout(timeout);
  }

  /**
   * Restarts the measurement and clears the timeout.
   */
  public final void restart() {
    start = System.currentTimeMillis();
    timeout = Long.MAX_VALUE;
  }

  /**
   * Restarts the measurement and sets a new timeout.
   */
  public final void setTimeout(long timeout) {
    this.timeout = start + timeout;
  }

  /**
   * Gets the elapsed time in milliseconds.
   */
  public long getMilliseconds() {
    return System.currentTimeMillis() - start;
  }

  /**
   * Returns true if the timeout has been reached.
   */
  public boolean timedOut() {
    return System.currentTimeMillis() > timeout;
  }

  /**
   * Formats the duration in decimal seconds.
   */
  @Override
  public String toString() {
    return String.format(Locale.ENGLISH, "%.3f", getMilliseconds() / 1000.0);
  }
}
