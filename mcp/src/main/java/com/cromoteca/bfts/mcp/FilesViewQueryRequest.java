package com.cromoteca.bfts.mcp;

import java.util.List;
import java.util.Objects;

/**
 * Parameters accepted by the MCP surface to query {@code friendly_file_view}.
 * The class keeps validation lightweight so the desktop client can perform
 * the heavy lifting securely before reaching the database.
 */
public final class FilesViewQueryRequest {

  private final List<String> columns;
  private final String where;
  private final String orderBy;
  private final int limit;
  private final String storageName;

  public FilesViewQueryRequest(
      List<String> columns,
      String where,
      String orderBy,
      int limit,
      String storageName) {
    this.columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
    this.where = where;
    this.orderBy = orderBy;
    this.limit = limit;
    this.storageName = storageName;
  }

  public List<String> getColumns() {
    return columns;
  }

  public String getWhere() {
    return where;
  }

  public String getOrderBy() {
    return orderBy;
  }

  public int getLimit() {
    return limit;
  }

  public String getStorageName() {
    return storageName;
  }
}
