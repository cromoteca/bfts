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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Extends DataInputStream to add support to read an array of bytes that has
 * been written using an {@link ArrayOfBytesOutputStream}.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class ArrayOfBytesInputStream extends DataInputStream {
  public ArrayOfBytesInputStream(InputStream in) {
    super(in);
  }

  /**
   * Reads an array of bytes whose length is specified by the data stream
   * itself.
   */
  public byte[] readArrayOfBytes() throws IOException {
    int length = readInt();
    byte[] result = new byte[length];
    int missing = length;

    while (missing > 0) {
      int n = read(result, length - missing, missing);

      if (n < 0) {
        // IOException instead of EOFException since it must be an error: we
        // know how many bytes we're expecting
        throw new IOException("Not enough bytes to read");
      }

      missing -= n;
    }

    return result;
  }

}
