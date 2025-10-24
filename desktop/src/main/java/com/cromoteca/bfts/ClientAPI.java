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
package com.cromoteca.bfts;

import com.cromoteca.bfts.client.ClientActivities;
import com.cromoteca.bfts.client.ClientScheduler;
import com.cromoteca.bfts.client.Configuration;
import com.cromoteca.bfts.client.Filesystem;
import com.cromoteca.bfts.cryptography.Cryptographer;
import com.cromoteca.bfts.gui.GuiLogManager;
import com.cromoteca.bfts.model.Pair;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.model.Stats;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.restore.BackupFileSystemFactory;
import com.cromoteca.bfts.restore.BackupFileSystemView;
import com.cromoteca.bfts.storage.EncryptedStorages;
import com.cromoteca.bfts.storage.EncryptionType;
import com.cromoteca.bfts.storage.FileStatus;
import com.cromoteca.bfts.storage.InitializationException;
import com.cromoteca.bfts.storage.LocalStorage;
import com.cromoteca.bfts.storage.RemoteStorage;
import com.cromoteca.bfts.storage.RemoteStorageServer;
import com.cromoteca.bfts.storage.Storage;
import com.cromoteca.bfts.storage.StorageException;
import com.cromoteca.bfts.util.Factory;
import com.cromoteca.bfts.util.FilePath;
import com.cromoteca.bfts.util.Util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.function.IntConsumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.ocpsoft.prettytime.PrettyTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class ClientAPI {
  private static final String FTP_URL = "ftp://localhost:3715/";
  private static final Pattern HOST_PORT = Pattern.compile("(.+):(\\d+)");
  private static final Factory FACTORY = new Factory();
  private static final Configuration CONFIG
      = new Configuration(Preferences.userNodeForPackage(ClientAPI.class));
  private static final ObjectMapper mapper = new ObjectMapper();

  /**
   * Quits the program.
   */
  public void quit() {
    System.exit(0);
  }

  /**
   * Cancels the whole configuration.
   */
  public void clearConfiguration() {
    CONFIG.remove();
    System.out.println("Configuration cleared, exiting");
    quit();
  }

  /**
   * Sets the name of this client.
   *
   * @param name Client name
   */
  public void name(String name) {
    if (!Util.validName(name)) {
      System.err.println("Name is not valid");
    } else {
      CONFIG.setClientName(name);
    }
  }

  /**
   * Initializes a new local storage.
   *
   * @param name Storage name
   * @param path Storage path
   * @param inMemory In memory database
   * @return Message about the operation
   */
  public String init(String name, String path, boolean inMemory) {
    StorageConfiguration storageConfig = new StorageConfiguration();
    LocalStorage.init(FilePath.get(path), inMemory, storageConfig);
    CONFIG.setLocalStoragePath(name, path);
    return String.format("Local storage %s initialized in directory %s\n", name, path);
  }

  /**
   * Publishes a local storage over HTTP.
   *
   * @param name Storage name
   * @param port HTTP port
   * @throws GeneralSecurityException If a security exception occurs
   */
  public String publish(String name, int port, String password)
      throws GeneralSecurityException {
    String path = CONFIG.getLocalStoragePath(name);
    LocalStorage storage = LocalStorage.get(FilePath.get(path));
    StorageConfiguration storageConfig = storage.getStorageConfiguration();

    // The HTTP connection needs a key pair to encrypt exchanged data
    Cryptographer crypto = new Cryptographer(storageConfig.getSalt(),
        password.toCharArray());
    storage.addKeyPair(crypto.generateKeyPair());
    CONFIG.setLocalStoragePort(name, port);
    return String.format("Storage %s published on port %d\n", name, port);
  }

  /**
   * Connects to a storage.
   *
   * @param name Connection name
   * @param path Storage path (Local dir or host:port)
   * @param encryption Encryption (none, data, full)
   * @param transmissionPassword Password for HTTP transmission encryption
   * @param fileEncryptionPassword Password for client-side file encryption (null if encryption is NONE)
   */
  public void connect(String name, String path, String encryption, String transmissionPassword, String fileEncryptionPassword) {
    EncryptionType encryptionType = EncryptionType.fromString(encryption);
    CONFIG.setConnectedStoragePath(name, path);
    CONFIG.setConnectedStorageEncryptionType(name, encryptionType);

    // Transmission password is always required
    if (transmissionPassword == null || transmissionPassword.isEmpty()) {
      throw new IllegalArgumentException("Transmission password must not be empty");
    }
    CONFIG.setConnectedStorageTransmissionPassword(name, transmissionPassword);

    // File encryption password is only required when encryption is not NONE
    if (encryptionType != EncryptionType.NONE) {
      if (fileEncryptionPassword == null || fileEncryptionPassword.isEmpty()) {
        throw new IllegalArgumentException("File encryption password must not be empty when encryption is enabled");
      }
      CONFIG.setConnectedStorageFileEncryptionPassword(name, fileEncryptionPassword);
    }

    if (!path.contains(":") && CONFIG.getLocalStoragePath(name) == null) {
      CONFIG.setLocalStoragePath(name, path);
    }

    System.out.format("Prepared connection to storage %s as %s\n", name, CONFIG.getClientName());
  }

  /**
   * Adds a backup source to a storage.
   *
   * @param storageName Connection name
   * @param name Source name
   * @param path Source path
   * @throws IOException If an I/O error occurs
   * @return Message about the operation
   */
  public String add(String storageName, String name, String path) throws IOException {
    if (!Util.validName(name)) {
      return "Source name is not valid\n";
    } else {
      FilePath directory = FilePath.get(path);

      if (directory.isDirectory()) {
        Storage storage = getStorage(storageName);
        storage.addSource(CONFIG.getClientName(), name, path);
        return String.format("Added source %s to storage %s\n", name, storageName);
      } else {
        return String.format("%s is not a directory\n", path);
      }
    }
  }

  /**
   * Change source priority.
   *
   * @param storageName Connection name
   * @param name Source name
   * @param priority Priority
   */
  public void priority(String storageName, String name, int priority) {
    Storage storage = getStorage(storageName);
    storage.setSourcePriority(CONFIG.getClientName(), name, priority);
    System.out.format("Priority for %s set to %d\n", name, priority);
  }

  /**
   * Lists local and connected storages.
   *
   * @return A JSON object containing the list of local and connected storages
   */
  public ObjectNode list() {
    ArrayNode locals = mapper.createArrayNode();

    for (String name : CONFIG.getLocalStorages()) {
      ObjectNode node = mapper.createObjectNode();
      node.put("name", name);
      node.put("path", CONFIG.getLocalStoragePath(name));
      node.put("port", CONFIG.getLocalStoragePort(name));
      locals.add(node);
    }

    ArrayNode connected = mapper.createArrayNode();

    for (String name : CONFIG.getConnectedStorages()) {
      ObjectNode node = mapper.createObjectNode();
      node.put("name", name);
      node.put("path", CONFIG.getConnectedStoragePath(name));
      EncryptionType encType = CONFIG.getConnectedStorageEncryptionType(name);
      node.put("encryption", encType.toString().toLowerCase());
      connected.add(node);
    }

    ObjectNode result = mapper.createObjectNode();
    result.set("localStorages", locals);
    result.set("connectedStorages", connected);

    return result;
  }

  /**
   * Returns collected log lines produced while the GUI is running.
   *
   * @return A JSON object containing log entries newer than the provided id
   */
  public ObjectNode logs() {
    return logs(0L);
  }

  /**
   * Returns collected log lines produced while the GUI is running.
   *
   * @param afterId Return only entries with id greater than this value
   * @return A JSON object containing log entries
   */
  public ObjectNode logs(long afterId) {
    return GuiLogManager.collect(mapper, afterId);
  }

  /**
   * Lists sources backed up on a storage.
   *
   * @param name Connection name
   * @return A string containing the list of sources
   */
  public String list(String name) {
    Storage storage = getStorage(name);
    List<Source> sources = storage.selectSources(CONFIG.getClientName());
    return sources.stream()
        .map(source -> String.format("    %s on path %s with priority %d",
        source.getName(), source.getRootPath(), source.getPriority()))
        .collect(Collectors.joining("\n"));
  }

  /**
   * Make a complete backup of all sources.
   *
   * @param storageName Storage name
   */
  public void complete(String storageName) {
    Storage storage = getStorage(storageName);
    Filesystem fs = new Filesystem();
    fs.setFilesystemScanSize(Integer.MAX_VALUE);
    ClientActivities ca = new ClientActivities(CONFIG.getClientName(), fs,
        storage, storageName, CONFIG.getLongOperationDuration());

    for (Source source : storage.selectSources(CONFIG.getClientName())) {
      doCompleteBackup(ca, source.getName());
    }
  }

  /**
   * Make a complete backup of a source.
   *
   * @param storageName Storage name
   * @param sourceName Source name
   */
  public void complete(String storageName, String sourceName) {
    Storage storage = getStorage(storageName);
    Filesystem fs = new Filesystem();
    fs.setFilesystemScanSize(Integer.MAX_VALUE);
    ClientActivities ca = new ClientActivities(CONFIG.getClientName(), fs,
        storage, storageName, CONFIG.getLongOperationDuration());
    doCompleteBackup(ca, sourceName);
  }

  private void doCompleteBackup(ClientActivities ca, String sourceName) {
    Source source = ca.selectSource(false, sourceName);

    if (source == null) {
        System.out.format("Source %s is not available at the moment\n", sourceName);
    } else {
      ca.sendFiles(source);
      while (!ca.syncDeletions(source, true).isEmpty()) {}
      while (!ca.syncAdditions(source, true).isEmpty()) {}
      while (ca.sendHashes(FileStatus.CURRENT, source.getId()) > 0) {}
      while (ca.uploadChunks(FileStatus.CURRENT, source.getId()) > 0) {}
    }
  }

  /**
   * Starts all backups.
   */
  public void start() {
    start(null);
  }

  /**
   * Starts a backup.
   *
   * @param name Storage name
   */
  public void start(String name) {
    // start all HTTP servers, for use by remote clients
    Stream<String> stream = Arrays.stream(CONFIG.getLocalStorages());

    if (name != null) {
      stream = stream.filter(n -> name.equals(n));
    }

    // collect paths and ports
    stream.map(n -> new Pair<>(CONFIG.getLocalStoragePath(n),
        CONFIG.getLocalStoragePort(n)))
        // keep those with a valid port number (port is 0 when not published)
        .filter(storage -> storage.getSecond() > 0)
        .forEach(storage -> {
          String path = storage.getFirst();
          Integer port = storage.getSecond();

          try {
            LocalStorage localStorage = LocalStorage.get(FilePath.get(path));
            RemoteStorageServer server = new RemoteStorageServer(localStorage);

            // start server and keep reference to be able to stop it
            IntConsumer stop = server.startHTTPServer(port);
            FACTORY.registerSingleton(IntConsumer.class, port, stop);
          } catch (InitializationException ex) {
            System.out.format("Storage server %s not started: %s\n", path,
                ex.getMessage());
          } catch (IOException ex) {
            throw new RuntimeException(ex);
          }

          System.out.format("Server started for %s on port %d\n", path, port);
        });

    Filesystem filesystem = new Filesystem();

    // start all backups
    stream = Arrays.stream(CONFIG.getConnectedStorages());

    if (name != null) {
      stream = stream.filter(n -> name.equals(n));
    }

    stream.forEach(n -> {
      try {
        Storage storage = getStorage(n);

        if (storage == null) {
          System.out.format("Storage server %s is not available\n", n);
        } else {
          // one ClientActivities object for each backup destination
          ClientActivities ca = new ClientActivities(CONFIG.getClientName(),
              filesystem, storage, n, CONFIG.getLongOperationDuration());

          // one scheduler for each backup destination
          ClientScheduler cs = new ClientScheduler(ca, 5000, 150000);
          FACTORY.registerSingleton(ClientScheduler.class, n, cs);
          cs.start();
          System.out.format("Client scheduler started for %s\n", n);
        }
      } catch (StorageException ex) {
        ex.printStackTrace(System.err);
      }
    });
  }

  /**
   * Stops all backups.
   */
  public void stop() {
    stop(null);
  }

  /**
   * Stops a backup.
   *
   * @param name Storage name
   */
  public void stop(String name) {
    System.out.print("Stopping running backup... ");

    Stream<String> stream = Arrays.stream(CONFIG.getConnectedStorages());

    if (name != null) {
      stream = stream.filter(n -> name.equals(n));
    }

    // stopping more backups at the same time (not optimal since parallel is
    // based on the number of CPUs)
    stream.parallel()
        .forEach(n -> {
          ClientScheduler cs = FACTORY.obtain(ClientScheduler.class, n);

          if (cs != null) {
            cs.stop();
            FACTORY.unregister(ClientScheduler.class, n);
          }
        });

    System.out.print("and local storage... ");

    stream = Arrays.stream(CONFIG.getLocalStorages());

    if (name != null) {
      stream = stream.filter(n -> name.equals(n));
    }

    // close more storages at the same time
    stream.parallel().forEach(n -> {
      int port = CONFIG.getLocalStoragePort(n);

      // stop the related HTTP server if the storage is published
      if (port > 0) {
        IntConsumer stop = FACTORY.obtain(IntConsumer.class, port);

        if (stop != null) {
          stop.accept(10);
          FACTORY.unregister(IntConsumer.class, port);
        }
      }

      FilePath path = FilePath.get(CONFIG.getLocalStoragePath(n));

      try {
        LocalStorage.get(path).close();
      } catch (InitializationException ex) {
        System.out.format("Storage server %s not stopped: %s\n", path,
            ex.getMessage());
      }
    });

    System.out.println("done");
  }

  /**
   * Browse backup.
   *
   * @throws FtpException If an FTP error occurs
   * @throws IOException If an I/O error occurs
   */
  public void browse() throws FtpException, IOException {
    browse(CONFIG.getClientName());
  }

  /**
   * Browse backup.
   *
   * @param clientName Client name
   * @throws FtpException If an FTP error occurs
   * @throws IOException If an I/O error occurs
   */
  public void browse(String clientName) throws FtpException, IOException {
    FtpServerFactory serverFactory = new FtpServerFactory();
    ListenerFactory factory = new ListenerFactory();
    factory.setPort(3715);
    serverFactory.addListener("default", factory.createListener());

    BaseUser user = new BaseUser();
    user.setName("anonymous");
    serverFactory.getUserManager().save(user);

    Map<String, Storage> map = Arrays.stream(CONFIG.getConnectedStorages())
        .map(n -> new Pair<>(n, getStorage(n)))
        .filter(p -> p.getSecond() != null)
        .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    BackupFileSystemFactory fileSystemFactory
        = new BackupFileSystemFactory(clientName, map);
    serverFactory.setFileSystem(fileSystemFactory);

    FtpServer server = serverFactory.createServer();
    server.start();

    if (Util.isWindows()) {
      Runtime.getRuntime().exec("explorer.exe " + FTP_URL
          + BackupFileSystemView.FORMATTER.format(new Date()));
    } else {
      System.out.println("Please open " + FTP_URL);
    }
  }

  /**
   * Get backup stats.
   *
   * @param name Storage name
   */
  public void stats(String name) {
    Storage storage = getStorage(name);
    SortedMap<String, Stats> allStats
        = storage.getDetailedClientStats(CONFIG.getClientName());

    PrettyTime pt = new PrettyTime(Locale.UK);

    for (Entry<String, Stats> entry : allStats.entrySet()) {
      Stats stats = entry.getValue();
      long time = stats.getLastUpdated();
      System.out.format("%s:\n    Last updated: %s\n    Files: %d\n"
          + "    Files without hash: %d\n    Missing file chunks: %d\n",
          entry.getKey(),
          time == 0 ? "never" : pt.format(new Date(time)),
          stats.getFiles(),
          stats.getFilesWithoutHash(),
          stats.getMissingChunks()
      );
    }
  }

  /**
   * Makes backup faster.
   */
  public void fast() {
    setFast(true);
  }

  /**
   * Makes backup slower.
   */
  public void nice() {
    setFast(false);
  }

  /**
   * Deletes unreferenced files.
   *
   * @param storageName Storage name
   */
  public void reclaimSpace(String storageName) {
    Storage storage = getStorage(storageName);
    Pair<Integer, Long> result = storage.deleteUnusedChunkFiles();
    System.out.format("%d files deleted for a total of %d bytes reclaimed\n",
        result.getFirst(), result.getSecond());
  }

  private void setFast(boolean fast) {
    Arrays.stream(CONFIG.getConnectedStorages())
        .forEach(name -> {
          ClientScheduler cs = FACTORY.obtain(ClientScheduler.class, name);

          if (cs != null) {
            cs.setFast(fast);
          }
        });
  }

  private Storage getStorage(String storageName) {
    String path = CONFIG.getConnectedStoragePath(storageName);
    EncryptionType encryptionType
        = CONFIG.getConnectedStorageEncryptionType(storageName);
    Storage storage = null;
    Pair<String, Integer> split = splitHostPort(path);

    if (split == null) {
      // local storage
      try {
        storage = LocalStorage.get(FilePath.get(path));
        System.out.format("Connected to local storage %s\n", path);
      } catch (InitializationException ex) {
        System.out.format("Storage server %s not started: %s\n", path,
            ex.getMessage());
      }
    } else {
      // remote storage
      String host = split.getFirst();
      int port = split.getSecond();
      storage = RemoteStorage.create(host, port,
          CONFIG.getConnectedStorageTransmissionPassword(storageName).toCharArray());
      System.out.format("Connected to remote storage %s\n", path);
    }

    if (storage != null) {
      switch (encryptionType) {
        case DATA:
          storage = EncryptedStorages.getEncryptedStorage(storage,
              CONFIG.getConnectedStorageFileEncryptionPassword(storageName).toCharArray(),
              false);
          System.out.format("Using data encryption on storage %s\n", path);
          break;
        case FULL:
          storage = EncryptedStorages.getEncryptedStorage(storage,
              CONFIG.getConnectedStorageFileEncryptionPassword(storageName).toCharArray(),
              true);
          System.out.format("Using full encryption on storage %s\n", path);
          break;
        case NONE:
        // leave storage unencrypted
        }
    }

    return storage;
  }

  /**
   * Extracts host name and port from a host in the format example.com:1234.
   *
   * @return a Pair containing hostname and port, or null if the passed string
   *         is not in the correct format
   */
  private static Pair<String, Integer> splitHostPort(String s) {
    Matcher m = HOST_PORT.matcher(s);

    if (m.matches()) {
      return new Pair<>(m.group(1), Integer.valueOf(m.group(2)));
    } else {
      return null;
    }
  }
}
