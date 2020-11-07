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

import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.storage.EncryptedStorages;
import com.cromoteca.bfts.storage.FileStatus;
import com.cromoteca.bfts.storage.LocalStorage;
import com.cromoteca.bfts.storage.Storage;
import com.cromoteca.bfts.testutil.DirectoryWalker;
import com.cromoteca.bfts.testutil.SQLScript;
import com.cromoteca.bfts.testutil.TestUtils;
import com.cromoteca.bfts.util.FilePath;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class DeletionTest {
  private static final FilePath TEST_DIR
      = TestUtils.getTestDir(DeletionTest.class);
  private static final String CLIENT = "client";
  private static final String SOURCE = "applets";
  private static ClientActivities client;
  private static LocalStorage localStorage;
  private static Storage storage;
  private static FilePath sourcePath;

  @BeforeClass
  public static void setUpClass() throws Exception {
    Filesystem fs = new Filesystem();
    fs.setFilesystemScanSize(20);
    TEST_DIR.createDirectories();
    StorageConfiguration storageConfig = new StorageConfiguration();
    localStorage = LocalStorage.init(TEST_DIR, false, storageConfig);
    storage = EncryptedStorages.getEncryptedStorage(localStorage,
        "123456".toCharArray(), true);
    FilePath clientDir = TEST_DIR.resolve(CLIENT);
    TestUtils.unzipSampleFiles(clientDir, SOURCE);
    sourcePath = clientDir.resolve(SOURCE);
    storage.addSource(CLIENT, SOURCE, sourcePath.toString());
    client = new ClientActivities(CLIENT, fs, storage, "local", 30);
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    localStorage.close();
    DirectoryWalker.rmDirSilent(TEST_DIR);
  }

  @Test
  public void testReclaimSpace() throws Exception {
    client.sendFiles(client.selectSource(false));
    client.sendFiles(client.selectSource(false));
    client.sendFiles(client.selectSource(false));
    assertEquals(41, storage.getClientStats(CLIENT).getFiles());

    client.sendHashes(FileStatus.CURRENT);
    client.uploadChunks(FileStatus.CURRENT);

    sourcePath.walk()
        .filter(f -> "readme.html".equals(f.getFileName()))
        .forEach(f -> {
          try {
            f.delete();
          } catch (IOException ex) {
            fail(ex.getMessage());
          }
        });

    client.sendFiles(client.selectSource(false));
    client.sendFiles(client.selectSource(false));
    assertEquals(33, storage.getClientStats(CLIENT).getFiles());

    SQLScript script = new SQLScript();
    localStorage.runSQL(session -> {
      return script.run(session.getConnection(),
          "delete from files where status > 0");
    });

    FilePath chunksPath = localStorage.getChunksDir();
    long before = chunksPath.walk().count();
    storage.purgeChunks();
    storage.deleteUnusedChunkFiles();
    long after = chunksPath.walk().count();
    assertEquals(8, before - after);
  }
}
