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
import com.cromoteca.bfts.storage.FileStatus;
import com.cromoteca.bfts.storage.LocalStorage;
import com.cromoteca.bfts.testutil.DirectoryWalker;
import com.cromoteca.bfts.testutil.TestUtils;
import com.cromoteca.bfts.util.FilePath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DuplicateFileTest {
  private FilePath testDir;
  private final String client = "client";
  private final String[] sources = new String[] { "source1", "source2" };
  private LocalStorage storage;
  private FilePath[] paths = new FilePath[sources.length];

  @Before
  public void setUp() throws Exception {
    testDir = TestUtils.getTestDir(DuplicateFileTest.class);
    storage = LocalStorage.init(testDir.resolve("storage"), false,
        new StorageConfiguration());
    for (int i = 0; i < sources.length; i++) {
      String source = sources[i];
      paths[i] = testDir.resolve(source);
      paths[i].createDirectories();
      storage.addSource(client, source, paths[i].toString());
    }
  }

  @After
  public void tearDown() throws Exception {
    storage.close();
    DirectoryWalker.rmDirSilent(testDir);
  }

  @Test
  public void testActivityRemoveFilesOnFirstClient() throws Exception {
    FilePath f1a = paths[0].resolve("file1");
    FilePath f1b = paths[0].resolve("file2");
    FilePath f2 = paths[1].resolve("file1");

    byte[] contents = TestUtils.randomBytes(100);
    f1a.write(contents);
    f1b.write(contents);
    f2.write(contents);

    Filesystem fs = new Filesystem();
    ClientActivities ca
        = new ClientActivities(client, fs, storage, "mystorage", 30);

    // ca1 adds the files
    ca.sendFiles(ca.selectSource(false));
    ca.sendFiles(ca.selectSource(false));
    assertEquals(3, ca.getStats().getFiles());

    ca.sendHashes(FileStatus.CURRENT);
    assertEquals(1, ca.getStats().getMissingChunks());

    int count = ca.uploadChunks(FileStatus.CURRENT);
    assertEquals(1, count);
  }
}
