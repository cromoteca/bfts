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
package com.cromoteca.bfts.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Helper class to compress (deflate) byte arrays.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Compression {
  private static final Factory baosFactory;

  static {
    baosFactory = new Factory();
    baosFactory.registerThreadLocal(ByteArrayOutputStream.class,
        ByteArrayOutputStream::new);
  }

  /**
   * Compresses a byte array.
   */
  public static byte[] compress(byte[] data) throws IOException {
    if (data == null || data.length == 0) {
      return data;
    }

    ByteArrayOutputStream baos = baosFactory.obtain(ByteArrayOutputStream.class);
    baos.reset();

    try (DeflaterOutputStream os = new DeflaterOutputStream(baos)) {
      os.write(data);
    }

    return baos.toByteArray();
  }

  /**
   * Decompresses a byte array.
   */
  public static byte[] decompress(byte[] data) throws IOException {
    if (data == null || data.length == 0) {
      return data;
    }

    ByteArrayOutputStream baos = baosFactory.obtain(ByteArrayOutputStream.class);
    baos.reset();

    try (InflaterInputStream is
        = new InflaterInputStream(new ByteArrayInputStream(data))) {
      byte[] buffer = new byte[data.length];
      int n;

      while ((n = is.read(buffer)) >= 0) {
        baos.write(buffer, 0, n);
      }
    }

    return baos.toByteArray();
  }
}
