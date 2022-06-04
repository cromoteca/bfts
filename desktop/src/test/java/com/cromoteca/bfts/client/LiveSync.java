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

import ch.qos.logback.classic.Level;
import com.cromoteca.bfts.cryptography.Cryptographer;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.restore.BackupFileSystemFactory;
import com.cromoteca.bfts.storage.EncryptedStorages;
import com.cromoteca.bfts.storage.InitializationException;
import com.cromoteca.bfts.storage.LocalStorage;
import com.cromoteca.bfts.storage.RemoteStorage;
import com.cromoteca.bfts.storage.RemoteStorageServer;
import com.cromoteca.bfts.storage.Storage;
import com.cromoteca.bfts.util.Container;
import com.cromoteca.bfts.util.FilePath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.slf4j.LoggerFactory;

public class LiveSync {
  private static final char[] STORAGE_PASSWORD = "myStoragePass".toCharArray();
  private static final char[] REMOTE_PASSWORD = "myRemotePass".toCharArray();

  public static void main(String[] args) throws Exception {
    ((ch.qos.logback.classic.Logger) LoggerFactory
        .getLogger(LocalStorage.class)).setLevel(Level.TRACE);

    FilePath testDir = FilePath.get(System.getProperty("java.io.tmpdir"),
        "LiveSync"); // TestUtils.getTestDir(LiveSync.class);
    Container<Boolean> newStorage = new Container<>();
    FilePath storageDir = testDir.resolve("storage");
    LocalStorage localStorage;

    try {
      StorageConfiguration config = new StorageConfiguration();
      localStorage = LocalStorage.init(storageDir, false, config);
      localStorage.addKeyPair(new Cryptographer(config.getSalt(),
          REMOTE_PASSWORD).generateKeyPair());
      newStorage.setValue(Boolean.TRUE);
    } catch (InitializationException ex) {
      localStorage = LocalStorage.get(storageDir);
      newStorage.setValue(Boolean.FALSE);
    }

    new RemoteStorageServer(localStorage).startHTTPServer(8715);
    Storage remoteStorage = RemoteStorage.create("localhost", 8715,
        REMOTE_PASSWORD);
    List<Params> clients = new ArrayList<>();
    clients.add(new Params("AAAA",
        EncryptedStorages.getEncryptedStorage(localStorage, STORAGE_PASSWORD,
            false), "*.bak;/tmp"));
    clients.add(new Params("BBBB",
        EncryptedStorages.getEncryptedStorage(remoteStorage, STORAGE_PASSWORD,
            false), "thumbs.db"));
    String sourceName = "sync";
    Filesystem fs = new Filesystem();
    fs.setFilesystemScanSize(20);

    ClientScheduler[] schedulers = clients.stream().map(c -> {
      try {
        FilePath clientPath = testDir.resolve(c.name);
        clientPath.createDirectories();
        ClientActivities ca
            = new ClientActivities(c.name, fs, c.storage, "remote", 5);

        if (newStorage.getValue()) {
          c.storage.addSource(c.name, sourceName, clientPath.toString());
          c.storage.setSourceSyncAttributes(c.name, sourceName, true, true);
          c.storage.setSourceIgnoredPatterns(c.name, sourceName, c.ignore);
        }

        ClientScheduler cs = new ClientScheduler(ca, 500, 5000);
        cs.start();
        cs.setFast(true);
        return cs;
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }).toArray(ClientScheduler[]::new);

    System.out.println("Commands: SLOW, FAST (default), QUIT");

    FtpServerFactory serverFactory = new FtpServerFactory();
    ListenerFactory factory = new ListenerFactory();
    factory.setPort(3715);
    serverFactory.addListener("default", factory.createListener());

    BaseUser user = new BaseUser();
    user.setName("anonymous");
    serverFactory.getUserManager().save(user);

    BackupFileSystemFactory fileSystemFactory
        = new BackupFileSystemFactory("AAAA",
            Collections.singletonMap("AAAA", clients.get(0).storage));
    serverFactory.setFileSystem(fileSystemFactory);

    FtpServer ftpServer = serverFactory.createServer();
    ftpServer.start();

    boolean running = true;

    try (Scanner scanner = new Scanner(System.in)) {
      while (running) {
        switch (scanner.next().toLowerCase()) {
          case "slow":
            for (ClientScheduler scheduler : schedulers) {
              scheduler.setFast(false);
            }

            break;

          case "fast":
            for (ClientScheduler scheduler : schedulers) {
              scheduler.setFast(true);
            }

            break;

          case "quit":
            running = false;
            ftpServer.stop();

            for (ClientScheduler scheduler : schedulers) {
              scheduler.stop();
            }

            break;
        }
      }
    }

    System.exit(0);
  }

  static class Params {
    String name;
    Storage storage;
    String ignore;

    public Params(String name, Storage storage, String ignore) {
      this.name = name;
      this.storage = storage;
      this.ignore = ignore;
    }
  }
}
