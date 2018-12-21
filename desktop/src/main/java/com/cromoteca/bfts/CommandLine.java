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

import asg.cliche.Command;
import asg.cliche.Param;
import asg.cliche.Shell;
import asg.cliche.ShellFactory;
import com.cromoteca.bfts.client.ClientActivities;
import com.cromoteca.bfts.client.ClientScheduler;
import com.cromoteca.bfts.client.Configuration;
import com.cromoteca.bfts.client.Filesystem;
import com.cromoteca.bfts.cryptography.Cryptographer;
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
import java.io.Console;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.ocpsoft.prettytime.PrettyTime;

/**
 * A shell that can be used to configure backups and run them interactively. It
 * is also able to run the program without interaction by passing some options
 * as command line arguments. Examples:
 *
 * <pre>java -jar bfts.jar</pre> shows a prompt;
 *
 * <pre>java -jar bfts.jar start true/false password</pre> runs the program in
 * fast/nice mode using the password when needed.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class CommandLine {
  private static final String FTP_URL = "ftp://localhost:3715/";
  private static final Pattern HOST_PORT = Pattern.compile("(.+):(\\d+)");
  private static final Factory FACTORY = new Factory();
  private static Shell shell;
  private static final Configuration CONFIG
      = new Configuration(Preferences.userNodeForPackage(CommandLine.class));
  private char[] password;

  public static void main(String[] args) throws Exception {
    // example: java -jar bfts.jar start true mypassword
    if (args.length > 0 && "start".equals(args[0])) {
      // run the backup immediately
      CommandLine cl = new CommandLine();

      if (args.length > 2) {
        cl.password = args[2].toCharArray();
      }

      cl.start();

      if (args.length > 1) {
        cl.setFast(Boolean.parseBoolean(args[1]));
      }
    } else {
      // use client name as prompt
      String name = CONFIG.getClientName();

      if (name == null) {
        name = "";
      }

      shell = ShellFactory.createConsoleShell(name, "BFTS", new CommandLine());
      shell.commandLoop(); // does not return until exit command is used
    }
  }

  @Command(description
      = "Quits the program (don't use EXIT as is causes thread locks")
  public void quit() {
    System.exit(0);
  }

  @Command(description = "Cancels the whole configuration")
  public void clearConfiguration() {
    CONFIG.remove();
    System.out.println("Configuration cleared, exiting");
    quit();
  }

  @Command(description = "Sets the name of this client")
  public void name(@Param(name = "Client name") String name) {
    if (!Util.validName(name)) {
      System.err.println("Name is not valid");
    } else {
      CONFIG.setClientName(name);
      shell.setPath(Arrays.asList(new String[] { name }));
    }
  }

  /**
   * Prompt for a password if it has not been stored previously.
   */
  private char[] askPassword() {
    if (password != null) {
      System.out.println("Using previously provided password");
      return password;
    }

    Console console = System.console();

    if (console == null) {
      throw new IllegalStateException("Console is not available."
          + " Use the 'password' command to store your password");
    }

    System.out.print("Enter password: ");
    return console.readPassword();
  }

  /**
   * Note: a stored password will be used for both storage encryption and HTTP
   * encryption. This is usually fine, since most users don't want to remember
   * multiple passwords, but it is good to remember that every backup can have
   * its own password and that the HTTP one is independent too.
   */
  @Command(description = "Prompts for password and keeps it in memory instead"
      + " of asking for it every time")
  public void password() {
    password = askPassword();
  }

  @Command(description = "Keeps in memory a password passed as parameter,"
      + " useful when console is not available")
  public void password(@Param(name = "Password") String password) {
    this.password = password.toCharArray();
  }

  @Command(description = "Forgets current password")
  public void noPassword() {
    password = null;
  }

  @Command(description = "Initializes a new local storage")
  public void init(@Param(name = "Storage name") String name,
      @Param(name = "Storage path") String path,
      @Param(name = "In memory database") boolean inMemory) {
    StorageConfiguration storageConfig = new StorageConfiguration();
    LocalStorage.init(FilePath.get(path), inMemory, storageConfig);
    CONFIG.setLocalStoragePath(name, path);
    System.out.format("Local storage %s initialized in directory %s\n", name,
        path);
  }

  @Command(abbrev = "pub", description = "Publishes a local storage over HTTP")
  public void publish(@Param(name = "Storage name") String name,
      @Param(name = "HTTP port") int port) throws GeneralSecurityException {
    String path = CONFIG.getLocalStoragePath(name);
    LocalStorage storage = LocalStorage.get(FilePath.get(path));
    StorageConfiguration storageConfig = storage.getStorageConfiguration();

    // The HTTP connection needs a key pair to encrypt exchanged data
    Cryptographer crypto = new Cryptographer(storageConfig.getSalt(),
        askPassword());
    storage.addKeyPair(crypto.generateKeyPair());
    CONFIG.setLocalStoragePort(name, port);
    System.out.format("Storage %s published on port %d\n", name, port);
  }

  @Command(description = "Connects to a storage")
  public void connect(@Param(name = "Connection name") String name,
      @Param(name = "Storage path",
          description = "Local dir or host:port") String path,
      @Param(name = "Encryption (none, data, full)") String encryption) {
    EncryptionType encryptionType = EncryptionType.fromString(encryption);
    CONFIG.setConnectedStoragePath(name, path);
    CONFIG.setConnectedStorageEncryptionType(name, encryptionType);
    System.out.format("Prepared connection to storage %s as %s\n", name,
        CONFIG.getClientName());
  }

  @Command(description = "Adds a backup source to a storage")
  public void add(@Param(name = "Connection name") String storageName,
      @Param(name = "Source name") String name,
      @Param(name = "Source path") String path) throws IOException {
    if (!Util.validName(name)) {
      System.err.println("Source name is not valid");
    } else {
      FilePath directory = FilePath.get(path);

      if (directory.isDirectory()) {
        Storage storage = getStorage(storageName);
        storage.addSource(CONFIG.getClientName(), name, path);
        System.out.format("Added source %s to storage %s\n", name, storageName);
      } else {
        System.out.format("%s is not a directory\n", path);
      }
    }
  }

  @Command(description = "Change source priority")
  public void priority(@Param(name = "Connection name") String storageName,
      @Param(name = "Source name") String name,
      @Param(name = "Priority") int priority) {
    Storage storage = getStorage(storageName);
    storage.setSourcePriority(CONFIG.getClientName(), name, priority);
    System.out.format("Priority for %s set to %d\n", name, priority);
  }

  @Command(description = "Lists local and connected storages")
  public void list() {
    System.out.println("Local storages:");
    String[] localStorages = CONFIG.getLocalStorages();

    if (localStorages.length == 0) {
      System.out.println("    none");
    } else {
      for (String name : localStorages) {
        String path = CONFIG.getLocalStoragePath(name);
        System.out.format("    %s on path %s", name, path);
        int port = CONFIG.getLocalStoragePort(name);

        if (port > 0) {
          System.out.format(", published on port %d", port);
        }

        System.out.println();
      }
    }

    System.out.println("Connected storages:");
    String[] connectedStorages = CONFIG.getConnectedStorages();

    if (connectedStorages.length == 0) {
      System.out.println("    none");
    } else {
      for (String name : connectedStorages) {
        String path = CONFIG.getConnectedStoragePath(name);
        EncryptionType encryptionType
            = CONFIG.getConnectedStorageEncryptionType(name);
        System.out.format("    %s on path %s (encryption: %s)\n", name, path,
            encryptionType.toString().toLowerCase());
      }
    }
  }

  @Command(description = "Lists sources backed up on a storage")
  public String list(@Param(name = "Connection name") String name) {
    Storage storage = getStorage(name);
    List<Source> sources = storage.selectSources(CONFIG.getClientName());
    return sources.stream()
        .map(source -> String.format("    %s on path %s with priority %d",
        source.getName(), source.getRootPath(), source.getPriority()))
        .collect(Collectors.joining("\n"));
  }

  @Command(description = "Make a complete backup of a source")
  public void complete(@Param(name = "Storage name") String storageName) {
    Storage storage = getStorage(storageName);
    Filesystem fs = new Filesystem();
    fs.setFilesystemScanSize(Integer.MAX_VALUE);
    ClientActivities ca = new ClientActivities(CONFIG.getClientName(), fs,
        storage, storageName, CONFIG.getLongOperationDuration());

    for (Source source : storage.selectSources(CONFIG.getClientName())) {
      doCompleteBackup(ca, source.getName());
    }
  }

  @Command(description = "Make a complete backup of a source")
  public void complete(@Param(name = "Storage name") String storageName,
      @Param(name = "Source name") String sourceName) {
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
      System.out.format("Source %s is not available at the moment\n",
          sourceName);
    } else {
      ca.sendFiles(source);
      for (int n = 1; n > 0; n = ca.syncDeletions(source, true).size());
      for (int n = 1; n > 0; n = ca.syncAdditions(source, true).size());
      for (int n = 1; n > 0; n = ca.sendHashes(FileStatus.CURRENT,
          source.getId()));
      for (int n = 1; n > 0; n = ca.uploadChunks(FileStatus.CURRENT,
          source.getId()));
    }
  }

  @Command(abbrev = "start", description = "Starts all backups")
  public void start() {
    start(null);
  }

  @Command(abbrev = "start", description = "Starts a backup")
  public void start(@Param(name = "Storage name") String name) {
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
          ClientScheduler cs = new ClientScheduler(ca, 5000);
          FACTORY.registerSingleton(ClientScheduler.class, n, cs);
          cs.start();
          System.out.format("Client scheduler started for %s\n", n);
        }
      } catch (StorageException ex) {
        ex.printStackTrace(System.err);
      }
    });
  }

  @Command(abbrev = "stop", description = "Stops all backups")
  public void stop() {
    stop(null);
  }

  @Command(abbrev = "stop", description = "Stops a backup")
  public void stop(@Param(name = "Storage name") String name) {
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

  // Not related to BFTS
  // @Command(description = "Browse filesystem via FTP")
  public void ftpFilesystem() throws FtpException, IOException {
    FtpServerFactory serverFactory = new FtpServerFactory();
    ListenerFactory factory = new ListenerFactory();
    factory.setPort(9713);
    serverFactory.addListener("default", factory.createListener());

    BaseUser user = new BaseUser();
    user.setName("anonymous");
    serverFactory.getUserManager().save(user);

    NativeFileSystemFactory fileSystemFactory = new NativeFileSystemFactory();
    serverFactory.setFileSystem(fileSystemFactory);

    FtpServer server = serverFactory.createServer();
    server.start();

    if (Util.isWindows()) {
      Runtime.getRuntime().exec("explorer.exe " + FTP_URL);
    } else {
      System.out.println("Please open " + FTP_URL);
    }
  }

  @Command(description = "Browse backup")
  public void browse() throws FtpException, IOException {
    browse(CONFIG.getClientName());
  }

  @Command(description = "Browse backup")
  public void browse(@Param(name = "Client name") String clientName)
      throws FtpException, IOException {
    FtpServerFactory serverFactory = new FtpServerFactory();
    ListenerFactory factory = new ListenerFactory();
    factory.setPort(3715);
    serverFactory.addListener("default", factory.createListener());

    BaseUser user = new BaseUser();
    user.setName("anonymous");
    serverFactory.getUserManager().save(user);

    BackupFileSystemFactory fileSystemFactory = new BackupFileSystemFactory(
        clientName, Arrays.stream(CONFIG.getConnectedStorages())
            .collect(Collectors.toMap(Function.identity(), this::getStorage)));
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

  @Command(description = "Get backup stats")
  public void stats(@Param(name = "Storage name") String name) {
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

  @Command(description = "Makes backup faster")
  public void fast() {
    setFast(true);
  }

  @Command(description = "Makes backup slower")
  public void nice() {
    setFast(false);
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
      storage = RemoteStorage.create(host, port, askPassword());
      System.out.format("Connected to remote storage %s\n", path);
    }

    if (storage != null) {
      switch (encryptionType) {
        case DATA:
          storage = EncryptedStorages.getEncryptedStorage(storage,
              askPassword(), false);
          System.out.format("Using data encryption on storage %s\n", path);
          break;
        case FULL:
          storage = EncryptedStorages.getEncryptedStorage(storage,
              askPassword(), true);
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
      return new Pair<>(m.group(1), Integer.parseInt(m.group(2)));
    } else {
      return null;
    }
  }
}
