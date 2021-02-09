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

import com.cromoteca.bfts.model.Chunk;
import com.cromoteca.bfts.model.DeletedFileInfo;
import com.cromoteca.bfts.model.File;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.util.Container;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

/**
 * Maps database queries used to perform storage operations.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public interface StorageMapper {
  /**
   * Runs analyze on the database
   */
  void analyze();

  /**
   * Gets info about the server
   */
  StorageConfiguration getStorageConfiguration();

  /**
   * Adds or replaces the key pair used to encrypt http data exchange
   */
  void addKeyPair(@Param("encryptedPublicKey") byte[] encryptedPublicKey,
      @Param("privateKey") byte[] privateKey);

  /**
   * Gets the private key needed to decrypt data from http connections
   */
  Container<byte[]> getPrivateKey();

  /**
   * Adds a new source
   *
   * @param clientName the client name
   * @param name       the source name
   * @param rootPath   the root path of the source on the client
   */
  void addSource(@Param("clientName") String clientName,
      @Param("name") String name,
      @Param("rootPath") String rootPath);

  /**
   * Change source priority
   *
   * @param clientName the client name
   * @param sourceName the source name
   * @param priority   the new priority
   */
  void setSourcePriority(@Param("clientName") String clientName,
      @Param("sourceName") String sourceName,
      @Param("priority") int priority);

  /**
   * Change source sync attributes
   *
   * @param clientName the client name
   * @param sourceName the source name
   * @param syncSource source changes are propagated to other sources
   * @param syncTarget receives changes from other sources
   */
  void setSourceSyncAttributes(@Param("clientName") String clientName,
      @Param("sourceName") String sourceName,
      @Param("syncSource") boolean syncSource,
      @Param("syncTarget") boolean syncTarget);

  /**
   * Change source ignored pattern list
   */
  void setSourceIgnoredPatterns(@Param("clientName") String clientName,
      @Param("sourceName") String sourceName,
      @Param("ignoredPatterns") String ignoredPatterns);

  /**
   * Gets a source by client name and source name
   */
  Source getSource(@Param("clientName") String clientName,
      @Param("sourceName") String sourceName);

  /**
   * Adds new files to the backup
   *
   * @param sourceId the source id
   * @param files    an ordered list of files
   */
  void addFiles(@Param("sourceId") int sourceId,
      @Param("files") List<File> files, @Param("instant") long instant);

  /**
   * Marks duplicate records as obsolete. Duplicates have identical file info
   * (path, size, last modified), the newest one wins, the oldest ones are
   * marked as duplicate
   *
   * @param sourceId the source id
   */
  int markObsoleteFiles(@Param("sourceId") int sourceId);

  /**
   * Deletes temporary records used to remember sync time information
   */
  void deleteConfirmedSyncedFiles(@Param("sourceId") int sourceId);

  /**
   * Searches deleted files. For a sequence of files like "a, b, c, a, c", the
   * file "b" can be marked as deleted.
   *
   * @param sourceId the source id
   */
  void markDeletedFiles(@Param("sourceId") int sourceId,
      @Param("instant") long instant);

  /**
   * Removes obsolete rows from the database (see
   * {@link #markObsoleteFiles(int)}).
   *
   * @param sourceId the source id
   */
  void removeObsoleteRows(@Param("sourceId") int sourceId);

  void setSourceLastUpdated(@Param("sourceId") int sourceId,
      @Param("lastUpdated") long lastUpdated);

  long getSourceLastUpdated(@Param("sourceId") int sourceId);

  long getClientLastUpdated(@Param("clientName") String clientName);

  List<Map<String, Object>> getClientsLastUpdated();

  /**
   * Counts items that are included in the current backup view, divided by file
   * type
   */
  int countCurrentFilesBySource(@Param("sourceId") int sourceId);

  /**
   * Counts items that are included in the current backup view, divided by file
   * type
   */
  int countCurrentFilesByClient(@Param("clientName") String clientName);

  /**
   * Counts chunks that should be uploaded to complete a backup source
   */
  int countMissingChunksBySource(@Param("sourceId") int sourceId);

  /**
   * Counts chunks that should be uploaded to complete a backup source
   */
  int countMissingChunksByClient(@Param("clientName") String clientName);

  /**
   * Gets the latest file that has been added to the backup. This does not mean
   * that the file contents have already been backed up.
   *
   * @param sourceId the source id
   * @return the file information
   */
  File getLastFile(@Param("sourceId") int sourceId);

  List<File> getFiles(@Param("sourceId") int sourceId,
      @Param("instant") long instant);

  List<Chunk> getFileChunks(@Param("fileHash") byte[] fileHash);

  /**
   * Gets a list of sources. They include information about oldest and newest
   * files in the backup.
   *
   * @param clientName the client name
   * @return the list of sources
   */
  List<Source> getSources(@Param("clientName") String clientName);

  /**
   * Count files without hash in a source
   */
  int countNotHashedFilesBySource(@Param("sourceId") int sourceId);

  /**
   * Count files without hash in a source
   */
  int countNotHashedFilesByClient(@Param("clientName") String clientName);

  /**
   * Gets a list of files that miss the hash.
   *
   * @param clientName the client name
   * @param status     file status as per FileStatus::getCode
   * @param limit      max number of files to retrieve
   * @return a list of files without hash
   */
  List<File> getNotHashedFiles(@Param("clientName") String clientName,
      @Param("status") int status, @Param("limit") int limit,
      @Param("sourceIds") int... sourceIds);

  /**
   * Gets a list of chunks whose corresponding file data needs to be sent to the
   * server.
   *
   * @param clientName the client name
   * @param status     file status as per FileStatus::getCode
   * @param limit      max number of chunks to retrieve
   * @return a list of chunks
   */
  List<Chunk> getNotUploadedChunks(@Param("clientName") String clientName,
      @Param("status") int status, @Param("limit") int limit,
      @Param("sourceIds") int... sourceIds);

  /**
   * Updates the hash value for a file.
   */
  void updateFileHash(@Param("file") File file);

  /**
   * Adds a new chunk to the database if it's not there already.
   *
   * @param main     hash of the full file
   * @param position chunk position in the file
   * @param length   chunk length
   * @param chunk    chunk hash
   */
  void addChunk(@Param("main") byte[] main, @Param("position") int position,
      @Param("length") int length, @Param("chunk") byte[] chunk);

  /**
   * Marks a chunk as uploaded (sets the upload time)
   */
  void markUploadedChunk(@Param("hash") byte[] hash,
      @Param("instant") long instant);

  /**
   * Gets a list of files that other clients have deleted. Those files must
   * exist in the given source and they must have been subsequently deleted from
   * other sources with the same name.
   *
   * @param sourceId the source id
   * @return a list of files to be deleted
   */
  List<File> getFilesDeletedFromOtherClients(@Param("sourceId") int sourceId,
      @Param("instant") long instant);

  /**
   * Immediately stores file deletions performed by sync operations, without
   * waiting for the next round trip.
   */
  void markFileDeletedFromSync(@Param("id") long id,
      @Param("instant") long instant);

  /**
   * Gets a list of files that other clients have added. Those files must only
   * exist in other sources with the same name.
   *
   * @param sourceId the source id
   * @return a list of files to be created
   */
  List<File> getNewFilesFromOtherClients(@Param("sourceId") int sourceId,
      @Param("instant") long instant, @Param("limit") int limit);

  /**
   * Stores temporary rows about files added from sync, to remember sync time,
   * that must be equal to the creation time of the original file.
   */
  void markFilesAddedFromSync(@Param("sourceId") int sourceId,
      @Param("files") List<File> files);

  /**
   * Return all already uploaded chunks included between some limits
   *
   * @param firstByte a byte array containing a single byte
   */
  List<Chunk> getUploadedChunks(@Param("firstByte") byte[] firstByte);

  /**
   * Deletes a chunk
   */
  void deleteChunk(@Param("hash") byte[] hash);

  void deleteFileInRealtime(@Param("file") DeletedFileInfo file,
      @Param("instant") long instant);

  /**
   * Stores information about new files detected using filesystem watching.
   *
   * @param files list of files to be added (must include source)
   */
  void addFilesInRealtime(@Param("files") List<File> files,
      @Param("instant") long instant);

  /**
   * Deletes all chunk information about files no longer mentioned in the backup
   */
  void purgeChunks();
}
