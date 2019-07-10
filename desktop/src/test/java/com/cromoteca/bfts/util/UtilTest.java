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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.*;

public class UtilTest {
  private static final String PARIS = "Paris";
  private static final String BERLIN = "Berlin";
  private static final String ROMA = "Roma";

  Stream<String> stream
      = Arrays.<String>stream(new String[] { ROMA, BERLIN, PARIS });

  @Test(expected = IOException.class)
  public void testConsumeIOWithoutComparator() throws Exception {
    Util.<String>consumeIO(stream, s -> {
      switch (s) {
        case ROMA:
          break;
        case BERLIN:
          throw new IOException(BERLIN);
        case PARIS:
          fail("IOException should have happened before");
          break;
      }
    });
  }

  @Test(expected = IOException.class)
  public void testConsumeIOWithComparator() throws Exception {
    Util.<String>consumeIO(stream.sorted(String::compareTo), s -> {
      switch (s) {
        case BERLIN:
          break;
        case PARIS:
          throw new IOException(PARIS);
        case ROMA:
          fail("IOException should have happened before");
      }
    });
  }

  @Test
  public void testOrderBy() {
    String[] array = stream.sorted(Util.orderBy(String::length))
        .toArray(String[]::new);
    assertEquals(ROMA, array[0]);
    assertEquals(PARIS, array[1]);
    assertEquals(BERLIN, array[2]);
  }

  @Test
  public void testValidName() {
    assertTrue(Util.validName("a"));
    assertTrue(Util.validName("aGoodName"));
    assertTrue(Util.validName("A_GOOD_NAME"));
    assertTrue(Util.validName("a-good-name"));
    assertTrue(Util.validName("a1"));
    assertTrue(Util.validName("aGoodName1"));
    assertTrue(Util.validName("A_GOOD_NAME_1"));
    assertTrue(Util.validName("a-good-name-1"));
    assertFalse(Util.validName(""));
    assertFalse(Util.validName(" "));
    assertFalse(Util.validName("a "));
    assertFalse(Util.validName(" a"));
    assertFalse(Util.validName("a-bad-name-"));
    assertFalse(Util.validName("-a-bad-name"));
    assertFalse(Util.validName("a bad name"));
  }

  @Test
  public void testNot() {
    IntPredicate even = i -> i % 2 == 0;
    assertTrue(IntStream.rangeClosed(1, 10).filter(even)
        .filter(i -> i == 4).count() == 1);
    assertTrue(IntStream.rangeClosed(1, 10).filter(Util.not(even))
        .filter(i -> i == 4).count() == 0);
  }

  @Test
  public void testUpdateMap() {
    Map<String, String> map = new HashMap<>();

    for (int i = 1; i < 10; i++) {
      String s = Integer.toString(i);
      map.put(s, s);
    }

    Set<String> keys = Collections.unmodifiableSet(IntStream.of(0, 1, 4, 5, 12)
        .mapToObj(Integer::toString)
        .collect(Collectors.toSet()));

    SortedSet<String> recycled = new TreeSet<>();

    Util.updateMap(map, keys, Function.identity(), recycled::add);

    assertEquals(keys, map.keySet());
    Set<String> expected = IntStream.of(2, 3, 6, 7, 8, 9)
        .mapToObj(Integer::toString)
        .collect(Collectors.toSet());
    assertEquals(recycled, expected);
  }
}
