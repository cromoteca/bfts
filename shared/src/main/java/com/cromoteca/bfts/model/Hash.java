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
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the whole file hash and hashes of single chunks.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Hash {
  private byte[] main;
  private List<Chunk> chunks = new ArrayList<>();
  private long uploaded;

  /**
   * Hash of the complete file
   */
  public byte[] getMain() {
    return main;
  }

  public void setMain(byte[] main) {
    this.main = main;
  }

  /**
   * Returns the hash of a single chunk
   *
   * @param index the chunk index in file, starting from 0
   */
  public byte[] getChunkHash(int index) {
    return chunks.get(index).getHash();
  }

  /**
   * Returns all chunk hashes
   */
  public List<Chunk> getChunks() {
    return chunks;
  }

  public void setChunks(List<Chunk> chunks) {
    this.chunks = chunks;
  }

  /**
   * Returns the uploaded instant
   *
   * @return the uploaded instant, in seconds since the epoch
   */
  public long getUploaded() {
    return uploaded;
  }

  public void setUploaded(long uploaded) {
    this.uploaded = uploaded;
  }

  /**
   * Returns the number of chunks
   */
  public int getLength() {
    return chunks.size();
  }

  @Override
  public String toString() {
    return Hex.printHexBinary(main);
  }
}
