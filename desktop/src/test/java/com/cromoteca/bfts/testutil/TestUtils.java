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

import com.cromoteca.bfts.util.FilePath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtils {
  private static final Logger log = LoggerFactory.getLogger(TestUtils.class);
  private static final Random random = new Random();

  public static <A> A[] runThreads(int num, Supplier<A> runnable,
      IntFunction<A[]> generator) {
    A[] array = generator.apply(num);
    Thread[] threads = new Thread[num];

    for (int i = 0; i < num; i++) {
      int n = i;
      Thread thread = new Thread(() -> array[n] = runnable.get());
      threads[i] = thread;
      thread.start();
    }

    for (int i = 0; i < num; i++) {
      try {
        threads[i].join();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }

    return array;
  }

  public static FilePath getTestDir(Class<?> cls) {
    try {
      FilePath dir = new FilePath(Files.createTempDirectory(cls.getSimpleName()));
      log.info("Test dir for {}: {}", cls, dir.getNativePath());
      return dir;
    } catch (IOException ex) {
      log.error(null, ex);
      return null;
    }
  }

  public static void unzipSampleFiles(FilePath path, String... subDirs)
      throws IOException {
    path.createDirectories();
    InputStream zip = TestUtils.class.getResourceAsStream("/test_files.zip");
    unzip(zip, path, subDirs);
  }

  /**
   * A quick and dirty method to unzip an archive into a directory.
   *
   * @param zip         source archive to be processed
   * @param destination destination directory where to unzip the archive
   * @param subDirs     dirs to extract from the zip (can be null to extract all
   *                    files)
   * @throws IOException If an I/O error occurs
   */
  public static void unzip(InputStream zip, FilePath destination,
      String... subDirs) throws IOException {
    destination.createDirectories();
    FilePath[] subPaths = Arrays.stream(subDirs).map(destination::resolve)
        .toArray(n -> new FilePath[n]);

    try (ZipInputStream zin = new ZipInputStream(zip)) {
      byte[] buf = new byte[65536];
      ZipEntry e;

      while ((e = zin.getNextEntry()) != null) {
        FilePath f = destination.resolve(e.getName());

        if (Arrays.stream(subPaths).filter(f::startsWith).count() > 0) {
          if (e.isDirectory()) {
            f.createDirectories();
          } else {
            f.getParent().createDirectories();

            try (OutputStream out = f.newOutputStream()) {
              int len;

              while ((len = zin.read(buf)) != -1) {
                out.write(buf, 0, len);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Creates a random string of the required length.
   */
  public static synchronized String randomString(int length) {
    if (length % 4 > 0) {
      throw new IllegalArgumentException("Length must be multiple of 4");
    }

    byte[] b = new byte[length * 3 / 4];
    random.nextBytes(b);
    return Base64.getUrlEncoder().encodeToString(b);
  }

  /**
   * Creates a random byte array.
   */
  public static synchronized byte[] randomBytes(int length) {
    byte[] b = new byte[length];
    random.nextBytes(b);
    return b;
  }
}
