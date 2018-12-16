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

import com.cromoteca.bfts.testutil.TestUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.*;

public class CompressionTest {
  @Test
  public void testCompression() throws IOException {
    byte[] data = TestUtils.randomBytes(16384);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    baos.write(data);
    baos.write(data);
    baos.write(data);
    baos.write(data);
    data = baos.toByteArray();
    byte[] compressed = Compression.compress(data);
    byte[] decompressed = Compression.decompress(compressed);
    assertArrayEquals(data, decompressed);
  }
}
