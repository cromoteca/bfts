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

import com.cromoteca.bfts.testutil.TestUtils;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import org.junit.Test;

import static org.junit.Assert.*;

public class FilePathTest {
  @Test
  public void testToString() {
    // makes little sense on non-Windows OSes
    assertFalse(TestUtils.getTestDir(FilePathTest.class).toString()
        .contains("\\"));
  }

  @Test
  public void testCompareTo() {
    FilePath ab = FilePath.get("/a/b");
    FilePath abc = FilePath.get("/a/b/c");
    assertTrue(ab.compareTo(ab) == 0);
    assertTrue(ab.compareTo(abc) < 0);
    assertTrue(abc.compareTo(ab) > 0);
  }

  @Test
  public void testIsMatchedBy() {
    PathMatcher pm1 = FileSystems.getDefault().getPathMatcher("glob:**/*.java");
    PathMatcher pm2 = FileSystems.getDefault().getPathMatcher("glob:abc*");
    FilePath fp1 = FilePath.get("/dir/file.java");

    // just to make sure that the syntax is correct
    assertTrue(pm1.matches(fp1.p));
    assertFalse(pm2.matches(fp1.p));

    assertFalse(fp1.isMatchedBy(pm2));
    assertTrue(fp1.isMatchedBy(pm1));
    assertTrue(fp1.isMatchedBy(pm2, pm1));
  }
}
