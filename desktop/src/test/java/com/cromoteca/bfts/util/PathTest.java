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

import org.junit.Test;

import static org.junit.Assert.*;

public class PathTest {

  @Test
  public void testAdd() {
    Path instance = new Path("a/b");
    instance = instance.add("c");
    assertEquals(instance, new Path("a/b/c"));
    instance = instance.add("../d");
    assertEquals(instance, new Path("a/b/d"));
    instance = new Path("../c");
    instance = instance.add("../../d");
    assertEquals(instance, new Path("../../d"));
  }

  @Test
  public void testGetParent() {
    Path instance = new Path("a/b");
    instance = instance.getParent();
    assertEquals(instance, new Path("a"));
    instance = new Path("../../a");
    instance = instance.getParent();
    assertEquals(instance, new Path("../.."));
    instance = new Path("..");
    instance = instance.getParent();
    assertEquals(instance, new Path("../.."));
  }

  @Test
  public void testGetPartial() {
    Path instance = new Path("a/b/c/d");
    instance = instance.getPartial(2);
    assertEquals(instance, new Path("a/b"));
  }

  @Test
  public void testGetCommonPath() {
    Path instance = new Path("a/b/c/d");
    instance = instance.getCommonPath(new Path("a/b/e"));
    assertEquals(instance, new Path("a/b"));
    instance = instance.getCommonPath(new Path("b/e"));
    assertEquals(instance, Path.ROOT);
  }

  @Test
  public void testIsRelative() {
    Path instance = new Path("a/b/c/d");
    assertTrue(!instance.isRelative());
    instance = new Path("..");
    assertTrue(instance.isRelative());
    instance = new Path("../c");
    assertTrue(instance.isRelative());
  }

  @Test
  public void testIsRoot() {
    Path instance = new Path("a/b/c/d");
    assertTrue(!instance.isRoot());
    instance = new Path("c/..");
    assertTrue(instance.isRoot());
  }

  @Test
  public void testIsChildOf() {
    Path instance = new Path("a/b/c/d");
    assertTrue(!instance.isChildOf(new Path("a/b")));
    assertTrue(instance.isChildOf(new Path("a/b/c")));
    assertTrue(!instance.isChildOf(new Path("a/b/c/d")));
    assertTrue(!instance.isChildOf(new Path("a/b/c/d/e")));
    instance = new Path("../a");
    assertTrue(instance.isChildOf(new Path("..")));
    assertTrue(!instance.isChildOf(Path.ROOT));
  }

  @Test
  public void testIsContainedIn() {
    Path instance = new Path("a/b/c/d");
    assertTrue(instance.isContainedIn(new Path("a/b")));
    assertTrue(instance.isContainedIn(new Path("a/b/c")));
    assertTrue(instance.isContainedIn(new Path("a/b/c/d")));
    assertTrue(!instance.isContainedIn(new Path("a/b/c/d/e")));
    instance = new Path("../a");
    assertTrue(!instance.isContainedIn(Path.ROOT));
  }

  @Test
  public void testGetRelativeTo() {
    Path instance = new Path("a/b/c/d");
    instance = instance.getRelativeTo(new Path("a/b"));
    assertEquals(instance, new Path("c/d"));
    instance = new Path("a");
    instance = instance.getRelativeTo(new Path("b/c"));
    assertEquals(instance, new Path("../../a"));
    instance = new Path("../a");
    instance = instance.getRelativeTo(new Path("b/c"));
    assertEquals(instance, new Path("../../../a"));
    instance = new Path("a/b/c/d");
    instance = instance.getRelativeTo(new Path("a/e/f"));
    assertEquals(instance, new Path("../../b/c/d"));

    assertEquals(new Path("a/b").getRelativeTo(new Path("c")), new Path("../a/b"));
    assertEquals(new Path("a/b").getRelativeTo(new Path("a")), new Path("b"));
    assertEquals(new Path("a/b").getRelativeTo(new Path(".")), new Path("a/b"));
    assertEquals(new Path("../a").getRelativeTo(new Path(".")), new Path("../a"));
    assertEquals(new Path("../a").getRelativeTo(new Path("b/c")), new Path("../../../a"));
    assertEquals(new Path("a").getRelativeTo(new Path("b/c")), new Path("../../a"));
    assertEquals(new Path("../a/b").getRelativeTo(new Path("a")), new Path("../../a/b"));
    assertEquals(new Path("../a/b").getRelativeTo(new Path("../a")), new Path("b"));
    assertEquals(new Path("../a/b").getRelativeTo(new Path("../a/c")), new Path("../b"));
  }

  @Test
  public void testGetElementCount() {
    Path instance = new Path("a/../b/c/./d");
    assertEquals(instance.getElementCount(), 3);
  }

  @Test
  public void testGetElementAt() {
    Path instance = new Path("a/../b/c/./d");
    assertEquals(instance.getElementAt(1), "c");
  }

  @Test
  public void testGetLastElement() {
    Path instance = new Path("a/b/c/d");
    assertEquals(instance.getLastElement(), "d");
    instance = new Path("a/b/c/..");
    assertEquals(instance.getLastElement(), "b");
  }

  @Test
  public void testGetAsLink() {
    Path instance = new Path("a/b");
    assertEquals(instance.asLink(), "/a/b");
    instance = new Path("../a/b");
    assertEquals(instance.asLink(), "../a/b");
    assertEquals(Path.ROOT.asLink(), "");
  }

  @Test
  public void testCompareTo() {
    Path p1 = new Path("abc/def");
    Path p2 = new Path("abc/de/f");
    Path p3 = new Path("abcd/e/f");
    Path p4 = new Path("abc");

    assertEquals(p3.compareTo(p3), 0);
    assertTrue(p1.compareTo(p2) > 0);
    assertTrue(p1.compareTo(p3) < 0);
    assertTrue(p1.compareTo(p4) > 0);
    assertTrue(p2.compareTo(p1) < 0);
    assertTrue(p2.compareTo(p3) < 0);
    assertTrue(p2.compareTo(p4) > 0);
    assertTrue(p4.compareTo(p3) < 0);
  }

  @Test
  public void testEquals() {
    Path p1 = new Path("abc/def");
    Path p2 = new Path("abc/./def/b/..");
    assertTrue(p1.equals(p2));
  }

  @Test
  public void testReplace() {
    Path instance = new Path("a/b/c");
    instance = instance.replace(1, "d");
    assertEquals(instance, new Path("a/d/c"));
    instance = instance.replace(2, "e");
    assertEquals(instance, new Path("a/d/e"));
  }

  @Test
  public void testGetSubPath() {
    Path instance = new Path("a/b/c/d/e");
    assertEquals(new Path("c/d"), instance.getSubPath(2, 4));
    assertEquals(new Path("e"), instance.getSubPath(4, 5));

    try {
      instance.getSubPath(4, 6);
      fail();
    } catch (ArrayIndexOutOfBoundsException ex) {
    }

    try {
      instance.getSubPath(4, 2);
      fail();
    } catch (IllegalArgumentException ex) {
    }
  }
}
