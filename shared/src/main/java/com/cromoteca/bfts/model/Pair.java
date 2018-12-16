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
package com.cromoteca.bfts.model;

import java.util.Objects;

/**
 * Encapsulates two objects.
 *
 * @param <T1>
 * @param <T2>
 */
public class Pair<T1, T2> {
  private T1 first;
  private T2 second;

  public Pair() {
  }

  public Pair(T1 first, T2 second) {
    this.first = first;
    this.second = second;
  }

  public T1 getFirst() {
    return first;
  }

  public void setFirst(T1 first) {
    this.first = first;
  }

  public T2 getSecond() {
    return second;
  }

  public void setSecond(T2 second) {
    this.second = second;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 17 * hash + Objects.hashCode(this.first);
    hash = 17 * hash + Objects.hashCode(this.second);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Pair<?, ?> other = (Pair<?, ?>) obj;
    if (!Objects.equals(this.first, other.first)) {
      return false;
    }
    return Objects.equals(this.second, other.second);
  }

  @Override
  public String toString() {
    return first + ";" + second;
  }
}
