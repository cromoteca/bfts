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

import com.cromoteca.bfts.model.Chunk;
import com.cromoteca.bfts.model.File;
import com.cromoteca.bfts.model.Hash;
import com.cromoteca.bfts.model.IgnoredFileChecker;
import com.cromoteca.bfts.model.Pair;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.testutil.DirectoryWalker;
import com.cromoteca.bfts.testutil.TestUtils;
import com.cromoteca.bfts.util.Counter;
import com.cromoteca.bfts.util.FilePath;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class FilesystemTest {
  private static final int CHUNK_SIZE = 32 * 1024;
  private static FilePath testDir = TestUtils.getTestDir(FilesystemTest.class);
  private static Source source;
  private static File[] files;
  private static byte[] b1 = TestUtils.randomBytes(CHUNK_SIZE);
  private static byte[] b2 = TestUtils.randomBytes(CHUNK_SIZE);
  private static byte[] b3 = TestUtils.randomBytes(CHUNK_SIZE);
  private static byte[] b4 = TestUtils.randomBytes(CHUNK_SIZE / 3);
  private final Filesystem filesystem;

  public FilesystemTest() {
    filesystem = new Filesystem(CHUNK_SIZE);
  }

  @BeforeClass
  public static void setUpClass() throws IOException {
    TestUtils.unzipSampleFiles(testDir, "applets");

    source = new Source();
    source.setRootPath(testDir.toString());

    FilePath[] paths = new FilePath[] {
      testDir.resolve("hashtest1"),
      testDir.resolve("hashtest2"),
      testDir.resolve("hashtest3"),
      testDir.resolve("hashtest4")
    };

    try (OutputStream os = paths[0].newOutputStream()) {
      os.write(b1);
      os.write(b2);
      os.write(b3);
    }

    // same as first file
    try (OutputStream os = paths[1].newOutputStream()) {
      os.write(b1);
      os.write(b2);
      os.write(b3);
    }

    // shares a chunk
    try (OutputStream os = paths[2].newOutputStream()) {
      os.write(b2);
      os.write(b4);
    }

    // has an additional chunk
    try (OutputStream os = paths[3].newOutputStream()) {
      os.write(b1);
      os.write(b2);
      os.write(b3);
      os.write(b4);
    }

    files = Arrays.stream(paths).map(p -> {
      File f = null;

      try {
        f = new File(p.getFileName(), "", p.size(),
            p.getLastModifiedTime().to(TimeUnit.MILLISECONDS));
        f.setSource(source);
      } catch (IOException ex) {
        fail(ex.getMessage());
      }

      return f;
    }).toArray(File[]::new);
  }

  @AfterClass
  public static void tearDownClass() throws IOException {
    DirectoryWalker.rmDirSilent(testDir);
  }

  @Test
  public void testReadChunk() throws Exception {
    Chunk chunk = new Chunk();
    File file = files[2];
    filesystem.hash(file);
    file.setSource(source);
    chunk.setFile(file);

    // test a chunk with CHUNK_SIZE length
    chunk.setHash(file.getHash().getChunkHash(0));
    chunk.setIndex(0);
    chunk.setLength(b2.length);
    byte[] chunkData = filesystem.readChunk(chunk);
    assertTrue(Arrays.equals(chunkData, b2));

    // test a chunk smaller than CHUNK_SIZE
    chunk.setHash(file.getHash().getChunkHash(1));
    chunk.setIndex(1);
    chunk.setLength(b4.length);
    chunkData = filesystem.readChunk(chunk);
    assertTrue(Arrays.equals(chunkData, b4));

    // test a modified file (size changed)
    file = new File(file.getName(), "", file.getSize() + 1, file.getLastModified());
    file.setSource(source);
    chunk.setFile(file);
    chunkData = filesystem.readChunk(chunk);
    assertNull(chunkData);
  }

  @Test
  public void testHash() throws Exception {
    Hash[] hashes = new Hash[files.length];

    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      filesystem.hash(file);
      hashes[i] = file.getHash();
    }

    assertArrayEquals(hashes[0].getMain(), hashes[1].getMain());
    assertFalse(Arrays.equals(hashes[0].getMain(), hashes[3].getMain()));
    assertArrayEquals(hashes[0].getChunkHash(1), hashes[2].getChunkHash(0));

    File file = new File("hashtest5", "", 123, 123456);
    file.setSource(source);
    filesystem.hash(file);
    assertNull(file.getHash());
  }

  @Test
  public void testGetFiles() throws Exception {
    filesystem.setFilesystemScanSize(10);
    FilePath root = testDir.resolve("applets");
    IgnoredFileChecker ifc = new IgnoredFileChecker();

    // Get 10 files starting from an existing file
    List<File> fl = filesystem.getFiles(root, "lvclock/miniball.jpg", ifc);
    assertEquals(10, fl.size());
    assertEquals("lvclock/readme.html", fl.get(0).toString());
    assertEquals("lvinter/readme.html", fl.get(9).toString());

    // Get 10 files, will need to "rewind" and start from the first file
    fl = filesystem.getFiles(root, "lvmenu/readme.html", ifc);
    assertEquals(10, fl.size());
    assertEquals("lvnews", fl.get(0).toString());
    assertEquals("lvbook/img9.jpg", fl.get(9).toString());

    // Get 10 files starting from a non existing path
    fl = filesystem.getFiles(root, "lvfoo/bar", ifc);
    assertEquals(10, fl.size());
    assertEquals("lvhues", fl.get(0).toString());
    assertEquals("lvlist", fl.get(9).toString());

    // Get 10 files starting from a non existing file in an existing dir
    fl = filesystem.getFiles(root, "lvnews/image.jpg", ifc);
    assertEquals(10, fl.size());
    assertEquals("lvnews/lvnews.jar", fl.get(0).toString());
    assertEquals("lvbook/lvbook.jar", fl.get(9).toString());
  }

  @Test
  public void testCreateFiles() throws Exception {
    List<Pair<FilePath, File>> pairs = new ArrayList<>();

    FilePath notEmptyPath = testDir.resolve("notEmptyFile");
    byte[] notEmptyContent = TestUtils.randomBytes(CHUNK_SIZE * 2);
    notEmptyPath.write(notEmptyContent);
    File notEmptyFile = new File(notEmptyPath.getFileName(), "",
        notEmptyContent.length,
        notEmptyPath.getLastModifiedTime().to(TimeUnit.MILLISECONDS));
    notEmptyFile.setSource(source);
    filesystem.hash(notEmptyFile);
    Filesystem.DeletionOutcome outcome
        = filesystem.deleteFile(notEmptyFile, notEmptyPath, testDir);
    assertEquals(Filesystem.DeletionOutcome.DISPOSED, outcome);
    pairs.add(new Pair<>(notEmptyPath, notEmptyFile));

    FilePath dirPath = testDir.resolve("dir");
    File dirFile = new File(dirPath.getFileName(), "");
    dirFile.setSource(source);
    pairs.add(new Pair<>(dirPath, dirFile));

    FilePath newNotEmptyPath = testDir.resolve("newNotEmptyFile");
    byte[] newNotEmptyContent = TestUtils.randomBytes(CHUNK_SIZE * 5);
    FilePath newNotEmptyPathCopy = testDir.resolve("copyOfNewNotEmptyFile");
    newNotEmptyPathCopy.write(newNotEmptyContent);
    Hash hash = filesystem.hash(newNotEmptyPathCopy, true);
    File newNotEmptyFile = new File(newNotEmptyPath.getFileName(),
        "", newNotEmptyContent.length,
        newNotEmptyPathCopy.getLastModifiedTime().to(TimeUnit.MILLISECONDS));
    newNotEmptyPathCopy.delete();
    newNotEmptyFile.setHash(hash);
    newNotEmptyFile.setSource(source);
    pairs.add(new Pair<>(newNotEmptyPath, newNotEmptyFile));

    FilePath newEmptyPath = testDir.resolve("newEmptyFile");
    File newEmptyFile = new File(newEmptyPath.getFileName(), "", 0,
        notEmptyFile.getLastModified());
    newEmptyFile.setSource(source);
    pairs.add(new Pair<>(newEmptyPath, newEmptyFile));

    pairs = filesystem.createFiles(pairs, testDir, (f, o) -> {
      System.out.format("%s: %s\n", f, o);
    });

    assertEquals(1, pairs.size());
    assertArrayEquals(notEmptyContent, notEmptyPath.readAllBytes());
    assertTrue(dirPath.isDirectory());
    assertTrue(newEmptyPath.isRegularFile());
    assertEquals(0, newEmptyPath.size());
    Counter c = new Counter();

    filesystem.getFilesFromStorage(pairs, testDir, () -> {
      byte[] chunkData = new byte[CHUNK_SIZE];
      System.arraycopy(newNotEmptyContent, c.asInt() * CHUNK_SIZE, chunkData, 0,
          CHUNK_SIZE);
      c.increment();
      return chunkData;
    }, (f, o) -> {
      System.out.format("%s: %s\n", f, o);
    });

    assertTrue(newNotEmptyPath.exists());
    assertArrayEquals(newNotEmptyContent, newNotEmptyPath.readAllBytes());
  }

  @Test
  public void testGetFile() throws IOException {
    Filesystem fs = new Filesystem();
    IgnoredFileChecker checker = new IgnoredFileChecker("*.bak");

    FilePath dir = testDir.resolve("get-file");
    dir.createDirectories();

    FilePath txtFile = dir.resolve("get-file.txt");
    txtFile.createFile();
    FilePath bakFile = dir.resolve("get-file.bak");
    bakFile.createFile();

    File relativeFile = fs.getFile(FilePath.get("get-file.txt"), dir, checker);
    assertNotNull(relativeFile);

    File absoluteFile = fs.getFile(txtFile, dir, checker);
    assertNotNull(absoluteFile);

    File ignoredFile = fs.getFile(bakFile, dir, checker);
    assertNull(ignoredFile);
  }
}
