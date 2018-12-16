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
package com.cromoteca.bfts.util.lambdas;

import java.io.IOException;

/**
 * Supplier interface that is known to throw IOExceptions.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
@FunctionalInterface
public interface IOSupplier<T> {
  T get() throws IOException;

  /**
   * Can be overridden for tasks like closing resources.
   */
  default void end() throws IOException {
  }
}
