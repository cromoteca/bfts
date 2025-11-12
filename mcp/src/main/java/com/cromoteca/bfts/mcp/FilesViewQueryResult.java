package com.cromoteca.bfts.mcp;

import java.util.List;
import java.util.Objects;

/**
 * Lightweight DTO for a table-like result. The MCP action will serialize this
 * into the protocol-specific response format once the SDK is wired.
 */
public final class FilesViewQueryResult {

  private final String storageName;
  private final List<String> columns;
  private final List<List<Object>> rows;

  public FilesViewQueryResult(
      String storageName, List<String> columns, List<List<Object>> rows) {
    this.storageName = storageName;
    this.columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
    this.rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
  }

  public String getStorageName() {
    return storageName;
  }

  public List<String> getColumns() {
    return columns;
  }

  public List<List<Object>> getRows() {
    return rows;
  }
}
