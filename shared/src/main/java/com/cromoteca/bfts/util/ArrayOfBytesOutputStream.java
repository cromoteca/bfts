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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Extends DataOutputStream to add support for writing an array of bytes and its
 * length, so that the byte array can be read back using a
 * {@link ArrayOfBytesInputStream}.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class ArrayOfBytesOutputStream extends DataOutputStream {
  public ArrayOfBytesOutputStream(OutputStream out) {
    super(out);
  }

  /**
   * Writes an array of bytes and its length.
   */
  public void writeArrayOfBytes(byte[] b) throws IOException {
    // write the array length...
    writeInt(b.length);

    // ... then the array itself
    if (b.length > 0) {
      write(b);
    }
  }
}
