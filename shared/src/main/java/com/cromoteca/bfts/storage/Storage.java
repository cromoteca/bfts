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

import com.cromoteca.bfts.model.Chunk;
import com.cromoteca.bfts.model.DeletedFileInfo;
import com.cromoteca.bfts.model.File;
import com.cromoteca.bfts.model.Pair;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.model.Stats;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.util.lambdas.IOSupplier;
import java.util.List;
import java.util.SortedMap;

/**
 * Interface implemented by all kind of storages.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public interface Storage {
  /**
   * Number of files that can be synced at once.
   */
  public static final int SYNC_FILES_BATCH_SIZE = 999 / 4;

  /**
   * Returns the configuration of this storage.
   *
   * @see com.cromoteca.bfts.model.StorageConfiguration
   */
  StorageConfiguration getStorageConfiguration();

  /**
   * Adds a new backup source
   *
   * @param clientName client that owns the source
   * @param sourceName source name
   * @param rootPath   directory path on the client
   */
  void addSource(String clientName, String sourceName, String rootPath);

  /**
   * Change source priority (0 is minimum, a higher value means higher
   * priority).
   */
  void setSourcePriority(String clientName, String sourceName, int priority);

  /**
   * Change source sync attributes
   */
  void setSourceSyncAttributes(String clientName, String sourceName,
      boolean syncSource, boolean syncTarget);

  /**
   * Change ignored pattern for source
   */
  void setSourceIgnoredPatterns(String clientName, String sourceName,
      String ignoredPatterns);

  /**
   * Returns a list of chunks that the client should own and that haven't been
   * uploaded yet. This storage might not return a full list of all missing
   * chunks if they are too many.
   */
  List<Chunk> getNotUploadedChunks(String clientName, int status,
      int maxNumberOfChunksToStore, int... sourceIds);

  /**
   * Get info about a source.
   */
  Source getSource(String clientName, String sourceName);

  /**
   * Returns all the sources for the passed client name, ordered by age.
   */
  List<Source> selectSources(String clientName);

  /**
   * Returns the last file added to the backup for the given source.
   */
  File getLastFile(int sourceId);

  /**
   * Gets all files available in a source at a certain time
   *
   * @param sourceId the backup source id
   * @param instant  the instant of time
   * @return a list of files
   */
  List<File> getFiles(int sourceId, long instant);

  List<Chunk> getFileChunks(byte[] fileHash);

  /**
   * Adds file information to the backup (not their content)
   *
   * @param sourceId source id
   * @param files    a list of files in the client order
   * @return the number of files that have been identified as new
   */
  int addFiles(int sourceId, long lastId, List<File> files);

  /**
   * Returns a list of files that the client should own and that haven't been
   * hashed yet. This storage might not return a full list of all missing hashes
   * if they are too many.
   */
  List<File> getNotHashedFiles(String clientName, int status,
      int maxNumberOfFilesToHash, int... sourceIds);

  /**
   * Update file hashes
   *
   * @param files a list of files with their hashes
   */
  void updateHashes(List<File> files);

  /**
   * Returns a list of files that the client should own and that have been
   * deleted by other clients. This storage might not return a full list of all
   * deleted files if they are too many.
   */
  List<File> getFilesDeletedFromOtherClients(int sourceId, boolean allTime);

  /**
   * Immediately marks as deleted files that have been removed by the client in
   * a sync operation.
   *
   * @param deletions pairs of file ids and deletion instants
   */
  void markFilesDeletedFromSync(List<Pair<Long, Long>> deletions);

  /**
   * Returns a list of files that have been created by other clients. This
   * storage might not return a full list of all created files if they are too
   * many.
   */
  List<File> getNewFilesFromOtherClients(int sourceId, boolean allTime);

  /**
   * Immediately marks as added files that have been created by the client in a
   * sync operation. These are temporary records, used to store creation time,
   * that must be the creation of the original file, not that of the copied
   * file.
   */
  void markFilesAddedFromSync(int sourceId, List<File> added);

  /**
   * Stores chunk content. Not all chunks will be stored since a timeout can be
   * applied by the client.
   *
   * @param hashes       hashes of the chunks that can be uploaded
   * @param dataSupplier provides chunk content
   * @return the number of uploaded chunks
   */
  int storeChunks(List<byte[]> hashes, IOSupplier<byte[]> dataSupplier);

  /**
   * Returns a supplier that allows to download file chunks. Not all chunks will
   * be downloadable since a timeout can be applied.
   *
   * @param hashes  hashes of desired chunks
   * @param timeout the timeout in seconds
   */
  IOSupplier<byte[]> getChunkSupplier(List<Pair<Long, byte[]>> hashes,
      int timeout);

  /**
   * Returns some statistics for a source.
   */
  Stats getSourceStats(int sourceId);

  /**
   * Returns some statistics for a client.
   */
  Stats getClientStats(String clientName);

  /**
   * Returns some statistics for each source of a client.
   */
  SortedMap<String, Stats> getDetailedClientStats(String clientName);

  /**
   * Returns a list of all chunks that have been uploaded in a specified range
   * (mostly useful for maintenance tasks)
   *
   * @param firstByte the first byte to search
   */
  List<Chunk> getUploadedChunks(byte firstByte);

  void deleteChunk(byte[] hash);

  void deleteFilesInRealtime(List<DeletedFileInfo> files);

  void addFilesInRealtime(List<File> files);

  void purgeChunks();

  Pair<Integer,Long> deleteUnusedChunkFiles();
}
