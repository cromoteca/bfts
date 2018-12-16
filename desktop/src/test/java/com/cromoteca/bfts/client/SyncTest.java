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
import com.cromoteca.bfts.storage.FileStatus;
import com.cromoteca.bfts.storage.LocalStorage;
import com.cromoteca.bfts.testutil.DirectoryWalker;
import com.cromoteca.bfts.testutil.TestUtils;
import com.cromoteca.bfts.util.FilePath;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SyncTest {
  private FilePath testDir;
  private final String[] clients = new String[] { "client1", "client2" };
  private final String sourceName = "sync";
  private final FilePath[] paths = new FilePath[clients.length];
  private LocalStorage storage;

  @Before
  public void setUp() throws Exception {
    testDir = TestUtils.getTestDir(SyncTest.class);
    storage = LocalStorage.init(testDir.resolve("storage"), false,
        new StorageConfiguration());

    for (int i = 0; i < clients.length; i++) {
      String client = clients[i];
      paths[i] = testDir.resolve(client);
      paths[i].createDirectories();
      // write something to avoid skipping empty sources
      // paths[i].resolve(TestUtils.randomString(4))
      //     .write(TestUtils.randomBytes(100));
      storage.addSource(client, sourceName, paths[i].toString());
      storage.setSourceSyncAttributes(client, sourceName, true, true);
    }
  }

  @After
  public void tearDown() throws Exception {
    storage.close();
    DirectoryWalker.rmDirSilent(testDir);
  }

  @Test
  public void testActivityRemoveFilesOnFirstClient() throws Exception {
    FilePath f1 = paths[0].resolve("file1");
    FilePath f2 = paths[1].resolve("file1");
    FilePath d1 = paths[0].resolve("dir1");
    FilePath d2 = paths[1].resolve("dir1");

    f1.write(TestUtils.randomBytes(1000));
    d1.createDirectories();
    // just to avoid an empty dir
    paths[0].resolve("useless").write(TestUtils.randomBytes(1000));

    Filesystem fs = new Filesystem();
    ClientActivities ca1
        = new ClientActivities(clients[0], fs, storage, "mystorage", 30);
    ClientActivities ca2
        = new ClientActivities(clients[1], fs, storage, "mystorage", 30);

    // ca1 adds the files
    ca1.sendFiles(ca1.selectSource(false));
    ca1.sendFiles(ca1.selectSource(false));
    ca1.sendHashes(FileStatus.CURRENT);
    ca1.uploadChunks(FileStatus.CURRENT);

    // ca2 receives the files via sync
    ca2.sendFiles(ca2.selectSource(false));
    ca2.sendFiles(ca2.selectSource(false));
    ca2.syncAdditions(ca2.selectSource(false), false);
    assertTrue(f2.exists());
    assertArrayEquals(f1.readAllBytes(), f2.readAllBytes());
    assertTrue(d2.exists());

    // ca1 deletes the files
    f1.delete();
    d1.delete();
    ca1.sendFiles(ca1.selectSource(false));
    ca1.sendFiles(ca1.selectSource(false));

    // ca2 adds the files it just got from synchronization
    ca2.sendFiles(ca2.selectSource(false));
    ca2.sendFiles(ca2.selectSource(false));

    // ca1 shouldn't get the files added from sync
    ca1.syncAdditions(ca1.selectSource(false), false);
    assertFalse(f1.exists());
    assertFalse(d1.exists());

    // ca2 should receive the deletion from sync
    ca2.syncDeletions(ca2.selectSource(false), false);
    assertFalse(f2.exists());
    assertFalse(d2.exists());
  }

  @Test
  public void testActivityRemoveFilesOnSecondClient() throws Exception {
    FilePath f1 = paths[0].resolve("file1");
    FilePath f2 = paths[1].resolve("file1");
    FilePath d1 = paths[0].resolve("dir1");
    FilePath d2 = paths[1].resolve("dir1");

    f1.write(TestUtils.randomBytes(1000));
    d1.createDirectories();

    Filesystem fs = new Filesystem();
    ClientActivities ca1
        = new ClientActivities(clients[0], fs, storage, "mystorage", 30);
    ClientActivities ca2
        = new ClientActivities(clients[1], fs, storage, "mystorage", 30);

    // ca1 adds the files
    ca1.sendFiles(ca1.selectSource(false));
    ca1.sendFiles(ca1.selectSource(false));
    ca1.sendHashes(FileStatus.CURRENT);
    ca1.uploadChunks(FileStatus.CURRENT);

    // ca2 receives the files via sync
    ca2.sendFiles(ca2.selectSource(false));
    ca2.sendFiles(ca2.selectSource(false));
    ca2.syncAdditions(ca2.selectSource(false), false);
    assertTrue(f2.exists());
    assertArrayEquals(f1.readAllBytes(), f2.readAllBytes());
    assertTrue(d2.exists());

    // files are deleted on the second client before their existance is notified
    // to the server
    f2.delete();
    d2.delete();
    ca2.sendFiles(ca2.selectSource(false));
    ca2.sendFiles(ca2.selectSource(false));

    // ca2 shouldn't get those files again
    ca2.syncAdditions(ca2.selectSource(false), false);
    assertFalse(f2.exists());
    assertFalse(d2.exists());
  }

  @Test
  public void testSyncAttributes1() throws Exception {
    assertFalse(sync(false, false));
  }

  @Test
  public void testSyncAttributes2() throws Exception {
    assertFalse(sync(true, false));
  }

  @Test
  public void testSyncAttributes3() throws Exception {
    assertTrue(sync(true, true));
  }

  @Test
  public void testSyncAttributes4() throws Exception {
    assertFalse(sync(false, true));
  }

  private boolean sync(boolean source, boolean target) throws Exception {
    FilePath f1 = paths[0].resolve("file1");
    FilePath f2 = paths[1].resolve("file1");

    f1.write(TestUtils.randomBytes(100));

    if (f2.exists()) {
      f2.delete();
    }

    Filesystem fs = new Filesystem();
    ClientActivities ca1
        = new ClientActivities(clients[0], fs, storage, "mystorage", 30);
    ClientActivities ca2
        = new ClientActivities(clients[1], fs, storage, "mystorage", 30);
    storage.setSourceSyncAttributes(clients[0], sourceName, source, false);
    storage.setSourceSyncAttributes(clients[1], sourceName, false, target);

    Source s1 = ca1.selectSource(false);
    Source s2 = ca2.selectSource(false);

    // ca1 adds the files
    ca1.sendFiles(s1);
    ca1.sendFiles(s1);
    ca1.sendHashes(FileStatus.CURRENT);
    ca1.uploadChunks(FileStatus.CURRENT);

    ca2.sendFiles(s2);
    ca2.sendFiles(s2);

    if (s2.isSyncTarget()) {
      // ca2 receives the files via sync
      ca2.syncAdditions(s2, false);
    }

    return f2.exists();
  }

  @Test
  public void testActivityIgnoreFiles() throws Exception {
    FilePath f1 = paths[0].resolve("file1");
    FilePath f2 = paths[1].resolve("file1");
    FilePath d1 = paths[0].resolve("dir1");
    FilePath d2 = paths[1].resolve("dir1");
    FilePath df1 = paths[0].resolve("dir1/file1");
    FilePath df2 = paths[1].resolve("dir1/file1");
    FilePath ddf1 = paths[0].resolve("dir1/dir2/file1");
    FilePath ddf2 = paths[1].resolve("dir1/dir2/file1");

    f1.write(TestUtils.randomBytes(1000));
    d1.createDirectories();
    df1.write(TestUtils.randomBytes(1000));

    Filesystem fs = new Filesystem();
    ClientActivities ca1
        = new ClientActivities(clients[0], fs, storage, "mystorage", 30);
    ClientActivities ca2
        = new ClientActivities(clients[1], fs, storage, "mystorage", 30);

    // ca1 adds the files
    ca1.sendFiles(ca1.selectSource(false));
    ca1.sendHashes(FileStatus.CURRENT);
    ca1.uploadChunks(FileStatus.CURRENT);
    assertEquals(3, ca1.getStats().getFiles());

    // ca2 receives the files via sync
    ca2.sendFiles(ca2.selectSource(false));
    ca2.syncAdditions(ca2.selectSource(false), false);
    ca2.sendFiles(ca2.selectSource(false));
    assertEquals(3, ca2.getStats().getFiles());

    // let's ignore file1 on first client in all dirs
    storage.setSourceIgnoredPatterns(clients[0], sourceName, "file1");
    ca1.sendFiles(ca1.selectSource(false));
    ca1.sendFiles(ca1.selectSource(false));
    assertEquals(1, ca1.getStats().getFiles());

    // let's ignore file1 on first client, but only inside dir1
    storage.setSourceIgnoredPatterns(clients[1], sourceName, "/dir1/file1");
    // ca2 will send 2 files since one is ignored from now
    ca2.sendFiles(ca2.selectSource(false));
    // sync should delete the ignored file on client2
    ca2.syncDeletions(ca2.selectSource(false), false);
    assertEquals(1, ca2.getStats().getFiles());
    assertFalse(f2.exists());
    // ignored, so deletion from ca1 is not propagated
    assertTrue(df2.exists());

    // create a new file in a new directory
    ddf2.getParent().createDirectories();
    ddf2.write(TestUtils.randomBytes(1000));
    ca2.sendFiles(ca2.selectSource(false));
    assertEquals(3, ca2.getStats().getFiles());

    // should get the new dir, but not the new file
    ca1.sendFiles(ca1.selectSource(false));
    ca1.syncAdditions(ca1.selectSource(false), false);
    ca1.sendFiles(ca1.selectSource(false));
    assertFalse(ddf1.exists());
    assertEquals(2, ca1.getStats().getFiles());
  }

  @Test
  public void testChangeFileDate() throws Exception {
    FilePath f1 = paths[0].resolve("file1");
    f1.write(TestUtils.randomBytes(1000));

    Filesystem fs = new Filesystem();
    ClientActivities ca1
        = new ClientActivities(clients[0], fs, storage, "mystorage", 30);
    ClientActivities ca2
        = new ClientActivities(clients[1], fs, storage, "mystorage", 30);

    // ca1 adds the file
    ca1.sendFiles(ca1.selectSource(false));
    ca1.sendHashes(FileStatus.CURRENT);
    ca1.uploadChunks(FileStatus.CURRENT);
    assertEquals(1, ca1.getStats().getFiles());

    // ca2 receives the file via sync
    ca2.sendFiles(ca2.selectSource(false));
    ca2.syncAdditions(ca2.selectSource(false), false);
    ca2.sendFiles(ca2.selectSource(false));
    assertEquals(1, ca2.getStats().getFiles());

    // the file time changes (on some systems Java has a resolution of a second)
    f1.setLastModifiedTime(FileTime.from(Instant.now().plusMillis(1100L)));
    ca1.sendFiles(ca1.selectSource(false));
    ca1.sendFiles(ca1.selectSource(false));
    // do not send hashes so hash stays null in the database
    // perform sync on second client
    ca2.sendFiles(ca2.selectSource(false));
    assertTrue(ca2.syncDeletions(ca2.selectSource(false), false).isEmpty());
    assertTrue(ca2.syncAdditions(ca2.selectSource(false), false).isEmpty());
    ca2.sendFiles(ca2.selectSource(false));
    // the old version of the file must stay in place
    assertEquals(1, ca2.getStats().getFiles());

    // now send hashes
    ca1.sendHashes(FileStatus.CURRENT);
    // file time changes are not synchronized
    assertTrue(ca2.syncDeletions(ca2.selectSource(false), false).isEmpty());
    assertTrue(ca2.syncAdditions(ca2.selectSource(false), false).isEmpty());
    assertEquals(1, ca2.getStats().getFiles());
  }
}
