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
package com.cromoteca.bfts.client;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConfigurationTest {
  private static final String NAME = "LocalConfigurationTest";

  private Configuration c;

  public ConfigurationTest() {
  }

  @Before
  public void setUp() throws BackingStoreException {
    c = new Configuration(Preferences
        .userNodeForPackage(ConfigurationTest.class).node("_test"));
  }

  @After
  public void tearDown() throws BackingStoreException {
    c.remove();
  }

  @Test
  public void testName() {
    c.setClientName(NAME);
    assertEquals(NAME, c.getClientName());
  }
}
