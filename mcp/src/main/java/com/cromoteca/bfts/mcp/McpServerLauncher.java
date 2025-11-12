package com.cromoteca.bfts.mcp;

import com.cromoteca.bfts.client.Configuration;

import com.cromoteca.bfts.storage.ConnectedStorageResolver;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpServerTransportProvider;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bootstrap entry point for the MCP server. It exposes a read-only tool that queries
 * {@code friendly_file_view} from the configured backup storages via the desktop module.
 */
public final class McpServerLauncher {

  private static final String SERVER_NAME = "bfts-mcp";
  private static final String SERVER_VERSION = "0.1.0";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final McpJsonMapper JSON_MAPPER = McpJsonMapper.getDefault();
  private static final String FILES_VIEW_TOOL_NAME = "files-view-query";
  private static final String LIST_STORAGES_TOOL_NAME = "list-connected-storages";
  private static final String TOOL_DESCRIPTION = """
      Explore backed-up files stored in the `friendly_file_view`. Each row includes:
       • 'id': monotonically increasing identifier (newer sightings → higher ids)
       • 'name': filename
       • 'parent': directory relative to the source root
       • 'size': bytes
       • 'lastModifiedTime': original mtime on the backed-up machine
       • 'hashString': shortened content hash (identical values → identical content)
       • 'createdTime': first time the backup saw the file
       • 'deletedTime': when the backup noticed the deletion (`NULL` means the file still exists)
       • 'uploaded': `1` when the file’s chunks are fully stored, otherwise upload is pending
       • source metadata: 'source.id', 'source.name', 'source.path', 'source.lastUpdatedDate'
      Note: there is a 'status' column in the view but it is not meaningful—prefer 'deletedTime' to detect removals.

      Provide SQL-style WHERE/ORDER BY clauses (without the keywords) and list the columns you want to project. Queries are executed against SQLite, so use SQLite-compatible syntax. Use the 'list-connected-storages' tool to discover storage names when multiple are configured.
      """;
  private static final String LIST_STORAGES_DESCRIPTION = """
      Return the storage names currently connected in the desktop client configuration.
      """;
  private static final String EMPTY_OBJECT_SCHEMA = """
      {
        "type": "object",
        "additionalProperties": false
      }
      """;
  private static final String SERVER_INSTRUCTIONS = """
      Use 'list-connected-storages' to learn which storages are available, then call 'files-view-query' to inspect backed-up files in one of these storages. The query tool returns rows with file metadata (path components, size, timestamps, status/deletion info, hash) and source metadata (client id/name/rootPath and last sync time). Supply optional SQL WHERE/ORDER BY clauses (without the keywords) and an optional LIMIT (default 50, max 500). Queries are executed against SQLite, so stick to SQLite-compatible SQL.
      """;
  private static final String TOOL_INPUT_SCHEMA = """
      {
        "type": "object",
        "properties": {
          "columns": {
            "type": "array",
            "items": { "type": "string" },
            "description": "Columns to project; leave empty or use '*' for defaults."
          },
          "where": {
            "type": "string",
            "description": "Optional SQL WHERE clause (without the 'WHERE' keyword)."
          },
          "orderBy": {
            "type": "string",
            "description": "ORDER BY clause, e.g. 'lastModifiedTime desc'."
          },
          "limit": {
            "type": "integer",
            "minimum": 1,
            "maximum": 500,
            "description": "Maximum number of rows to return (default 50, max 500)."
          },
          "storage": {
            "type": "string",
            "description": "Name of the connected storage to query when multiple exist."
          }
        },
        "additionalProperties": false
      }
      """;

  private McpServerLauncher() {
    // Utility class
  }

  public static void main(String[] args) {
    try {
      run();
    } catch (Exception ex) {
      reportStartupFailure(ex);
      System.exit(1);
    }
  }

  private static void run() {
    Configuration configuration = Configuration.load();
    FilesViewQueryService queryService =
        new ConnectedStorageFilesViewQueryService(configuration);

    CloseSignal closeSignal = new CloseSignal();
    StdioServerTransportProvider transportProvider = createTransportProvider(closeSignal);
    McpSyncServer server = buildServer(configuration, queryService, transportProvider);
    runServer(server, closeSignal);
  }

  private static McpSyncServer buildServer(
      Configuration configuration,
      FilesViewQueryService queryService,
      McpServerTransportProvider transportProvider) {
    return McpServer.sync(transportProvider)
        .serverInfo(SERVER_NAME, SERVER_VERSION)
        .instructions(SERVER_INSTRUCTIONS.strip())
        .capabilities(createServerCapabilities())
        .requestTimeout(REQUEST_TIMEOUT)
        .immediateExecution(true)
        .tool(
            createListStoragesTool(),
            (exchange, params) -> handleListStoragesCall(configuration))
        .tool(
            createFilesViewTool(),
            (exchange, params) -> handleFilesViewQuery(queryService, params))
        .build();
  }

  private static Tool createFilesViewTool() {
    return Tool.builder()
        .name(FILES_VIEW_TOOL_NAME)
        .title("Files View Query")
        .description(TOOL_DESCRIPTION.strip())
        .inputSchema(JSON_MAPPER, TOOL_INPUT_SCHEMA.strip())
        .build();
  }

