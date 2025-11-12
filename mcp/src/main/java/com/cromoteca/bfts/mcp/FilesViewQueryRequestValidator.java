package com.cromoteca.bfts.mcp;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Performs lightweight validation and normalization on {@link FilesViewQueryRequest}
 * instances before they are sent over the control port. The heavy SQL enforcement still
 * lives on the server side, but this provides early feedback and clamps obviously unsafe
 * requests.
 */
final class FilesViewQueryRequestValidator {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");
  private static final Pattern UNSAFE_SQL_TOKENS = Pattern.compile("(;|--|/\\*)");
  private static final List<String> DEFAULT_COLUMNS = List.of(
      "id",
      "name",
      "parent",
      "size",
      "lastModifiedTime",
      "hashString",
      "createdTime",
      "status",
      "deletedTime",
      "source.id",
      "source.client",
      "source.name",
      "source.rootPath",
      "source.lastUpdatedDate",
      "uploaded");
  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 500;

  private FilesViewQueryRequestValidator() {}

  static FilesViewQueryRequest validate(FilesViewQueryRequest request) {
    Objects.requireNonNull(request, "request");

    List<String> normalizedColumns = normalizeColumns(request.getColumns());
    String normalizedWhere = normalizeClause(request.getWhere());
    String normalizedOrder = normalizeClause(request.getOrderBy());
    int normalizedLimit = normalizeLimit(request.getLimit());
    String normalizedStorage = normalizeStorageName(request.getStorageName());

    return new FilesViewQueryRequest(
        normalizedColumns, normalizedWhere, normalizedOrder, normalizedLimit, normalizedStorage);
  }

  private static List<String> normalizeColumns(List<String> columns) {
    if (columns == null) {
      return DEFAULT_COLUMNS;
    }
    List<String> normalized = columns.stream()
        .map(FilesViewQueryRequestValidator::trimToNull)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    if (normalized.isEmpty()) {
      return DEFAULT_COLUMNS;
    }
    if (normalized.size() == 1 && "*".equals(normalized.get(0))) {
      return DEFAULT_COLUMNS;
    }
    for (String column : normalized) {
      if (!IDENTIFIER.matcher(column).matches()) {
        throw new IllegalArgumentException("Invalid column identifier: " + column);
      }
    }
    return List.copyOf(normalized);
  }

  private static String normalizeClause(String clause) {
    return ensureSafeClause(trimToNull(clause));
  }

  private static int normalizeLimit(int limit) {
    if (limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private static String normalizeStorageName(String storageName) {
    return trimToNull(storageName);
  }

  private static String ensureSafeClause(String clause) {
    if (clause == null) {
      return null;
    }
    if (UNSAFE_SQL_TOKENS.matcher(clause).find()) {
      throw new IllegalArgumentException("Clause contains unsupported tokens");
    }
    return clause;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
