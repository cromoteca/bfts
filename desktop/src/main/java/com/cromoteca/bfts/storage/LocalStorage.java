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

import com.cromoteca.bfts.client.Filesystem;
import com.cromoteca.bfts.mappers.InitMapper;
import com.cromoteca.bfts.mappers.StorageMapper;
import com.cromoteca.bfts.model.Chunk;
import com.cromoteca.bfts.model.DeletedFileInfo;
import com.cromoteca.bfts.model.File;
import com.cromoteca.bfts.model.Hash;
import com.cromoteca.bfts.model.Pair;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.model.Stats;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.util.Cached;
import com.cromoteca.bfts.util.Container;
import com.cromoteca.bfts.util.Counter;
import com.cromoteca.bfts.util.FilePath;
import com.cromoteca.bfts.util.Hex;
import com.cromoteca.bfts.util.TaskDuration;
import com.cromoteca.bfts.util.Util;
import com.cromoteca.bfts.util.lambdas.IOSupplier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.cromoteca.bfts.util.Util.*;
import static java.util.stream.Collectors.*;

/**
 * The main storage implementation. All other storages write to a LocalStorage
 * at the end.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class LocalStorage implements Storage, AutoCloseable {
  /**
   * Number of files that can be added at once. It depends on
   * SQLITE_MAX_VARIABLE_NUMBER and the number of host parameters in the insert
   * query (see addFiles in StorageMapper.xml).
   */
  public static final int BATCH_SIZE = 100;

  private static final Logger log = LoggerFactory.getLogger(LocalStorage.class);
  private static final long DAY_MILLISECONDS
      = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);
  private static final DateTimeFormatter BACKUP_FILE_NAME
      = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
  private SqlSessionFactory factory;
  private ScheduledExecutorService sqlExecutor
      = Executors.newSingleThreadScheduledExecutor();
  private Cached<StorageConfiguration> config;
  private final FilePath storagePath;
  private final FilePath backupPath;
  private final boolean inMemory;
  private long lastAnalyze;
  private static final Map<FilePath, LocalStorage> activeStorages = new HashMap<>();
  private int lastDeletedBackupIndex;

  /**
   * Make sure that local storages are closed correctly. This is very important
   * for in memory databases, that must be flushed to disk.
   */
  static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      // get a view of the storage map, so there are no concurrent changes to it
      LocalStorage[] storages = activeStorages.values()
          .toArray(new LocalStorage[activeStorages.size()]);

      for (LocalStorage storage : storages) {
        if (storage.getPath().exists()) {
          log.info("Stopping storage {}", storage.getPath());
          storage.close();
        }
      }
    }));
  }

  /**
   * Creates a new local storage.
   *
   * @param path      storage path
   * @param inMemory  if true, SQLite database will be kept in memory
   * @param newConfig storage configuration
   */
  public static LocalStorage init(FilePath path, boolean inMemory,
      StorageConfiguration newConfig) {
    path = path.normalize();
    LocalStorage ls = activeStorages.get(path);

    if (ls != null) {
      ls.close();
    }

    ls = new LocalStorage(path, inMemory, newConfig);
    activeStorages.put(path, ls);
    return ls;
  }

  /**
   * Opens an existing storage
   *
   * @param path storage path
   */
  public static LocalStorage get(FilePath path) {
    path = path.normalize();
    LocalStorage ls = activeStorages.get(path);

    if (ls == null) {
      ls = new LocalStorage(path);
      activeStorages.put(path, ls);
    }

    return ls;
  }

  protected LocalStorage(FilePath storagePath) {
    this(storagePath, null, null);
  }

  protected LocalStorage(FilePath storagePath, Boolean inMemory,
      StorageConfiguration newConfig) {
    config = new Cached<>();
    config.setPeriod(1, TimeUnit.HOURS);

    this.storagePath = storagePath;
    // dir used to store SQLite backups
    backupPath = storagePath.resolve("bfts.sqlite.backup");
    // only used for on disk databases
    FilePath databasePath = storagePath.resolve("bfts.sqlite");

    try {
      if (databasePath.exists()) {
        if (newConfig != null) {
          // cannot initialize an existing db
          throw new InitializationException("Database already exists");
        }

        // database file exists, use it
        inMemory = false;
      } else if (backupPath.exists() && !Filesystem.isEmpty(backupPath)) {
        if (newConfig != null) {
          // cannot initialize an existing db
          throw new InitializationException("Database already exists");
        }

        // no database file, but we have a backup: this means that the database
        // must be loaded in memory
        inMemory = true;
      } else if (newConfig == null) {
        // no database, we need to create it, but the initial configuration is
        // missing
        throw new InitializationException("Database doesn't exist");
      }

      // creates storagePath too
      backupPath.createDirectories();
      // dir for temporary files (chunks and db backups)
      storagePath.resolve("temp").createDirectories();
      Properties props = FileStatus.getAsProperties();
      props.setProperty("database.file", databasePath.toString());

      String dbConfig = "/com/cromoteca/bfts/mybatis.config.xml";

      // build mybatis sql session factory
      try (InputStream stream = getClass().getResourceAsStream(dbConfig)) {
        String env = inMemory ? "memory" : "file";
        factory = new SqlSessionFactoryBuilder().build(stream, env, props);
      }

      try (SqlSession session = factory.openSession()) {
        if (newConfig != null) {
          // init db
          String version = init(session, newConfig);
          log.info("{} database created in {} (SQLite version {})", inMemory
              ? "In memory" : "File", storagePath.getNativePath(), version);
        } else if (inMemory) {
          // load the latest backup
          try (final Stream<FilePath> list = backupPath.list()) {
            FilePath backup
                = list.sorted(Comparator.reverseOrder()).findFirst().get();
            Statement stmt = session.getConnection().createStatement();
            stmt.executeUpdate("restore from " + backup);
            log.info("In memory database restored from {}", backup);
          }
        }

        session.commit();

        int dbBackupInterval = config(session.getMapper(StorageMapper.class))
            .getDatabaseBackupIntervalMinutes();

        if (dbBackupInterval > 0) {
          // schedule db backups
          sqlExecutor.scheduleAtFixedRate(this::backupDatabase,
              dbBackupInterval, dbBackupInterval, TimeUnit.MINUTES);
        }
      }

      if (inMemory && newConfig != null) {
        // immediately backup a new in memory database
        backupDatabase();
      }
    } catch (IOException | SQLException ex) {
      throw new InitializationException(ex);
    }

    this.inMemory = inMemory;
  }

  public boolean isInMemory() {
    return inMemory;
  }

  /**
   * Gets storage configuration (cached to avoid repeating queries)
   *
   * @param mapper used to retrieve configuration if cache has expired
   */
  private StorageConfiguration config(StorageMapper mapper) {
    return config.get(() -> mapper.getStorageConfiguration());
  }

  /**
   * Inits a new storage by running all needed queries in sequence.
   */
  private String init(SqlSession session, StorageConfiguration newConfig) {
    InitMapper im = session.getMapper(InitMapper.class);

    im.dropServerTable();
    im.createServerTable();

    im.dropSourceTable();
    im.createSourceTable();

    im.dropFileTable();
    im.createFileTable();
    im.createFilePrimaryIndex();
    im.createFileSecondaryIndex();
    im.createFileTrigger();

    im.dropHashTable();
    im.createHashTable();
    im.createHashChunkIndex();
    im.createHashTrigger();

    im.addServer(newConfig);

    im.createFileView();
    im.createFriendlyFileView();
    im.createFriendlySourceView();

    return im.getVersion();
  }

  /**
   * Creates a new db backup
   */
  private void backupDatabase() {
    try (SqlSession session = factory.openSession()) {
      String fileName = BACKUP_FILE_NAME.format(LocalDateTime.now()) + ".sqlite";
      FilePath tempPath = getTempFile(fileName);

      try (Statement stmt = session.getConnection().createStatement()) {
        stmt.executeUpdate("backup to " + tempPath);
        session.commit();
      }

      tempPath.move(backupPath.resolve(fileName));

      // discard older backups
      try (final Stream<FilePath> files = backupPath.list()) {
        List<FilePath> list = files.sorted().collect(Collectors.toList());
        int max = config(session.getMapper(StorageMapper.class))
            .getDatabaseBackupsToKeep();

        while (list.size() > max) {
          FilePath toBeRemoved = list.remove(lastDeletedBackupIndex);
          toBeRemoved.delete();
          lastDeletedBackupIndex = (lastDeletedBackupIndex + 1) % max;
        }
      }
    } catch (Exception ex) {
      log.warn(null, ex);
    }
  }

  @Override
  public void close() {
    boolean interrupted = false;
    sqlExecutor.shutdown();

    try {
      // wait for termination
      sqlExecutor.awaitTermination(1, TimeUnit.MINUTES);
    } catch (InterruptedException ex) {
      log.warn(null, ex);
      interrupted = true;
    }

    // create last backup
    backupDatabase();
    activeStorages.remove(storagePath);

    if (interrupted) {
      // honor interruption request
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Storage path
   */
  public FilePath getPath() {
    return storagePath;
  }

  @Override
  public StorageConfiguration getStorageConfiguration() {
    return run(mapper -> {
      return config(mapper);
    });
  }

  @Override
  public void addSource(String clientName, String sourceName, String rootPath) {
    run(mapper -> {
      mapper.addSource(clientName, sourceName, rootPath);
      return null;
    });
  }

  @Override
  public void setSourcePriority(String clientName, String sourceName,
      int priority) {
    run(mapper -> {
      mapper.setSourcePriority(clientName, sourceName, priority);
      return null;
    });
  }

  @Override
  public void setSourceSyncAttributes(String clientName, String sourceName,
      boolean syncSource, boolean syncTarget) {
    run(mapper -> {
      mapper.setSourceSyncAttributes(clientName, sourceName, syncSource,
          syncTarget);
      return null;
    });
  }

  @Override
  public void setSourceIgnoredPatterns(String clientName, String sourceName,
      String ignoredPatterns) {
    run(mapper -> {
      mapper.setSourceIgnoredPatterns(clientName, sourceName, ignoredPatterns);
      return null;
    });
  }

  @Override
  public Source getSource(String clientName, String sourceName) {
    return run(mapper -> {
      return mapper.getSource(clientName, sourceName);
    });
  }

  @Override
  public List<Source> selectSources(String clientName) {
    return run(mapper -> {
      return mapper.getSources(clientName).stream()
          // sort according to the mean point between the oldest file in the
          // and the newest one. Oldest and newest do not refer to file creation
          // or modification times, but to the last time that their existence
          // has been confirmed. File id always increases, so older files have
          // smaller id.
          .sorted((j1, j2) -> Long.compare(
          j1.getOldestFile().getId() + j1.getNewestFile().getId(),
          j2.getOldestFile().getId() + j2.getNewestFile().getId()))
          .collect(Collectors.toList());
    });
  }

  @Override
  public File getLastFile(int sourceId) {
    return run(mapper -> {
      return mapper.getLastFile(sourceId);
    });
  }

  @Override
  public List<File> getFiles(int sourceId, long instant) {
    return run(mapper -> {
      return mapper.getFiles(sourceId, instant);
    });
  }

  @Override
  public List<Chunk> getFileChunks(byte[] fileHash) {
    return run(mapper -> {
      return mapper.getFileChunks(fileHash);
    });
  }

  @Override
  public int addFiles(int sourceId, long lastId, List<File> files) {
    if (files.isEmpty()) {
      throw new IllegalArgumentException("Empty sources cannot be updated");
    }

    File lastFile = getLastFile(sourceId);

    if (lastFile == null) {
      if (lastId != 0) {
        return 0;
      }
    } else if (lastFile.getId() != lastId) {
      return 0;
    }

    return run(mapper -> {
      long instant = System.currentTimeMillis();
      int newFiles;

      if (instant - lastAnalyze > DAY_MILLISECONDS) {
        // update SQLite statistics once a day
        mapper.analyze();
        lastAnalyze = instant;
      }

      Util.ofSubLists(files, BATCH_SIZE).forEach(subList -> {
        mapper.addFiles(sourceId, subList, instant);
      });

      // mark identical records as obsolete
      int alreadyBackedUp = mapper.markObsoleteFiles(sourceId);
      newFiles = files.size() - alreadyBackedUp;
      mapper.deleteConfirmedSyncedFiles(sourceId);
      // find deleted files
      mapper.markDeletedFiles(sourceId, instant);
      // remove obsolete rows
      mapper.removeObsoleteRows(sourceId);
      mapper.setSourceLastUpdated(sourceId, instant);

      return newFiles;
    });
  }

  @Override
  public List<File> getNotHashedFiles(String clientName, int status,
      int maxNumberOfFilesToHash, int... sourceIds) {
    return run(mapper -> {
      return mapper.getNotHashedFiles(clientName, status,
          maxNumberOfFilesToHash, sourceIds);
    });
  }

  @Override
  public void updateHashes(List<File> files) {
    run(mapper -> {
      for (File file : files) {
        Hash hash = file.getHash();

        if (hash != null) {
          mapper.updateFileHash(file);

          for (int i = 0; i < hash.getLength(); i++) {
            Chunk chunk = hash.getChunks().get(i);
            mapper.addChunk(hash.getMain(), i, chunk.getLength(), chunk.getHash());
          }
        }
      }
      return null;
    });
  }

  @Override
  public List<Chunk> getNotUploadedChunks(String clientName, int status,
      int maxNumberOfChunksToStore, int... sourceIds) {
    return run(mapper -> {
      return mapper.getNotUploadedChunks(clientName, status,
          maxNumberOfChunksToStore, sourceIds);
    });
  }

  @Override
  public int storeChunks(List<byte[]> hashes, IOSupplier<byte[]> dataSupplier) {
    Set<byte[]> uploaded = new HashSet<>();

    try {
      for (byte[] hash : hashes) {
        FilePath chunkFile = getChunkPath(hash);
        FilePath temp = getTempFile(chunkFile.getFileName());

        try {
          byte[] data = dataSupplier.get();

          if (data != null && data.length > 0) {
            // we can't verify that the passed data is correct, as it could be
            // encrypted
            temp.getParent().createDirectories();
            temp.write(data);
            chunkFile.getParent().createDirectories();
            temp.move(chunkFile, StandardCopyOption.REPLACE_EXISTING);

            if (chunkFile.size() == data.length) {
              uploaded.add(hash);
            } else {
              chunkFile.delete();
            }
          }
        } finally {
          if (temp.exists()) {
            temp.delete();
          }
        }
      }
    } catch (IOException ex) {
      throw new StorageException(ex);
    }

    run(mapper -> {
      long time = System.currentTimeMillis();

      for (byte[] hash : uploaded) {
        mapper.markUploadedChunk(hash, time);
      }

      return null;
    });

    return uploaded.size();
  }

  @Override
  public IOSupplier<byte[]> getChunkSupplier(List<Pair<Long, byte[]>> hashes,
      int timeout) {
    Iterator<Pair<Long, byte[]>> iterator = hashes.iterator();
    Container<Long> last = new Container<>();
    TaskDuration duration = new TaskDuration(timeout * 1000L);

    return () -> {
      byte[] data = null;

      if (iterator.hasNext()) {
        Pair<Long, byte[]> hash = iterator.next();

        // hash.getFirst() is the file id: we'll stop sending data after the
        // timeout has expired, but without interrupting the current file
        if (hash.getFirst().equals(last.getValue()) || !duration.timedOut()) {
          FilePath chunkFile = getChunkPath(hash.getSecond());
          data = chunkFile.readAllBytes();
          last.setValue(hash.getFirst());
        }
      }

      return data;
    };
  }

  @Override
  public List<File> getFilesDeletedFromOtherClients(int sourceId,
      boolean allTime) {
    return run(mapper -> {
      return mapper.getFilesDeletedFromOtherClients(sourceId,
          allTime ? 0L : System.currentTimeMillis());
    });
  }

  @Override
  public void markFilesDeletedFromSync(List<Pair<Long, Long>> deletions) {
    run(mapper -> {
      for (Pair<Long, Long> deletion : deletions) {
        mapper.markFileDeletedFromSync(deletion.getFirst(), deletion.getSecond());
      }

      return null;
    });
  }

  @Override
  public List<File> getNewFilesFromOtherClients(int sourceId, boolean allTime) {
    return run(mapper -> {
      return mapper.getNewFilesFromOtherClients(sourceId,
          allTime ? 0L : System.currentTimeMillis(), SYNC_FILES_BATCH_SIZE);
    });
  }

  @Override
  public void markFilesAddedFromSync(int sourceId, List<File> added) {
    run(mapper -> {

      Util.ofSubLists(added, BATCH_SIZE).forEach(subList -> {
        mapper.markFilesAddedFromSync(sourceId, subList);
      });

      return null;
    });
  }

  @Override
  public Stats getSourceStats(int sourceId) {
    return run(mapper -> {
      Stats stats = new Stats();
      stats.setFiles(mapper.countCurrentFilesBySource(sourceId));
      stats.setFilesWithoutHash(mapper.countNotHashedFilesBySource(sourceId));
      stats.setMissingChunks(mapper.countMissingChunksBySource(sourceId));
      stats.setLastUpdated(mapper.getSourceLastUpdated(sourceId));
      return stats;
    });
  }

  @Override
  public Stats getClientStats(String clientName) {
    return run(mapper -> {
      Stats stats = new Stats();
      stats.setFiles(mapper.countCurrentFilesByClient(clientName));
      stats.setFilesWithoutHash(mapper.countNotHashedFilesByClient(clientName));
      stats.setMissingChunks(mapper.countMissingChunksByClient(clientName));
      stats.setLastUpdated(mapper.getClientLastUpdated(clientName));
      return stats;
    });
  }

  @Override
  public Map<String, Long> getClientsLastUpdated() {
    return run(mapper -> {
      return mapper.getClientsLastUpdated().stream()
          .collect(Collectors.toMap(m -> (String) m.get("client"),
                   m -> ((Number) m.get("lastUpdated")).longValue()));
    });
  }

  @Override
  public SortedMap<String, Stats> getDetailedClientStats(String clientName) {
    List<Source> sources = run(mapper -> {
      return mapper.getSources(clientName);
    });
    return sources.stream()
        .collect(toMap(Source::getName, s -> getSourceStats(s.getId()),
            (a, b) -> a, // useless: no duplicate keys
            TreeMap::new));
  }

  public FilePath getChunksDir() {
    return storagePath.resolve("chunks");
  }

  public FilePath getChunkPath(byte[] hash) {
    String fileName = Hex.printHexBinary(hash, 0, Math.min(hash.length, 64));
    return getChunksDir().resolve(fileName.substring(0, 2)).resolve(fileName);
  }

  private FilePath getTempFile(String baseName) {
    return storagePath.resolve("temp").resolve(baseName
        + System.currentTimeMillis() + File.BFTS_SUFFIX);
  }

  public void addKeyPair(byte[][] keyPair) {
    run(mapper -> {
      mapper.addKeyPair(keyPair[0], keyPair[1]);
      config.clear(); // configuration has changed
      return null;
    });
  }

  public byte[] getEncodedPrivateKey() {
    return run(mapper -> {
      Container<byte[]> container = mapper.getPrivateKey();
      return container == null ? null : container.getValue();
    });
  }

  @Override
  public List<Chunk> getUploadedChunks(byte firstByte) {
    return run(mapper -> {
      return mapper.getUploadedChunks(new byte[] { firstByte });
    });
  }

  @Override
  public void deleteChunk(byte[] hash) {
    run(mapper -> {
      mapper.deleteChunk(hash);
      return null;
    });
  }

  @Override
  public void deleteFilesInRealtime(List<DeletedFileInfo> files) {
    run(mapper -> {
      long instant = System.currentTimeMillis();

      for (DeletedFileInfo file : files) {
        mapper.deleteFileInRealtime(file, instant);
      }

      return null;
    });
  }

  @Override
  public void addFilesInRealtime(List<File> files) {
    run(mapper -> {
      long instant = System.currentTimeMillis();

      Util.ofSubLists(files, BATCH_SIZE).forEach(subList -> {
        mapper.addFilesInRealtime(subList, instant);
      });

      return null;
    });
  }

  @Override
  public void purgeChunks() {
    run(mapper -> {
      mapper.purgeChunks();
      return null;
    });
  }

  @Override
  public Pair<Integer, Long> deleteUnusedChunkFiles() {
    return run(mapper -> {
      FilePath chunksPath = getChunksDir();
      Counter reclaimedSpace = new Counter();
      int total = 0;

      for (int i = 0; i < 256; i++) {
        byte bi = (byte) i;
        List<Chunk> chunks = mapper.getUploadedChunks(new byte[] { bi });
        Set<FilePath> expectedChunks = chunks.stream()
            .map(c -> getChunkPath(c.getHash()))
            .collect(Collectors.toSet());

        String prefix = Hex.byteToString(bi);
        FilePath dir = chunksPath.resolve(prefix);

        if (dir.isDirectory()) {
          long count;

          try {
            count = dir.list()
                .filter(not(expectedChunks::contains))
                .peek(fp -> {
                  try {
                    reclaimedSpace.add(fp.size());
                    fp.delete();
                  } catch (IOException ex) {
                    throw new StorageException(ex);
                  }
                })
                .count();
            total += (int) count;
          } catch (IOException ex) {
            throw new StorageException(ex);
          }

          if (count > 0) {
            log.debug("{} chunks deleted with prefix {}", count, prefix);
          }
        }
      }

      log.info("{} chunks deleted for a total of {} bytes", total,
          reclaimedSpace);
      return new Pair<>(total, reclaimedSpace.get());
    });
  }

  /**
   * Allows to execute database mapped queries
   *
   * @param <T>  return type
   * @param task a function that uses a StorageMapper object to execute its
   *             methods
   */
  private <T> T run(Function<StorageMapper, T> task) {
    return runSQL(session -> {
      StorageMapper mapper = session.getMapper(StorageMapper.class);

      if (log.isDebugEnabled()) {
        mapper = Util.measure(mapper, (name, duration) -> {
          if (duration.getMilliseconds() > 500) {
            // log slow queries
            log.debug("Method {} executed in {} seconds", name, duration);
          }
        });
      }

      if (log.isTraceEnabled()) {
        mapper = Util.traceMethodCalls(mapper, log);
      }

      return task.apply(mapper);
    });
  }

  /**
   * Allows to execute SQL on the embedded SQLite database, using a scheduler to
   * avoid locking issues.
   *
   * @param <T>         return type
   * @param sqlFunction a function that can use a SqlSession to run SQL
   */
  public final <T> T runSQL(SQLFunction<T> sqlFunction) {
    try {
      return sqlExecutor.submit(() -> {
        T result = null;

        try (SqlSession session = factory.openSession()) {
          result = sqlFunction.execute(session);
          session.commit();
        } catch (SQLException | IOException ex) {
          throw new StorageException(ex);
        }

        return result;
      }).get();
    } catch (ExecutionException ex) {
      throw new StorageException(ex);
    } catch (InterruptedException ex) {
      log.warn(null, ex);
      Thread.currentThread().interrupt();
      return null;
    }
  }

  /**
   * Functional interface that throws <code>SQLException</code>s.
   */
  @FunctionalInterface
  public interface SQLFunction<T> {
    T execute(SqlSession session) throws SQLException, IOException;
  }
}
