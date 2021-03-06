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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class FileTest {
  @Test
  public void testConstructorWithPath() {
    File file = new File("d1/d2/f");
    assertEquals("f", file.getName());
    assertEquals("d1/d2", file.getParent());
  }

  @Test
  public void testGetParentFile() {
    File file = new File("f", "d1/d2/d3");
    File parent = file.getParentFile();
    assertNotNull(parent);
    assertEquals("d3", parent.getName());
    assertEquals("d1/d2", parent.getParent());
  }

  @Test
  public void testGetParentFileFirstLevel() {
    File file = new File("f", "d1");
    File parent = file.getParentFile();
    assertNotNull(parent);
    assertEquals("d1", parent.getName());
    assertEquals("", parent.getParent());
  }

  @Test
  public void testGetParentFileRoot() {
    File file = new File("f", "");
    File parent = file.getParentFile();
    assertNull(parent);
  }
}
