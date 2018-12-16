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
import org.junit.Test;

import static org.junit.Assert.*;

public class CachedTest {
  public CachedTest() {
  }

  @Test
  public void test() throws Exception {
    Cached<Integer> c = new Cached<>();
    c.setPeriod(100, TimeUnit.MILLISECONDS);
    assertEquals(1, (int) c.get(() -> 1));
    assertEquals(1, (int) c.get(() -> 2));
    Thread.sleep(200);
    assertEquals(2, (int) c.get(() -> 2));
  }
}
