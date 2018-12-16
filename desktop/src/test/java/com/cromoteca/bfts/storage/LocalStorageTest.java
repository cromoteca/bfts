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

import com.cromoteca.bfts.cryptography.Cryptographer;
import com.cromoteca.bfts.model.Chunk;
import com.cromoteca.bfts.model.File;
import com.cromoteca.bfts.model.Hash;
import com.cromoteca.bfts.model.Pair;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.testutil.DirectoryWalker;
import com.cromoteca.bfts.testutil.SQLScript;
import com.cromoteca.bfts.testutil.SQLScriptVars;
import com.cromoteca.bfts.testutil.TestUtils;
import com.cromoteca.bfts.util.Counter;
import com.cromoteca.bfts.util.FilePath;
import com.cromoteca.bfts.util.Hex;
import com.cromoteca.bfts.util.lambdas.IOSupplier;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ibatis.session.SqlSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class LocalStorageTest {
  private static FilePath storageDir;
  private static LocalStorage storage;

  @BeforeClass
  public static void setUpClass() throws Exception {
    storageDir = TestUtils.getTestDir(LocalStorageTest.class);
    storageDir.createDirectories();
    storage = new LocalStorage(storageDir, false, new StorageConfiguration());
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    DirectoryWalker.rmDirSilent(storageDir);
  }

  @Test
  public void testSelectSource() throws Exception {
    SQLScriptVars vars;

    try (SqlSession session = storage.getFactory().openSession()) {
      vars = new SQLScript().run(session.getConnection(),
          getClass().getResource("LocalStorage.selectSource.sql"));
    }

    Source source = storage.selectSources(vars.getString("clientName")).get(0);
    assertEquals(vars.getString("sourceName2"), source.getName());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testaddFiles() throws Exception {
    SQLScript script = new SQLScript();
    SQLScriptVars vars;

    try (SqlSession session = storage.getFactory().openSession()) {
      vars = script.run(session.getConnection(),
          getClass().getResource("LocalStorage.addFiles.sql"));
    }

    List<File> files = Arrays.asList(new File[] {
      // add a file that doesn't exist in the database
      new File("file4", "dir1", 400, 4444),
      // confirm the existing dir
      new File("dir1", ""),
      // modify file2 (file1 is deleted)
      new File("file2", "dir1", 500, 5555),
      // confirm file3
      new File("file3", "dir1", 300, 3333)
    });

    int sourceId = vars.getInt("sourceId");
    storage.addFiles(sourceId, storage.getLastFile(sourceId).getId(), files);

    String fileCount = "fileCount :: select count(*) from files"
        + " where sourceId = ${sourceId} and status = 0";
    try (SqlSession session = storage.getFactory().openSession()) {
      script.run(session.getConnection(), fileCount);
    }
    assertEquals(4, vars.getInt("fileCount"));

    // IllegalArgumentException here: empty source
    storage.addFiles(sourceId, storage.getLastFile(sourceId).getId(), new ArrayList<>());
  }

  @Test
  public void testGetNotHashedFiles() throws Exception {
    SQLScript script = new SQLScript();
    SQLScriptVars vars;
    try (SqlSession session = storage.getFactory().openSession()) {
      vars = script.run(session.getConnection(),
          getClass().getResource("LocalStorage.addFiles.sql"));
    }
    String countFiles = "countFiles :: select count(*) from files"
        + " where hash is null and lastModified > 0";

    try (SqlSession session = storage.getFactory().openSession()) {
      script.run(session.getConnection(), countFiles);
    }
    List<File> files = storage.getNotHashedFiles(vars.getString("clientName"),
        FileStatus.CURRENT.getCode(), 100);
    int sizeBefore = files.size();
    assertEquals(vars.getInt("countFiles"), sizeBefore);

    try (SqlSession session = storage.getFactory().openSession()) {
      script.run(session.getConnection(), "update files set hash = 'abc'"
          + " where name = 'file2' and parent = 'dir1'", countFiles);
    }
    files = storage.getNotHashedFiles(vars.getString("clientName"),
        FileStatus.CURRENT.getCode(), 100);
    int sizeAfter = files.size();
    assertEquals(vars.getInt("countFiles"), sizeAfter);
    assertEquals(sizeBefore - 1, sizeAfter);
  }

  @Test
  public void testUpdateHashes() throws Exception {
    SQLScript script = new SQLScript();
    SQLScriptVars vars;
    try (SqlSession session = storage.getFactory().openSession()) {
      vars = script.run(session.getConnection(),
          getClass().getResource("LocalStorage.addFiles.sql"));
    }
    int chunkSize = 4096;

    Source source = new Source();
    source.setId(vars.getInt("sourceId"));

    File file1 = new File("file1", "dir1", 100, 1111);
    file1.setSource(source);
    Hash hash1 = new Hash();
    hash1.setMain("abcde1".getBytes());
    hash1.setChunks(Arrays.asList(new Chunk[] {
      createChunk(chunkSize, "ccccc1".getBytes()),
      createChunk(chunkSize, "ccccc2".getBytes()),
      createChunk(chunkSize, "ccccc3".getBytes())
    }));
    file1.setHash(hash1);

    File file2 = new File("file2", "dir1", 200, 2222);
    file2.setSource(source);
    Hash hash2 = new Hash();
    hash2.setMain("abcde2".getBytes());
    hash2.setChunks(Arrays.asList(new Chunk[] {
      createChunk(chunkSize, "ccccc4".getBytes()),
      createChunk(chunkSize, "ccccc3".getBytes()),
      createChunk(chunkSize, "ccccc5".getBytes())
    }));
    file2.setHash(hash2);

    try (SqlSession session = storage.getFactory().openSession()) {
      script.run(session.getConnection(), "delete from hashes");
    }

    storage.updateHashes(Arrays.asList(new File[] { file1, file2 }));

    try (SqlSession session = storage.getFactory().openSession()) {
      script.run(session.getConnection(),
          "count :: select count(*) from files where hash is not null");
    }
    assertEquals(2, vars.getInt("count"));

    try (SqlSession session = storage.getFactory().openSession()) {
      script.run(session.getConnection(), "count :: select count(*) from hashes");
    }
    assertEquals(6, vars.getInt("count"));

    try (SqlSession session = storage.getFactory().openSession()) {
      script.run(session.getConnection(),
          "count :: select count(distinct chunk) from hashes");
    }
    assertEquals(5, vars.getInt("count"));
  }

  @Test
  public void testGetNotUploadedChunks() throws Exception {
    SQLScript script = new SQLScript();
    SQLScriptVars vars = script.getVariables();
    int chunkSize = 4096;
    vars.put("chunkSize", chunkSize);

    try (SqlSession session = storage.getFactory().openSession()) {
      script.run(session.getConnection(),
          getClass().getResource("LocalStorage.getNotUploadedChunks.sql"));
    }

    // first client has two sources, must get chunks from both
    List<Chunk> chunks
        = storage.getNotUploadedChunks(vars.getString("clientName1"),
            FileStatus.CURRENT.getCode(), 100);
    // duplicates are no longer filtered for performance reasons
    assertEquals(5, chunks.size());

    // second client has a single source, expect less chunks
    chunks = storage.getNotUploadedChunks(vars.getString("clientName2"),
        FileStatus.CURRENT.getCode(), 100);
    assertEquals(4, chunks.size());
  }

  @Test
  public void testChunks() throws Exception {
    byte[][] hashes = new byte[][] {
      "abcdef45".getBytes(), "bcdef087".getBytes(), "cdef01a3".getBytes(),
      "def0125f".getBytes()
    };

    Map<byte[], byte[]> data = new HashMap<>();
    int chunkSize = 4096;

    List<byte[]> hashList = Arrays.stream(hashes).map(hash -> {
      FilePath chunkDir = storageDir.resolve(Hex.printHexBinary(hash, 0, 1));

      try {
        DirectoryWalker.rmDir(chunkDir);
      } catch (NoSuchFileException ex) {
        // ignore
      } catch (IOException ex) {
        fail(ex.getMessage());
      }

      data.put(hash, TestUtils.randomBytes(chunkSize));
      return createChunk(chunkSize, hash);
    }).map(Chunk::getHash).collect(Collectors.toList());

    Iterator<byte[]> iterator = hashList.iterator();
    storage.storeChunks(hashList, () -> data.get(iterator.next()));

    hashList.stream().forEach(hash -> {
      String fileName = Hex.printHexBinary(hash);
      String dirName = fileName.substring(0, 2);
      FilePath chunkDir = storageDir.resolve("chunks").resolve(dirName);
      assertTrue(chunkDir.exists());

      try (Stream<FilePath> list = chunkDir.list()) {
        FilePath[] paths = list.toArray(FilePath[]::new);
        assertEquals(1, paths.length);
        assertEquals(fileName, paths[0].getFileName());

        // data is compressed when saved, so we don't know the exact file size
        assertTrue(paths[0].size() > chunkSize / 10);
      } catch (IOException ex) {
        fail(ex.getMessage());
      }
    });

    Counter c = new Counter();
    List<Pair<Long, byte[]>> list = hashList.stream()
        .map(hash -> new Pair<>(c.increment(), hash))
        .collect(Collectors.toList());
    IOSupplier<byte[]> supplier = storage.getChunkSupplier(list, 10);

    for (byte[] hash : hashList) {
      byte[] stored = supplier.get();
      assertArrayEquals(data.get(hash), stored);
    }
  }

  @Test
  public void testKeyPair() throws Exception {
    StorageConfiguration sc = storage.getStorageConfiguration();
    assertNull(sc.getEncryptedPublicKey());
    byte[] privateKey = storage.getEncodedPrivateKey();
    assertNull(privateKey);
    byte[] salt = storage.getStorageConfiguration().getSalt();
    storage.addKeyPair(new Cryptographer(salt, "mypassword".toCharArray())
        .generateKeyPair());
    sc = storage.getStorageConfiguration();
    assertNotNull(sc.getEncryptedPublicKey());
    privateKey = storage.getEncodedPrivateKey();
    assertNotNull(privateKey);
  }

  private static Chunk createChunk(int length, byte[] hash) {
    Chunk c = new Chunk();
    c.setLength(length);
    c.setHash(hash);
    return c;
  }
}
