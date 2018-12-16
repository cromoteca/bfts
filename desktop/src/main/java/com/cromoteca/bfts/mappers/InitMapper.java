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
package com.cromoteca.bfts.mappers;

import com.cromoteca.bfts.model.StorageConfiguration;
import org.apache.ibatis.annotations.Param;

/**
 * Maps database queries used to initialize a storage.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public interface InitMapper {
  /**
   * Gets SQLite version
   */
  String getVersion();

  /**
   * Drops the "servers" table
   */
  void dropServerTable();

  /**
   * Creates the "servers" table, that is supposed to contain a single row
   */
  void createServerTable();

  /**
   * Drop the "sources" table
   */
  void dropSourceTable();

  /**
   * Creates the "sources" table
   */
  void createSourceTable();

  /**
   * Drops the "files" table
   */
  void dropFileTable();

  /**
   * Creates the "files" table
   */
  void createFileTable();

  /**
   * Creates an index for name on the "files" table
   */
  void createFilePrimaryIndex();

  /**
   * Creates an index for status, sourceId, hash on the "files" table
   */
  void createFileSecondaryIndex();

  void createFileTrigger();

  /**
   * Drops the "hashes" table
   */
  void dropHashTable();

  /**
   * Creates the "hashes" table
   */
  void createHashTable();

  /**
   * Creates an index for "chunk" on the "chunks" table
   */
  void createHashChunkIndex();

  /**
   * Creates a trigger to identify and mark already uploaded chunks
   */
  void createHashTrigger();

  /**
   * Adds a new server
   */
  void addServer(@Param("server") StorageConfiguration server);

  /**
   * Creates a view which provides more information about files
   */
  void createFileView();

  void createFriendlyFileView();

  void createFriendlySourceView();
}
