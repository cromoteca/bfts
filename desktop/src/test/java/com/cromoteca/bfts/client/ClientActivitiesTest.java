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

import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.storage.LocalStorage;
import com.cromoteca.bfts.testutil.DirectoryWalker;
import com.cromoteca.bfts.testutil.TestUtils;
import com.cromoteca.bfts.util.FilePath;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class ClientActivitiesTest {
  private static final FilePath TEST_DIR
      = TestUtils.getTestDir(ClientActivitiesTest.class);
  private static final String CLIENT = "client";
  private static final String SOURCE = "applets";
  private static final ClientActivities[] CLIENT_ACTIVITIES = new ClientActivities[2];
  private static LocalStorage localStorage;

  @BeforeClass
  public static void setUpClass() throws Exception {
    Filesystem fs = new Filesystem(4096);
    fs.setFilesystemScanSize(20);
    TEST_DIR.createDirectories();
    StorageConfiguration storageConfig = new StorageConfiguration();
    localStorage = LocalStorage.init(TEST_DIR, false, storageConfig);
    FilePath clientDir = TEST_DIR.resolve(CLIENT);
    TestUtils.unzipSampleFiles(clientDir, SOURCE);
    localStorage.addSource(CLIENT, SOURCE, clientDir.resolve(SOURCE).toString());

    for (int i = 0; i < CLIENT_ACTIVITIES.length; i++) {
      CLIENT_ACTIVITIES[i] = new ClientActivities(CLIENT, fs, localStorage, "local", 30);
    }
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    localStorage.close();
    DirectoryWalker.rmDirSilent(TEST_DIR);
  }

  @Test
  public void testTwoClients() {
    ClientActivities client1 = CLIENT_ACTIVITIES[0];
    Source source1 = client1.selectSource(false);
    ClientActivities client2 = CLIENT_ACTIVITIES[1];
    Source source2 = client2.selectSource(false);

    client1.sendFiles(source1);
    assertEquals(20, localStorage.getClientStats(CLIENT).getFiles());

    source1 = client1.selectSource(false);
    client1.sendFiles(source1);
    assertEquals(40, localStorage.getClientStats(CLIENT).getFiles());

    // this execution should be rejected: if files are accepted, count remains
    // 40, but file order is screwed and next call will delete some files
    client2.sendFiles(source2);
    assertEquals(40, localStorage.getClientStats(CLIENT).getFiles());

    source1 = client1.selectSource(false);
    client1.sendFiles(source1);
    assertEquals(41, localStorage.getClientStats(CLIENT).getFiles());
  }
}
