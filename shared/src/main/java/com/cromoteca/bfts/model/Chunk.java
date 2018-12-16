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
package com.cromoteca.bfts.model;

import com.cromoteca.bfts.util.Hex;

/**
 * File chunk
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Chunk {
  private File file;
  private int index;
  private int length;
  private byte[] hash;

  /**
   * File that contains the chunk
   */
  public File getFile() {
    return file;
  }

  public void setFile(File file) {
    this.file = file;
  }

  /**
   * Chunk hash
   */
  public byte[] getHash() {
    return hash;
  }

  public void setHash(byte[] hash) {
    this.hash = hash;
  }

  /**
   * Position of chunk in file
   */
  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  /**
   * Chunk size
   */
  public int getLength() {
    return length;
  }

  public void setLength(int length) {
    this.length = length;
  }

  @Override
  public String toString() {
    return Hex.printHexBinary(hash);
  }
}
