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
public class IgnoredFileCheckerTest {
  @Test public void testCheckNotMatched() {
    // inverse logic: matched names will lead to false
    // only filename checks are case insensitive
    IgnoredFileChecker checker = new IgnoredFileChecker("thumbs.db",
        "/myproject/target;/Android;*.bak;*2");

    // name not mentioned
    assertTrue(checker.checkNotMatched("readme.txt"));

    // name mentioned
    assertFalse(checker.checkNotMatched("Thumbs.db"));

    // name and path not mentioned
    assertTrue(checker.checkNotMatched("readme.txt", ""));
    assertTrue(checker.checkNotMatched("readme.txt", "myproject"));

    // name or extension mentioned, but not as absolute path
    assertTrue(checker.checkNotMatched("thumbs.db", ""));
    assertTrue(checker.checkNotMatched("doc.bak", "docs"));
    assertTrue(checker.checkNotMatched("doc2", ""));

    // name mentioned, but as absolute path
    assertTrue(checker.checkNotMatched("android"));
    assertTrue(checker.checkNotMatched("target"));

    // name and path mentioned
    assertFalse(checker.checkNotMatched("Android", ""));
    assertFalse(checker.checkNotMatched("target", "myproject"));

    // extension (i.e. end of filename) mentioned
    assertFalse(checker.checkNotMatched("DOC.BAK"));
    assertFalse(checker.checkNotMatched("doc2"));

    // subitems must be checked too when dealing with paths
    assertTrue(checker.checkNotMatched("test", ""));
    assertFalse(checker.checkNotMatched("test", "Android"));
    assertFalse(checker.checkNotMatched("test", "Android/subdir"));

    // com.cromoteca.bfts.model.File objects are checked in all cases
    assertFalse(checker.checkMatched(new File("readme.txt", "")));
    assertTrue(checker.checkMatched(new File("doc.bak", "docs")));
    assertFalse(checker.checkMatched(new File("Android", "projects")));
    assertTrue(checker.checkMatched(new File("THUMBS.DB", "dirA/dirB")));
    assertFalse(checker.checkMatched(new File("test", "")));
    assertTrue(checker.checkMatched(new File("test", "Android")));
  }
}
