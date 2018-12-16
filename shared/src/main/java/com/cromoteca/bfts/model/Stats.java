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

/**
 * Some statistical information about a backup
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Stats {
  private int files;
  private int filesWithoutHash;
  private int missingChunks;
  private long lastUpdated;

  public int getFilesWithoutHash() {
    return filesWithoutHash;
  }

  public void setFilesWithoutHash(int filesWithoutHash) {
    this.filesWithoutHash = filesWithoutHash;
  }

  public int getFiles() {
    return files;
  }

  public void setFiles(int files) {
    this.files = files;
  }

  public int getMissingChunks() {
    return missingChunks;
  }

  public void setMissingChunks(int missingChunks) {
    this.missingChunks = missingChunks;
  }

  public long getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(long lastUpdated) {
    this.lastUpdated = lastUpdated;
  }
}
