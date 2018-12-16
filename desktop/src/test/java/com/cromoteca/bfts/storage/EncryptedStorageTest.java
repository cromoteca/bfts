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
package com.cromoteca.bfts.storage;

import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.testutil.DirectoryWalker;
import com.cromoteca.bfts.testutil.TestUtils;
import com.cromoteca.bfts.util.FilePath;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class EncryptedStorageTest {
  private static FilePath testDir;
  private static Storage encrypted;
  private static LocalStorage unencrypted;

  @BeforeClass
  public static void setUpClass() throws Exception {
    testDir = TestUtils.getTestDir(EncryptedStorageTest.class);
    testDir.createDirectories();
    unencrypted = new LocalStorage(testDir, true, new StorageConfiguration());
    encrypted = EncryptedStorages.getEncryptedStorage(unencrypted,
        "nicepassword".toCharArray(), true);
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    DirectoryWalker.rmDirSilent(testDir);
  }

  @Test
  public void testAddClientAndSources() throws Exception {
    String clientName = "client";
    String sourceName = "source";
    String path = "path/dir";
    encrypted.addSource(clientName, sourceName, path);
    Source source = encrypted.selectSources(clientName).get(0);
    assertEquals(sourceName, source.getName());
    assertEquals(path, source.getRootPath());
    source = encrypted.getSource(clientName, sourceName);
    assertEquals(sourceName, source.getName());
    assertEquals(path, source.getRootPath());
  }
}
