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
package com.cromoteca.bfts.client;

import com.cromoteca.bfts.client.Filesystem.CreationOutcome;
import com.cromoteca.bfts.client.Filesystem.DeletionOutcome;
import com.cromoteca.bfts.model.Chunk;
import com.cromoteca.bfts.model.DeletedFileInfo;
import com.cromoteca.bfts.model.File;
import com.cromoteca.bfts.model.IgnoredFileChecker;
import com.cromoteca.bfts.model.Pair;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.model.Stats;
import com.cromoteca.bfts.storage.FileStatus;
import com.cromoteca.bfts.storage.Storage;
import com.cromoteca.bfts.util.Counter;
import com.cromoteca.bfts.util.FilePath;
import com.cromoteca.bfts.util.Hex;
import com.cromoteca.bfts.util.TaskDuration;
import com.cromoteca.bfts.util.Util;
import com.cromoteca.bfts.util.lambdas.IOSupplier;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Contains the tasks that are performed to backup a client. They orchestrate a
 * {@link Filesystem} and a {@link com.cromoteca.bfts.storage.Storage}.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class ClientActivities {
  private static final Logger log
      = LoggerFactory.getLogger(ClientActivities.class);
  private static final Logger syncLog = LoggerFactory.getLogger("sync");

  private final String client;
  private final Filesystem filesystem;
  private final Storage storage;
  private final String connectionName;
  private final int longOperationDuration;
  private final Counter sequence;
  private int maxNumberOfFilesToHash = 200;
  private int maxNumberOfChunksToStore = 300;

  private Map<Source, WatchKey> fsWatch;

  /**
   * Creates an instance with all required parameters (see details).
   *
   * @param client                the client name
   * @param filesystem            the {@link Filesystem} used to interact with
   *                              the filesystem containing the directories to
   *                              backup
   * @param storage               the {@link com.cromoteca.bfts.storage.Storage}
   *                              used to store backups
   * @param connectionName        the connection name (only useful to print
   *                              logs)
   * @param longOperationDuration the maximum recommended duration of long
   *                              tasks, like upload and download
   */
  public ClientActivities(String client, Filesystem filesystem, Storage storage,
      String connectionName, int longOperationDuration) {
    this.client = client;
    this.filesystem = filesystem;
    this.storage = storage;
    this.connectionName = connectionName;
    this.longOperationDuration = longOperationDuration;
    sequence = new Counter();

    if (Util.isWindows()) {
      fsWatch = new HashMap<>();
    }
  }

  /**
   * Maximum number of files that will be hashed in a single storage connection
   */
  public int getMaxNumberOfFilesToHash() {
    return maxNumberOfFilesToHash;
  }

  public void setMaxNumberOfFilesToHash(int maxNumberOfFilesToHash) {
    this.maxNumberOfFilesToHash = maxNumberOfFilesToHash;
  }

  /**
   * Maximum number of chunks that will be stored in a single storage connection
   */
  public int getMaxNumberOfChunksToStore() {
    return maxNumberOfChunksToStore;
  }

  public void setMaxNumberOfChunksToStore(int maxNumberOfChunksToStore) {
    this.maxNumberOfChunksToStore = maxNumberOfChunksToStore;
  }

  public Storage getStorage() {
    return storage;
  }

  public String getClient() {
    return client;
  }

  /**
   * Select a source to backup and sync. The choice is based on priority and
   * age.
   */
  public Source selectSource(boolean skipEmptySources, String... allowed) {
    // get all sources that must be backed up, then discard those
    // that are not available at the moment (e.g. a disconnected usb stick)
    // sources are already sorted by age (see LocalStorage.selectSources)
    Stream<Source> stream = storage.selectSources(client).stream();

    if (skipEmptySources) {
      stream = stream.filter(Source::hasFilesInBackup);
    }

    if (allowed.length > 0) {
      Arrays.sort(allowed);
      stream = stream.filter(s -> Arrays.binarySearch(allowed, s.getName()) >= 0);
    }

    List<Source> sources = stream
        .filter(Source::isAvailable)
        .collect(Collectors.toList());

    if (fsWatch != null && !skipEmptySources && allowed.length == 0) {
      Util.updateMap(fsWatch, sources, s -> {
        try {
          Path p = Paths.get(s.getRootPath());
          WatchService watchService = p.getFileSystem().newWatchService();
          WatchEvent.Kind<?>[] eventKinds = new WatchEvent.Kind<?>[] {
            ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
          };
          return p.register(watchService, eventKinds,
              com.sun.nio.file.ExtendedWatchEventModifier.FILE_TREE);
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }, WatchKey::cancel);
    }

    if (sources.isEmpty()) {
      return null;
    }

    // get the highest priority between available sources
    int maxPriority
        = sources.stream().mapToInt(Source::getPriority).max().orElse(0);
    // get the priority required to be selected in the current execution
    int priority = sequence.asInt() % (maxPriority + 1);
    sequence.increment();

    // return the first source between those satisfying the priority
    return sources.stream().filter(s -> s.getPriority() >= priority)
        .findFirst().orElse(sources.get(0));
  }

  /**
   * Sends file information to backup. Name, size and date are collected for a
   * number of files, then all this information is sent to the storage.
   */
  public int sendFiles(Source source) {
    int count = 0;

    // we must always check that the source is still available: availability can
    // change at any time due to network issues or disconnected drives
    if (source.isAvailable()) {
      try {
        // backup must resume from where it has been left the previous time
        String lastPath = source.getNewestFile().toString();

        // for a new backup or an empty one, it's OK to start from the root
        if (lastPath == null) {
          lastPath = source.getRootPath();
        }

        // collect files (may take some time)
        List<File> files = filesystem.getFiles(FilePath.get(source.getRootPath()),
            lastPath, source.getIgnoredFileChecker());

        count = files.size();

        // check availability as it might have changed while collecting files
        if (count > 0 && source.isAvailable()) {
          TaskDuration duration = new TaskDuration();
          // send file information to storage (returns the number of new files)
          int newFiles = storage.addFiles(source.getId(),
              source.getNewestFile().getId(), files);
          log.debug("{}-{}->{}: sent {} files in {} seconds ({} files are new)",
              client, source, connectionName, files.size(), duration, newFiles);
        }
      } catch (IOException ex) {
        log.error(null, ex);
      }
    }

    return count;
  }

  /**
   * Attemps to delete files that have been deleted from other clients. A
   * deletion also happens in case of file move: BFTS sees this as a deletion
   * and a creation.
   *
   * @return number of deleted files
   */
  public List<File> syncDeletions(Source source, boolean allTime) {
    // as always, make sure that the source is still available
    if (source.isSyncTarget() && source.isAvailable()) {
      // get deleted files from storage
      List<File> deletedFiles
          = storage.getFilesDeletedFromOtherClients(source.getId(), allTime);
      // take note of successful deletions
      List<File> successfullyDeletedFiles = new ArrayList<>();
      deletedFiles.removeIf(source.getIgnoredFileChecker()::checkMatched);

      if (!deletedFiles.isEmpty()) {
        // sort deletions so that dir1/dir2/file.txt comes before dir1/dir2,
        // otherwise dir2 cannot be deleted
        deletedFiles.sort((f1, f2) -> FilePath.get(f2.toString())
            .compareTo(FilePath.get(f1.toString())));
        FilePath root = FilePath.get(source.getRootPath());

        for (File file : deletedFiles) {
          if (source.isAvailable()) {
            try {
              FilePath path = file.getPath(source.getRootPath());
              DeletionOutcome result
                  = filesystem.deleteFile(file, path, root);
              log.debug("{}->{}: deletion of file {}: {}", connectionName,
                  client, path, result);

              if (result != DeletionOutcome.FAILED) {
                successfullyDeletedFiles.add(file);
                syncLog.info("{}: {} ({} bytes, hash {})", path, result,
                    file.getSize(), file.getHash());
              }
            } catch (IOException ex) {
              log.error(null, ex);
            }
          }
        }

        if (!successfullyDeletedFiles.isEmpty()) {
          // files will be identified by their IDs, so this is only correct
          // if sendFiles has not been called meanwhile
          storage.markFilesDeletedFromSync(successfullyDeletedFiles.stream()
              // map every file to id and deletion time
              .map(file -> new Pair<>(file.getId(), file.getSyncTime()))
              .collect(Collectors.toList()));
          log.debug("{} files have been deleted", successfullyDeletedFiles.size());
          return successfullyDeletedFiles;
        }
      }
    }

    return Collections.emptyList();
  }

  /**
   * Copies new files from backup storage.
   *
   * @return number of added files
   */
  public List<File> syncAdditions(Source source, boolean allTime) {
    List<File> added = new ArrayList<>();

    if (source.isSyncTarget() && source.isAvailable()) {
      try {
        List<File> newFiles
            = storage.getNewFilesFromOtherClients(source.getId(), allTime);
        newFiles.removeIf(source.getIgnoredFileChecker()::checkMatched);

        if (newFiles.size() > 0) {
          log.debug("Received {} files that are new on other clients",
              newFiles.size());
        }

        if (!newFiles.isEmpty()) {
          FilePath root = FilePath.get(source.getRootPath());
          List<Pair<FilePath, File>> files = newFiles.stream()
              // the real file path is needed too
              .map(file -> new Pair<>(file.getPath(source.getRootPath()), file))
              // exclude already existing files
              .filter(pair -> !pair.getFirst().exists())
              .collect(Collectors.toList());

          if (source.isAvailable()) {
            log.debug("{} files out of {} can be created", files.size(),
                newFiles.size());
            // create trivial files (directories and empty files)
            files = filesystem.createFiles(files, root, afterNewFile(added,
                source.getRootPath()));
            // remaining files
            log.debug("{} files must be downloaded from servers", files.size());

            // list the hashes of all chunks that must be requested on server
            // (order is important!)
            List<Pair<Long, byte[]>> hashes = files.stream()
                .flatMap(pair -> {
                  File file = pair.getSecond();
                  List<Chunk> chunks = file.getHash().getChunks();
                  return chunks.stream().map(chunk
                      -> new Pair<>(file.getId(), chunk.getHash()));
                }).collect(Collectors.toList());

            log.debug("{} chunks will be requested to server", hashes.size());

            // will provide chunk data when requested
            IOSupplier<byte[]> bytesFromStorage
                = storage.getChunkSupplier(hashes, longOperationDuration);
            // actual file download
            filesystem.getFilesFromStorage(files, root, bytesFromStorage,
                afterNewFile(added, source.getRootPath()));
            // close connection
            bytesFromStorage.end();

            int count = added.size();

            if (count > 0) {
              // storage must know immediately that those files have been added,
              // so it won't try to add them again and it will remember the
              // original creation time, instead of marking files as created
              // when synced
              storage.markFilesAddedFromSync(source.getId(), added);
              log.debug("{}->{}-{}: received {} files", connectionName, client,
                  source, count);
            }
          }
        }
      } catch (IOException ex) {
        log.error(null, ex);
      }
    }

    return added;
  }

  // a piece of code that is executed in two different places
  private BiConsumer<File, CreationOutcome> afterNewFile(List<File> added,
      String sourcePath) {
    return (file, result) -> {
      log.debug("{}->{}: creation of file {}: {}", connectionName,
          client, file, result);

      if (result != CreationOutcome.FAILED
          && result != CreationOutcome.POSTPONED) {
        added.add(file);
        syncLog.info("{}: {} ({} bytes, hash {})", file.getPath(sourcePath),
            result, file.getSize(), file.getHash());
      }
    };
  }

  public boolean isFileWatchingAvailable() {
    return fsWatch != null;
  }

  public int processRealtimeChanges() {
    if (fsWatch == null) {
      throw new IllegalStateException("Realtime file watching not available");
    }

    Set<DeletedFileInfo> deleteFiles = new HashSet<>();
    List<File> newFiles = new ArrayList<>();
    Set<String> processedFiles = new HashSet<>();

    for (Map.Entry<Source, WatchKey> e : fsWatch.entrySet()) {
      WatchKey watchKey = e.getValue();

      for (WatchEvent<?> we : watchKey.pollEvents()) {
        FilePath relativePath = new FilePath((Path) we.context());
        String relative = relativePath.toString();

        if (relative.isEmpty() || relative.contains(File.BFTS_SUFFIX)) {
          // source root or recycle bin
          continue;
        }

        FilePath rootPath = new FilePath((Path) watchKey.watchable());
        FilePath absolutePath = rootPath.resolve(relativePath);
        String pathAsString = absolutePath.toString();
        Source source = e.getKey();
        IgnoredFileChecker checker = source.getIgnoredFileChecker();

        if (we.kind() == ENTRY_DELETE) {
          if (checker == null
              || checker.checkNotMatched(relativePath.getFileName())) {
            FilePath parent = relativePath.getParent();
            deleteFiles.add(new DeletedFileInfo(source.getId(),
                parent == null ? "" : parent.toString(),
                relativePath.getFileName()));
          }
        } else if (we.kind() == ENTRY_CREATE || we.kind() == ENTRY_MODIFY) {
          if (!processedFiles.contains(pathAsString)) {
            try {
              File file = filesystem.getFile(relativePath, rootPath, checker);

              if (file != null) {
                file.setSource(source);
                newFiles.add(file);
              }

              processedFiles.add(pathAsString);
            } catch (IOException ex) {
              // swallow exception, probably due to short-lived file
              // (not interested by tricky files anyway)
              log.trace(ex.getMessage());
            }
          }
        }
      }
    }

    int count = deleteFiles.size();

    if (count > 0) {
      storage.deleteFilesInRealtime(new ArrayList<>(deleteFiles));
    }

    int added = newFiles.size();

    if (added > 0) {
      storage.addFilesInRealtime(newFiles);
      count += added;
    }

    if (count > 0) {
      log.debug("{}->{}: {} changes detected by realtime monitoring", client,
          connectionName, count);
    }

    return count;
  }

  /**
   * Deletes old files from recycle bin.
   */
  public void collectTrash(Source source) {
    try {
      filesystem.collectTrash(source);
    } catch (IOException ex) {
      log.error(null, ex);
    }
  }

  /**
   * Sends missing hashes to storage.
   *
   * @return number of hashed files
   */
  public int sendHashes(FileStatus status, int... sourceIds) {
    try {
      List<File> files = storage.getNotHashedFiles(client, status.getCode(),
          maxNumberOfFilesToHash, sourceIds);

      if (!files.isEmpty()) {
        // collect successfully hashes files
        List<File> hashedFiles = new ArrayList<>();
        TaskDuration duration = new TaskDuration(longOperationDuration * 1000);

        for (File file : files) {
          // the hash method checks file existence, so isAvailable is not needed
          if (filesystem.hash(file)) {
            hashedFiles.add(file);
          }

          if (duration.timedOut()) {
            break;
          }
        }

        if (!hashedFiles.isEmpty()) {
          // send new hashes to storage
          storage.updateHashes(hashedFiles);
          log.debug("{}->{}: {} files hashed in {} seconds", client,
              connectionName, hashedFiles.size(), duration);
        }

        return hashedFiles.size();
      }
    } catch (IOException ex) {
      log.error(null, ex);
    }

    return 0;
  }

  /**
   * Uploads missing chunks to storage.
   *
   * @return number of uploaded chunks
   */
  public int uploadChunks(FileStatus status, int... sourceIds) {
    List<Chunk> chunks = storage.getNotUploadedChunks(client, status.getCode(),
        maxNumberOfChunksToStore, sourceIds);
    chunks = new ArrayList<>(chunks.stream()
        .filter(c -> c.getFile().getPath().isRegularFile())
        .collect(Collectors.toMap(c -> Hex.printHexBinary(c.getHash()),
            Function.identity(), (a, b) -> a))
        .values());

    if (!chunks.isEmpty()) {
      TaskDuration duration = new TaskDuration(longOperationDuration * 1000);
      List<byte[]> hashes = chunks.stream().map(Chunk::getHash)
          .collect(Collectors.toList());
      Iterator<Chunk> iterator = chunks.iterator();

      // this operation can be slow in case of HTTP upload, so it will stop
      // after a soft timeout. Storage will ask for one chunk at a time,
      // Filesystem will provide them if the timeout has not been reached
      int count = storage.storeChunks(hashes, () -> {
        if (!duration.timedOut() && iterator.hasNext()) {
          return filesystem.readChunk(iterator.next());
        } else {
          return null;
        }
      });

      if (count > 0) {
        log.debug("{}->{}: uploaded {} chunks in {} seconds", client,
            connectionName, count, duration);
      }

      return count;
    }

    return 0;
  }

  /**
   * Gets some backup stats
   *
   * @see com.cromoteca.bfts.model.Stats
   */
  public Stats getStats() {
    return storage.getClientStats(client);
  }
}
