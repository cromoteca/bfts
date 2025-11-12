package com.cromoteca.bfts.mcp;

import com.cromoteca.bfts.client.Configuration;
import com.cromoteca.bfts.storage.ConnectedStorageResolver;
import com.cromoteca.bfts.storage.TabularQueryResult;
import com.cromoteca.bfts.storage.Storage;
import java.util.Objects;

/**
 * Executes read-only queries against {@code friendly_file_view} on connected storages.
 */
final class ConnectedStorageFilesViewQueryService implements FilesViewQueryService {

  private final Configuration configuration;

  ConnectedStorageFilesViewQueryService(Configuration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
  }

  @Override
  public FilesViewQueryResult execute(FilesViewQueryRequest request) {
    FilesViewQueryRequest sanitized = FilesViewQueryRequestValidator.validate(request);

    String storageName =
        ConnectedStorageResolver.resolveStorageName(configuration, sanitized.getStorageName());
    Storage storage = ConnectedStorageResolver.getStorage(configuration, storageName);

    TabularQueryResult result;
    try {
      result =
          storage.queryFilesView(
              sanitized.getColumns(),
              sanitized.getWhere(),
              sanitized.getOrderBy(),
              sanitized.getLimit());
    } catch (RuntimeException ex) {
      throw new StorageQueryException("Failed to query storage '" + storageName + "'", ex);
    }

    return new FilesViewQueryResult(storageName, result.getColumns(), result.getRows());
  }

  private static final class StorageQueryException extends RuntimeException {
    private StorageQueryException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
