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
package com.cromoteca.bfts.storage;

import java.util.Properties;

/**
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public enum FileStatus {
  CURRENT(0), OBSOLETE(-1), SYNCED(-2), REALTIME(-3);

  private final int code;

  private FileStatus(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public static Properties getAsProperties() {
    Properties props = new Properties();

    for (FileStatus status : values()) {
      props.setProperty(status.toString().toLowerCase(),
          Integer.toString(status.getCode()));
    }

    return props;
  }
}
