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
import com.cromoteca.bfts.storage.EncryptionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.Console;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import org.apache.ftpserver.ftplet.FtpException;

/**
 * A shell that can be used to configure backups and run them interactively. It
 * is also able to run the program without interaction by passing some options
 * as command line arguments.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class CommandLine {
  private Shell shell;
  private final ClientAPI client;

  public CommandLine() {
    client = new ClientAPI();
  }

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && "start".equals(args[0])) {
      CommandLine cl = new CommandLine();

      cl.start();

      if (args.length > 1) {
        if (Boolean.parseBoolean(args[1])) {
          cl.fast();
        } else {
          cl.nice();
        }
      }
    } else if (args.length > 2 && "init".equals(args[0])) {
      CommandLine cl = new CommandLine();

      String name = args[1];
      String path = args[2];
      cl.init(name, path, false);

      if (args.length > 3) {
        int port = Integer.parseInt(args[3]);
        cl.publish(name, port);
      }

      cl.quit();
    } else if (args.length > 0 && ("shell".equals(args[0]) || "cli".equals(args[0]))) {
      CommandLine commandLine = new CommandLine();
      String name = commandLine.client.getClientName();

      if (name == null) {
        name = "";
      }

      commandLine.shell = ShellFactory.createConsoleShell(name, "BFTS", commandLine);
      commandLine.shell.commandLoop(); // does not return until exit command is used
    } else {
      GUI.main(args);
    }
  }

  private char[] askPassword(String prompt) {
    Console console = System.console();

    if (console == null) {
      throw new IllegalStateException("Console is not available.");
    }

    System.out.print(prompt);
    return console.readPassword();
  }

  @Command(description
      = "Quits the program (don't use EXIT as is causes thread locks")
  public void quit() {
    client.quit();
  }

  @Command(description = "Cancels the whole configuration")
  public void clearConfiguration() {
    client.clearConfiguration();
  }

  @Command(description = "Sets the name of this client")
  public void name(@Param(name = "Client name") String name) {
    client.name(name);

    if (shell != null) {
      String currentName = client.getClientName();

      if (currentName == null) {
        currentName = "";
      }

      shell.setPath(Collections.singletonList(currentName));
    }
  }

  @Command(description = "Initializes a new local storage")
  public void init(@Param(name = "Storage name") String name,
      @Param(name = "Storage path") String path,
      @Param(name = "In memory database") boolean inMemory) {
    System.out.print(client.init(name, path, inMemory));
  }

  @Command(abbrev = "pub", description = "Publishes a local storage over HTTP")
  public void publish(@Param(name = "Storage name") String name,
      @Param(name = "HTTP port") int port)
      throws GeneralSecurityException {
    char[] passwordChars = askPassword("Transmission password: ");
    String password = new String(passwordChars);
    Arrays.fill(passwordChars, '\0');
    System.out.print(client.publish(name, port, password));
  }

  @Command(description = "Connects to a storage")
  public void connect(@Param(name = "Connection name") String name,
      @Param(name = "Storage path",
          description = "Local dir or host:port") String path,
      @Param(name = "Encryption (none, data, full)") String encryption) {
    EncryptionType encryptionType = EncryptionType.fromString(encryption);

    char[] transmissionChars = askPassword("Transmission password: ");
    String transmissionPassword = new String(transmissionChars);
    Arrays.fill(transmissionChars, '\0');

    String fileEncryptionPassword = null;

    if (encryptionType != EncryptionType.NONE) {
      char[] fileChars = askPassword("File encryption password: ");
      fileEncryptionPassword = new String(fileChars);
      Arrays.fill(fileChars, '\0');
    }

    client.connect(name, path, encryption, transmissionPassword, fileEncryptionPassword);
  }

  @Command(description = "Adds a backup source to a storage")
  public void add(@Param(name = "Connection name") String storageName,
      @Param(name = "Source name") String name,
      @Param(name = "Source path") String path) throws IOException {
    System.out.print(client.add(storageName, name, path));
  }

  @Command(description = "Change source priority")
  public void priority(@Param(name = "Connection name") String storageName,
      @Param(name = "Source name") String name,
      @Param(name = "Priority") int priority) {
    client.priority(storageName, name, priority);
  }

  @Command(description = "Lists local and connected storages")
  public void list() {
    ObjectNode result = client.list();
    System.out.println("Local storages:");
    ArrayNode localStorages = (ArrayNode) result.get("localStorages");

    if (localStorages == null || localStorages.isEmpty()) {
      System.out.println("    none");
    } else {
      for (JsonNode node : localStorages) {
        String name = node.path("name").asText();
        String path = node.path("path").asText("");
        System.out.format("    %s on path %s", name, path);
        int port = node.path("port").asInt(0);

        if (port > 0) {
          System.out.format(", published on port %d", port);
        }

        System.out.println();
      }
    }

    System.out.println("Connected storages:");
    ArrayNode connectedStorages = (ArrayNode) result.get("connectedStorages");

    if (connectedStorages == null || connectedStorages.isEmpty()) {
      System.out.println("    none");
    } else {
      for (JsonNode node : connectedStorages) {
        String name = node.path("name").asText();
        String path = node.path("path").asText("");
        String encryptionType = node.path("encryption").asText("none");
        System.out.format("    %s on path %s (encryption: %s)\n", name, path,
            encryptionType);
      }
    }
  }

  @Command(description = "Lists sources backed up on a storage")
  public String list(@Param(name = "Connection name") String name) {
    return client.list(name);
  }

  @Command(description = "Make a complete backup of all sources")
  public void complete(@Param(name = "Storage name") String storageName) {
    client.complete(storageName);
  }

  @Command(description = "Make a complete backup of a source")
  public void complete(@Param(name = "Storage name") String storageName,
      @Param(name = "Source name") String sourceName) {
    client.complete(storageName, sourceName);
  }

  @Command(abbrev = "start", description = "Starts all backups")
  public void start() {
    client.start();
  }

  @Command(abbrev = "start", description = "Starts a backup")
  public void start(@Param(name = "Storage name") String name) {
    client.start(name);
  }

  @Command(abbrev = "stop", description = "Stops all backups")
  public void stop() {
    client.stop();
  }

  @Command(abbrev = "stop", description = "Stops a backup")
  public void stop(@Param(name = "Storage name") String name) {
    client.stop(name);
  }

  @Command(description = "Browse backup")
  public void browse() throws FtpException, IOException {
    client.browse();
  }

  @Command(description = "Browse backup")
  public void browse(@Param(name = "Client name") String clientName)
      throws FtpException, IOException {
    client.browse(clientName);
  }

  @Command(description = "Get backup stats")
  public void stats(@Param(name = "Storage name") String name) {
    client.stats(name);
  }

  @Command(description = "Makes backup faster")
  public void fast() {
    client.fast();
  }

  @Command(description = "Makes backup slower")
  public void nice() {
    client.nice();
  }

  @Command(description = "Deletes unreferenced files")
  public void reclaimSpace(@Param(name = "Storage name") String storageName) {
    client.reclaimSpace(storageName);
  }
}
