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
import java.util.Objects;

/**
 * A database source is a client directory that must be backed up
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Source implements Comparable<Source> {
  private int id;
  private String name;
  private String client;
  private String rootPath;
  private File oldestFile;
  private File newestFile;
  private int priority;
  private boolean syncSource;
  private boolean syncTarget;
  private String ignoredPatterns;
  private transient IgnoredFileChecker ignoredFileChecker;

  @Override
  public int compareTo(Source o) {
    int result = name.compareTo(o.name);

    if (result == 0) {
      result = rootPath.compareTo(o.rootPath);
    }

    return result;
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 37 * hash + Objects.hashCode(this.name);
    hash = 37 * hash + Objects.hashCode(this.rootPath);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Source other = (Source) obj;
    if (!Objects.equals(this.name, other.name)) {
      return false;
    }
    return Objects.equals(this.rootPath, other.rootPath);
  }

  /**
   * The source id in the database
   */
  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  /**
   * The source name
   */
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * The source client name
   */
  public String getClient() {
    return client;
  }

  public void setClient(String client) {
    this.client = client;
  }

  /**
   * The latest backed up file
   */
  public File getNewestFile() {
    return newestFile;
  }

  public void setNewestFile(File newestFile) {
    this.newestFile = newestFile;
  }

  public boolean hasFilesInBackup() {
    if (newestFile == null) {
      throw new IllegalStateException("No backup information available");
    }

    return newestFile.getId() > 0;
  }

  /**
   * The oldest backed up file
   */
  public File getOldestFile() {
    return oldestFile;
  }

  public void setOldestFile(File oldestFile) {
    this.oldestFile = oldestFile;
  }

  /**
   * The source root path
   */
  public String getRootPath() {
    return rootPath;
  }

  public void setRootPath(String rootPath) {
    this.rootPath = rootPath;
  }

  /**
   * The source priority. A higher number means a higher priority.
   */
  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  /**
   * When true, this is a sync source.
   *
   * @return
   */
  public boolean isSyncSource() {
    return syncSource;
  }

  public void setSyncSource(boolean syncSource) {
    this.syncSource = syncSource;
  }

  /**
   * When true, this is a sync target.
   *
   * @return
   */
  public boolean isSyncTarget() {
    return syncTarget;
  }

  public void setSyncTarget(boolean syncTarget) {
    this.syncTarget = syncTarget;
  }

  /**
   * A semicolon-separated list of file patterns that must be ignored
   */
  public String getIgnoredPatterns() {
    return ignoredPatterns;
  }

  public void setIgnoredPatterns(String ignoredPatterns) {
    this.ignoredPatterns = ignoredPatterns;
  }

  public IgnoredFileChecker getIgnoredFileChecker() {
    if (ignoredFileChecker == null) {
      ignoredFileChecker = new IgnoredFileChecker(ignoredPatterns);
    }

    return ignoredFileChecker;
  }

  @Override
  public String toString() {
    return name == null ? Integer.toString(id) : name;
  }

  /**
   * Returns true if this source is currently available, i.e.&nbsp;its contents
   * can be accessed.
   */
  public boolean isAvailable() {
    FilePath path = FilePath.get(rootPath);
    return path.isDirectory() && path.isReadable();
  }
}
