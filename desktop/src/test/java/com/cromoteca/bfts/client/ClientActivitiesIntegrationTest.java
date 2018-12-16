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

import com.cromoteca.bfts.cryptography.Cryptographer;
import com.cromoteca.bfts.model.Stats;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.storage.EncryptedStorages;
import com.cromoteca.bfts.storage.FileStatus;
import com.cromoteca.bfts.storage.LocalStorage;
import com.cromoteca.bfts.storage.RemoteStorage;
import com.cromoteca.bfts.storage.RemoteStorageServer;
import com.cromoteca.bfts.storage.Storage;
import com.cromoteca.bfts.testutil.DirectoryWalker;
import com.cromoteca.bfts.testutil.TestUtils;
import com.cromoteca.bfts.util.FilePath;
import java.io.BufferedWriter;
import java.util.function.IntConsumer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Executes a backup with three clients.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class ClientActivitiesIntegrationTest {
  private static final FilePath TEST_DIR
      = TestUtils.getTestDir(ClientActivitiesIntegrationTest.class);
  private static final char[] PASSWORD = "nicepassword".toCharArray();
  private static final String[] CLIENTS = new String[] { "home", "laptop", "work" };
  private static final String[][] SOURCES = new String[][] {
    new String[] { "applets" },
    new String[] { "applets" },
    new String[] { "applets", "ishido" }
  };
  private static final ClientActivities[] CLIENT_ACTIVITIES
      = new ClientActivities[CLIENTS.length];
  private static LocalStorage localStorage;
  private static IntConsumer stopServerAfterSeconds;

  @BeforeClass
  public static void setUpClass() throws Exception {
    Filesystem fs = new Filesystem(4096);
    fs.setFilesystemScanSize(20);
    TEST_DIR.createDirectories();
    StorageConfiguration storageConfig = new StorageConfiguration();
    localStorage = LocalStorage.init(TEST_DIR, false, storageConfig);

    // configure a HTTP server for this local storage
    localStorage.addKeyPair(new Cryptographer(storageConfig.getSalt(),
        PASSWORD).generateKeyPair());
    int port = 20202;
    Storage remoteStorage = RemoteStorage.create("localhost", port, PASSWORD);
    Storage encryptedRemoteStorage
        = EncryptedStorages.getEncryptedStorage(remoteStorage, PASSWORD, true);
    // encryptedRemoteStorage = localStorage;

    // start the remote server after the client to verify that it works anyway
    RemoteStorageServer rss = new RemoteStorageServer(localStorage);
    stopServerAfterSeconds = rss.startHTTPServer(port);

    for (int i = 0; i < CLIENTS.length; i++) {
      String client = CLIENTS[i];
      CLIENT_ACTIVITIES[i] = new ClientActivities(client, fs,
          encryptedRemoteStorage, "remote", 30);
      CLIENT_ACTIVITIES[i].setMaxNumberOfFilesToHash(10);
      CLIENT_ACTIVITIES[i].setMaxNumberOfChunksToStore(25);
      FilePath clientDir = TEST_DIR.resolve(client);
      TestUtils.unzipSampleFiles(clientDir, SOURCES[i]);

      for (String source : SOURCES[i]) {
        encryptedRemoteStorage.addSource(client, source,
            clientDir.resolve(source).toString());
        encryptedRemoteStorage.setSourceSyncAttributes(client, source, true,
            true);
      }
    }
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    stopServerAfterSeconds.accept(1);
    localStorage.close();
    DirectoryWalker.rmDirSilent(TEST_DIR);
  }

  @Test
  public void integrationTest() throws Exception {
    ClientActivities laptop = CLIENT_ACTIVITIES[1];
    Storage laptopStorage = laptop.getStorage();
    int laptopAppletsSourceId
        = laptopStorage.getSource(CLIENTS[1], SOURCES[1][0]).getId();

    laptop.sendFiles(laptop.selectSource(false));
    Stats stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(20, stats.getFiles());
    int filesWithoutHash = stats.getFilesWithoutHash();
    laptop.sendHashes(FileStatus.CURRENT);
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(filesWithoutHash - 10, stats.getFilesWithoutHash());

    laptop.sendFiles(laptop.selectSource(false));
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(40, stats.getFiles());
    filesWithoutHash = stats.getFilesWithoutHash();
    laptop.sendHashes(FileStatus.CURRENT);
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(filesWithoutHash - 10, stats.getFilesWithoutHash());

    // we have 41 items to backup, so this completes first round
    laptop.sendFiles(laptop.selectSource(false));
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(41, stats.getFiles());
    filesWithoutHash = stats.getFilesWithoutHash();
    laptop.sendHashes(FileStatus.CURRENT);
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(filesWithoutHash - 10, stats.getFilesWithoutHash());

    laptop.sendFiles(laptop.selectSource(false));
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(41, stats.getFiles());

    // all files should have their hashes now
    laptop.sendHashes(FileStatus.CURRENT);
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(0, stats.getFilesWithoutHash());

    // let's backup the same folder from another client
    ClientActivities home = CLIENT_ACTIVITIES[0];
    Storage homeStorage = home.getStorage();
    int homeAppletsSourceId
        = homeStorage.getSource(CLIENTS[0], SOURCES[0][0]).getId();

    home.sendFiles(home.selectSource(false));
    home.sendHashes(FileStatus.CURRENT);
    home.sendFiles(home.selectSource(false));
    home.sendHashes(FileStatus.CURRENT);
    home.sendFiles(home.selectSource(false));
    home.sendHashes(FileStatus.CURRENT);
    home.sendFiles(home.selectSource(false));
    home.sendHashes(FileStatus.CURRENT);
    stats = homeStorage.getSourceStats(homeAppletsSourceId);
    assertEquals(41, stats.getFiles());
    assertEquals(0, stats.getFilesWithoutHash());

    // laptop will now upload real file contents: we have 71 chunks to store
    assertEquals(71, stats.getMissingChunks());
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(71, stats.getMissingChunks());

    // ten iterations should suffice
    for (int i = 0; i < 10 && stats.getMissingChunks() > 0; i++) {
      laptop.uploadChunks(FileStatus.CURRENT);
      stats = homeStorage.getSourceStats(homeAppletsSourceId);
    }

    assertEquals(0, stats.getMissingChunks());
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(0, stats.getMissingChunks());

    // let's change a file on the "laptop"
    String modify = "lvbox/messages.txt";
    FilePath laptopModify = FilePath.get(laptopStorage.getSource(CLIENTS[1],
        SOURCES[1][0]).getRootPath(), modify);
    byte[] oldLaptopModify = laptopModify.readAllBytes();
    try (BufferedWriter w = laptopModify.newAppendWriter()) {
      w.write("\nJust appended.\n");
    }
    byte[] newLaptopModify = laptopModify.readAllBytes();
    assertTrue(newLaptopModify.length > oldLaptopModify.length);
    System.out.println("Modified " + laptopModify);
    laptop.sendFiles(laptop.selectSource(false));
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(41, stats.getFiles());
    assertEquals(1, stats.getFilesWithoutHash());
    assertEquals(0, stats.getMissingChunks());

    laptop.sendHashes(FileStatus.CURRENT);
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(0, stats.getFilesWithoutHash());
    assertEquals(1, stats.getMissingChunks());

    // the file change in "laptop" is seen as a deletion and an addition
    // the deletion should now be available to other clients
    home.syncDeletions(home.selectSource(false), false);
    home.sendFiles(home.selectSource(false));
    stats = homeStorage.getSourceStats(homeAppletsSourceId);
    assertEquals(40, stats.getFiles());

    // the addition should be ignored since the new file has not been stored
    home.syncAdditions(home.selectSource(false), false);
    // called three times to make sure we're up to date
    home.sendFiles(home.selectSource(false));
    home.sendFiles(home.selectSource(false));
    home.sendFiles(home.selectSource(false));
    stats = homeStorage.getSourceStats(homeAppletsSourceId);
    assertEquals(40, stats.getFiles());

    // now let's upload the new file contents
    laptop.uploadChunks(FileStatus.CURRENT);
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(0, stats.getMissingChunks());

    // sync the new file on "home"
    home.syncAdditions(home.selectSource(false), false);
    home.sendFiles(home.selectSource(false));
    stats = homeStorage.getSourceStats(homeAppletsSourceId);
    assertEquals(41, stats.getFiles());
    FilePath homeModify = FilePath.get(laptopStorage.getSource(CLIENTS[0],
        SOURCES[0][0]).getRootPath(), modify);
    byte[] newHomeModify = homeModify.readAllBytes();
    assertArrayEquals(newHomeModify, newLaptopModify);

    // let's delete a file on "home"
    String delete = "lvinter/lvinter.jar";
    FilePath jarFile = FilePath.get(homeStorage.getSource(CLIENTS[0],
        SOURCES[0][0]).getRootPath(), delete);
    jarFile.delete();
    System.out.println("Deleted " + jarFile);
    home.sendFiles(home.selectSource(false));
    stats = homeStorage.getSourceStats(homeAppletsSourceId);
    assertEquals(40, stats.getFiles());

    // sync the deletion
    laptop.syncDeletions(laptop.selectSource(false), false);
    laptop.sendFiles(laptop.selectSource(false));
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(40, stats.getFiles());

    // let's add "work" to the game
    ClientActivities work = CLIENT_ACTIVITIES[2];
    Storage workStorage = work.getStorage();
    int workAppletsSourceId
        = workStorage.getSource(CLIENTS[2], SOURCES[2][0]).getId();
    int workIshidoSourceId
        = workStorage.getSource(CLIENTS[2], SOURCES[2][1]).getId();

    work.sendFiles(work.selectSource(false));
    work.sendFiles(work.selectSource(false));
    work.sendFiles(work.selectSource(false));
    work.sendFiles(work.selectSource(false));
    stats = workStorage.getSourceStats(workAppletsSourceId);
    assertEquals(41, stats.getFiles());
    assertEquals(33, stats.getFilesWithoutHash());
    stats = workStorage.getSourceStats(workIshidoSourceId);
    assertEquals(4, stats.getFiles());
    assertEquals(4, stats.getFilesWithoutHash());

    work.sendHashes(FileStatus.CURRENT);
    work.sendHashes(FileStatus.CURRENT);
    work.sendHashes(FileStatus.CURRENT);
    work.sendHashes(FileStatus.CURRENT);
    stats = workStorage.getSourceStats(workAppletsSourceId);
    assertEquals(0, stats.getFilesWithoutHash());
    assertEquals(0, stats.getMissingChunks());
    stats = workStorage.getSourceStats(workIshidoSourceId);
    assertEquals(0, stats.getFilesWithoutHash());
    assertEquals(88, stats.getMissingChunks());

    // ten iterations should suffice
    for (int i = 0; i < 10 && stats.getMissingChunks() > 0; i++) {
      work.uploadChunks(FileStatus.CURRENT);
      stats = workStorage.getSourceStats(workIshidoSourceId);
    }

    assertEquals(0, stats.getMissingChunks());

    // "work" has uploaded the old version of messages.txt, but since it has
    // not marked the new one as deleted, "laptop" will not pick that change
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(40, stats.getFiles());
    laptop.syncDeletions(laptop.selectSource(false), false);
    laptop.sendFiles(laptop.selectSource(false));
    laptop.sendFiles(laptop.selectSource(false));
    laptop.sendFiles(laptop.selectSource(false));
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(40, stats.getFiles());

    // "work" has also resurrected lvinter.jar
    laptop.syncAdditions(laptop.selectSource(false), false);
    laptop.sendFiles(laptop.selectSource(false));
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(41, stats.getFiles());

    // now let's delete lvinter.jar on "work": "laptop" should delete it too
    jarFile = FilePath.get(homeStorage.getSource(CLIENTS[2], SOURCES[2][0])
        .getRootPath(), delete);
    jarFile.delete();
    System.out.println("Deleted " + jarFile);
    work.sendFiles(work.selectSource(false));
    work.sendFiles(work.selectSource(false));
    stats = workStorage.getSourceStats(workAppletsSourceId);
    assertEquals(40, stats.getFiles());

    laptop.syncDeletions(laptop.selectSource(false), false);
    laptop.sendFiles(laptop.selectSource(false));
    laptop.sendFiles(laptop.selectSource(false));
    laptop.sendFiles(laptop.selectSource(false));
    stats = laptopStorage.getSourceStats(laptopAppletsSourceId);
    assertEquals(40, stats.getFiles());
  }
}
