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
package com.cromoteca.bfts.testutil;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class SQLScriptTest {
  private static Connection connection;

  @BeforeClass
  public static void setUpClass() throws Exception {
    connection = DriverManager.getConnection("jdbc:sqlite::memory:");
  }

  @Test
  public void testGoodRun() throws Exception {
    URL resource = getClass().getResource("SQLScriptTest.sql");
    SQLScriptVars variables = new SQLScript().run(connection, resource);

    assertEquals(variables.get("pi"), 3.14);
    assertEquals(variables.get("pi_id"), 1);
    assertEquals(variables.get("name"), "John");
    assertEquals(variables.get("len"), 4);
    assertEquals(variables.get("rows"), 1);
  }

  @Test
  public void testRunBadVariable() throws Exception {
    URL resource = getClass().getResource("SQLScriptTestBadVariable.sql");

    try {
      new SQLScript().run(connection, resource);
      fail("Should have thrown ScriptException");
    } catch (SQLScriptException ex) {
      assertTrue(ex.getMessage().endsWith("is not a valid variable name"));
    }
  }

  @Test
  public void testRunBadQuery() throws Exception {
    URL resource = getClass().getResource("SQLScriptTestBadQuery.sql");

    try {
      new SQLScript().run(connection, resource);
      fail("Should have thrown ScriptException");
    } catch (SQLScriptException ex) {
      assertTrue(ex.getMessage().indexOf("invalid") > 0);
    }
  }

  @Test
  public void testRunBadQueryWithResult() throws Exception {
    URL resource = getClass().getResource("SQLScriptTestBadQueryWithResult.sql");

    try {
      new SQLScript().run(connection, resource);
      fail("Should have thrown ScriptException");
    } catch (SQLScriptException ex) {
      assertTrue(ex.getMessage().indexOf("invalid") > 0);
    }
  }

  @Test
  public void testRunNullValue() throws Exception {
    URL resource = getClass().getResource("SQLScriptTestNullValue.sql");
    SQLScriptVars variables = new SQLScript().run(connection, resource);
    assertTrue(variables.containsKey("result"));
    assertNull(variables.get("result"));
    assertFalse(variables.containsKey("result2"));
  }

  @Test
  public void testRunUndefinedVariable() throws Exception {
    URL resource = getClass().getResource("SQLScriptTestUndefinedVariable.sql");

    try {
      new SQLScript().run(connection, resource);
      fail("Should have thrown ScriptException");
    } catch (SQLScriptException ex) {
      assertTrue(ex.getMessage().endsWith("is not defined"));
    }
  }
}