  private static Tool createListStoragesTool() {
    return Tool.builder()
        .name(LIST_STORAGES_TOOL_NAME)
        .title("List Connected Storages")
        .description(LIST_STORAGES_DESCRIPTION.strip())
        .inputSchema(JSON_MAPPER, EMPTY_OBJECT_SCHEMA.strip())
        .build();
  }

  private static CallToolResult handleFilesViewQuery(
      FilesViewQueryService queryService, Map<String, Object> params) {
    try {
      FilesViewQueryRequest request = toRequest(params);
      FilesViewQueryResult result = queryService.execute(request);
      String payload = serializeResult(result);
      return new CallToolResult(List.of(new TextContent(payload)), false);
    } catch (Throwable t) {
      return new CallToolResult(List.of(new TextContent(formatException(t))), true);
    }
  }

  private static CallToolResult handleListStoragesCall(Configuration configuration) {
    try {
      List<String> names = ConnectedStorageResolver.listConnectedStorageNames(configuration);
      String payload = serializeStorageNames(names);
      return new CallToolResult(List.of(new TextContent(payload)), false);
    } catch (Exception ex) {
      return new CallToolResult(List.of(new TextContent(formatException(ex))), true);
    }
  }

  private static String formatException(Throwable throwable) {
    if (throwable == null) {
      return "";
    }

    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    StringBuilder builder = new StringBuilder();
    boolean first = true;

    for (Throwable current = throwable;
        current != null && visited.add(current);
        current = current.getCause()) {
      if (!first) {
        builder.append(" | caused by: ");
      }
      first = false;

      builder.append(current.getClass().getName());
      String message = current.getMessage();
      if (message != null && !message.isBlank()) {
        builder.append(": ").append(message.trim());
      }
    }

    return builder.toString();
  }

  private static FilesViewQueryRequest toRequest(Map<String, Object> params) {
    List<String> columns = extractStringList(params.get("columns"));
    String where = extractString(params.get("where"));
    Object orderCandidate =
        params.containsKey("orderBy") ? params.get("orderBy") : params.get("order");
    String orderBy = extractString(orderCandidate);
    int limit = parseLimit(params.get("limit"));
    Object storageCandidate =
        params.containsKey("storage") ? params.get("storage") : params.get("storageName");
    String storage = extractString(storageCandidate);

    return FilesViewQueryRequestValidator.validate(
        new FilesViewQueryRequest(columns, where, orderBy, limit, storage));
  }

  private static List<String> extractStringList(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof List<?> raw) {
      var result = new ArrayList<String>(raw.size());
      for (var element : raw) {
        if (element instanceof String s) {
          result.add(s);
        }
      }
      return result;
    }
    if (value instanceof String s) {
      return new ArrayList<>(List.of(s));
    }
    return null;
  }

  private static String extractString(Object value) {
    if (value instanceof String s) {
      String trimmed = s.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }
    return null;
  }

  private static int parseLimit(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String s) {
      try {
        return Integer.parseInt(s.trim());
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }
    return 0;
  }

  private static String serializeResult(FilesViewQueryResult result) {
    Map<String, Object> payload = new LinkedHashMap<>();
    if (result.getStorageName() != null) {
      payload.put("storage", result.getStorageName());
    }
    payload.put("columns", result.getColumns());
    payload.put("rowCount", result.getRows().size());
    payload.put("rows", result.getRows());

    try {
      return JSON_MAPPER.writeValueAsString(payload);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to serialize query result", ex);
    }
  }

  private static String serializeStorageNames(List<String> names) {
    Map<String, Object> payload = Map.of("storages", names);
    try {
      return JSON_MAPPER.writeValueAsString(payload);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to serialize storage list", ex);
    }
  }

  private static void reportStartupFailure(Exception ex) {
    Throwable root = ex;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    System.err.println("[bfts-mcp] Startup failed: " + root.getMessage());
    ex.printStackTrace(System.err);
    System.err.flush();
  }

  private static void runServer(McpSyncServer server, CloseSignal closeSignal) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  closeSignal.signal();
                  server.closeGracefully();
                },
                "mcp-server-shutdown"));

    closeSignal.await();
    server.closeGracefully();
  }

  private static McpSchema.ServerCapabilities createServerCapabilities() {
    return new McpSchema.ServerCapabilities.Builder()
        .tools(true)
        .resources(false, false)
        .prompts(false)
        .build();
  }

  private static StdioServerTransportProvider createTransportProvider(CloseSignal closeSignal) {
    return new StdioServerTransportProvider(
        JSON_MAPPER, new CloseAwareInputStream(System.in, closeSignal), System.out);
  }

  private static final class CloseSignal {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicBoolean signaled = new AtomicBoolean(false);

    void signal() {
      if (signaled.compareAndSet(false, true)) {
        latch.countDown();
      }
    }

    void await() {
      try {
        latch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static final class CloseAwareInputStream extends InputStream {
    private final InputStream delegate;
    private final CloseSignal closeSignal;

    private CloseAwareInputStream(InputStream delegate, CloseSignal closeSignal) {
      this.delegate = delegate;
      this.closeSignal = closeSignal;
    }

    @Override
    public int read() throws IOException {
      int value = delegate.read();
      if (value == -1) {
        closeSignal.signal();
      }
      return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int result = delegate.read(b, off, len);
      if (result == -1) {
        closeSignal.signal();
      }
      return result;
    }

    @Override
    public void close() {
      closeSignal.signal();
    }
  }
}
