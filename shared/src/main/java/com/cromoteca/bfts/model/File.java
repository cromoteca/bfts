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

import com.cromoteca.bfts.util.FilePath;
import java.beans.Transient;
import java.util.Objects;

/**
 * Info about a file (or directory, or symlink, or ignored path).
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class File {
  /**
   * Suffix appended to all BFTS specific files and directories.
   */
  public static final String BFTS_SUFFIX = "~bfts~";

  private long id;
  private String name;
  private String parent;
  private Source source;
  private Long lastModified;
  private long size;
  private Hash hash;
  private long syncTime;

  private File() {
  }

  @Deprecated
  public File(String path) {
    this(splitParentAndName(path)[1], splitParentAndName(path)[0]);
  }

  /**
   * Creates a directory.
   *
   * @param name   the directory name
   * @param parent the parent directory
   */
  public File(String name, String parent) {
    this.name = name;
    this.parent = parent;
    lastModified = null;
    size = 0;
  }

  @Deprecated
  public File(String path, long size, long lastModified) {
    this(splitParentAndName(path)[1], splitParentAndName(path)[0], size,
        lastModified);
  }

  /**
   * Creates a regular file.
   *
   * @param name         the file name
   * @param parent       the parent directory
   * @param size         the file size
   * @param lastModified the file last modified date, expressed in seconds since
   *                     the epoch
   */
  public File(String name, String parent, long size, long lastModified) {
    this.name = name;
    this.parent = parent;
    this.lastModified = lastModified;
    this.size = size;
  }

  /**
   * The file hash
   */
  public Hash getHash() {
    return hash;
  }

  public void setHash(Hash hash) {
    this.hash = hash;
  }

  /**
   * The file id in the database
   */
  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  /**
   * The source that contains the file
   */
  public Source getSource() {
    return source;
  }

  public void setSource(Source source) {
    this.source = source;
  }

  /**
   * The parent directory path
   */
  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  @Transient
  public File getParentFile() {
    if (parent.length() == 0) {
      return null;
    }

    String[] split = splitParentAndName(parent);
    return new File(split[1], split[0]);
  }

  private static String[] splitParentAndName(String path) {
    String[] result = new String[] { "", "" };

    int index = path.lastIndexOf('/');

    if (index < 0) {
      result[1] = path;
    } else {
      result[0] = path.substring(0, index);
      result[1] = path.substring(index + 1);
    }

    return result;
  }

  /**
   * File name
   */
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * The file last modified date, expressed in seconds since the epoch
   */
  public Long getLastModified() {
    return lastModified;
  }

  public void setLastModified(Long lastModified) {
    this.lastModified = lastModified;
  }

  /**
   * The file size
   */
  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  /**
   * Returns the time that is relevant for synchronization. For deleted files,
   * it is the deletion time, while for created files it is the creation time.
   */
  public long getSyncTime() {
    return syncTime;
  }

  public void setSyncTime(long syncTime) {
    this.syncTime = syncTime;
  }

  @Override
  public int hashCode() {
    int h = 7;
    h = 79 * h + Objects.hashCode(this.name);
    h = 79 * h + Objects.hashCode(this.parent);
    h = 79 * h + Objects.hashCode(this.lastModified);
    h = 79 * h + (int) (this.size ^ (this.size >>> 32));
    return h;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final File other = (File) obj;
    if (this.size != other.size) {
      return false;
    }
    if (!Objects.equals(this.name, other.name)) {
      return false;
    }
    if (!Objects.equals(this.parent, other.parent)) {
      return false;
    }
    return Objects.equals(this.lastModified, other.lastModified);
  }

  @Override
  public String toString() {
    return parent == null || parent.length() == 0 ? name : parent + '/' + name;
  }

  /**
   * Returns true if the file is a directory
   */
  @Transient
  public boolean isDirectory() {
    return lastModified == null;
  }

  /**
   * Returns true if the file is a regular file
   */
  @Transient
  public boolean isFile() {
    return lastModified != null;
  }

  /**
   * Builds the file path of this file relative to its source, which must not be
   * null
   */
  @Transient
  public FilePath getPath() {
    return FilePath.get(source.getRootPath(), parent, name);
  }

  /**
   * Builds the file path of this file relative to the passed root
   */
  @Transient
  public FilePath getPath(String root) {
    return FilePath.get(root, parent, name);
  }
}
