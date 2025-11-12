package com.cromoteca.bfts.storage;

import com.cromoteca.bfts.client.Configuration;
import com.cromoteca.bfts.model.Pair;
import com.cromoteca.bfts.util.FilePath;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class that centralizes the logic required to resolve connected storages
 * from the persisted desktop configuration. It supports both local and remote storages
 * and applies the configured encryption mode when required.
 */
public final class ConnectedStorageResolver {

  private static final Pattern HOST_PORT = Pattern.compile("(.+):(\\d+)");

  private ConnectedStorageResolver() {
    // Utility class
  }

  /**
   * Resolve a storage using the provided desktop configuration.
   *
   * @param configuration desktop configuration backing the storage connections
   * @param storageName   logical storage name
   * @return an initialized {@link Storage}
   */
  public static Storage getStorage(Configuration configuration, String storageName) {
    return getStorage(configuration, storageName, null);
  }

  /**
   * Resolve a storage using the provided desktop configuration.
   *
   * @param configuration desktop configuration backing the storage connections
   * @param storageName   logical storage name
   * @param logger        optional logger where informational messages will be printed
   * @return an initialized {@link Storage}
   */
  public static Storage getStorage(
      Configuration configuration, String storageName, PrintStream logger) {
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(storageName, "storageName");

    String path = configuration.getConnectedStoragePath(storageName);
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Storage path not configured for " + storageName);
    }

    EncryptionType encryptionType = configuration.getConnectedStorageEncryptionType(storageName);
    Storage storage = resolveStorage(configuration, storageName, path, logger);

    if (storage != null) {
      storage = applyEncryptionIfNeeded(configuration, storageName, encryptionType, storage, path, logger);
    }

    return storage;
  }

  /**
   * Lists the names of connected storages that have a configured path.
   */
  public static List<String> listConnectedStorageNames(Configuration configuration) {
    Objects.requireNonNull(configuration, "configuration");
    List<String> names = new ArrayList<>();
    for (String name : configuration.getConnectedStorages()) {
      String path = configuration.getConnectedStoragePath(name);
      if (path != null && !path.isBlank()) {
        names.add(name);
      }
    }
    return names;
  }

  /**
   * Resolves a requested storage name (or the only configured storage) to an actual storage name.
   */
  public static String resolveStorageName(Configuration configuration, String requestedStorage) {
    List<String> names = listConnectedStorageNames(configuration);
    if (names.isEmpty()) {
      throw new IllegalStateException(
          "No connected storages found; connect a storage from the desktop client first.");
    }

    if (requestedStorage != null) {
      if (!names.contains(requestedStorage)) {
        throw new IllegalArgumentException("Unknown connected storage: " + requestedStorage);
      }
      return requestedStorage;
    }

    if (names.size() == 1) {
      return names.get(0);
    }

    throw new IllegalArgumentException("Missing storage name; specify one of: " + String.join(", ", names));
  }

  private static Storage resolveStorage(
      Configuration configuration, String storageName, String path, PrintStream logger) {
    Pair<String, Integer> split = splitHostPort(path);
    if (split == null) {
      try {
        Storage storage = LocalStorage.get(FilePath.get(path));
        log(logger, "Connected to local storage %s", path);
        return storage;
      } catch (InitializationException ex) {
        throw new StorageException("Storage server " + path + " not started: " + ex.getMessage(), ex);
      }
    }

    String transmissionPassword = configuration.getConnectedStorageTransmissionPassword(storageName);
    if (transmissionPassword == null || transmissionPassword.isEmpty()) {
      throw new IllegalStateException("Transmission password not set for " + storageName);
    }

    Storage storage =
        RemoteStorage.create(split.getFirst(), split.getSecond(), transmissionPassword.toCharArray());
    log(logger, "Connected to remote storage %s", path);
    return storage;
  }

  private static Storage applyEncryptionIfNeeded(
      Configuration configuration,
      String storageName,
      EncryptionType encryptionType,
      Storage storage,
      String path,
      PrintStream logger) {
    if (encryptionType == null) {
      return storage;
    }

    switch (encryptionType) {
      case DATA:
        String dataPassword = configuration.getConnectedStorageFileEncryptionPassword(storageName);
        if (dataPassword == null || dataPassword.isEmpty()) {
          throw new IllegalStateException("File encryption password not set for " + storageName);
        }
        log(logger, "Using data encryption on storage %s", path);
        return EncryptedStorages.getEncryptedStorage(storage, dataPassword.toCharArray(), false);
      case FULL:
        String fullPassword = configuration.getConnectedStorageFileEncryptionPassword(storageName);
        if (fullPassword == null || fullPassword.isEmpty()) {
          throw new IllegalStateException("File encryption password not set for " + storageName);
        }
        log(logger, "Using full encryption on storage %s", path);
        return EncryptedStorages.getEncryptedStorage(storage, fullPassword.toCharArray(), true);
      case NONE:
      default:
        return storage;
    }
  }

  private static Pair<String, Integer> splitHostPort(String value) {
    Matcher matcher = HOST_PORT.matcher(value);
    if (matcher.matches()) {
      return new Pair<>(matcher.group(1), Integer.valueOf(matcher.group(2)));
    }
    return null;
  }

  private static void log(PrintStream logger, String pattern, Object... args) {
    if (logger != null) {
      logger.printf(pattern + "%n", args);
    }
  }
}
