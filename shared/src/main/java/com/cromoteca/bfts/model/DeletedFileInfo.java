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

import java.util.Objects;

/**
 * Data object for files deleted in realtime
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class DeletedFileInfo {
  private final int sourceId;
  private final String parent;
  private final String name;

  public DeletedFileInfo(int sourceId, String parent, String name) {
    this.sourceId = sourceId;
    this.parent = parent;
    this.name = name;
  }

  public int getSourceId() {
    return sourceId;
  }

  public String getParent() {
    return parent;
  }

  public String getName() {
    return name;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 31 * hash + this.sourceId;
    hash = 31 * hash + Objects.hashCode(this.parent);
    hash = 31 * hash + Objects.hashCode(this.name);
    return hash;
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
    final DeletedFileInfo other = (DeletedFileInfo) obj;
    if (this.sourceId != other.sourceId) {
      return false;
    }
    if (!Objects.equals(this.parent, other.parent)) {
      return false;
    }
    return Objects.equals(this.name, other.name);
  }

  @Override
  public String toString() {
    return "DeletedFileInfo{" + "sourceId=" + sourceId + ", parent=" + parent
        + ", name=" + name + '}';
  }
}
