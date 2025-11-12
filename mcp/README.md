# MCP Module

This module packages the `bfts-mcp` command-line server that exposes the backup metadata via the Model Context Protocol (MCP) using the `friendly_file_view`. It is a **stdio** server: it is not meant to be started manually and left running. Instead, your MCP client launches the process, communicates over stdin/stdout, and terminates it when the session ends.

## Prerequisites

1. Build the project with Java 17 or newer (the MCP SDK jars are compiled using Java records):
   ```bash
   mvn -pl mcp -am -DskipTests package
   ```
2. Ensure the desktop application has already connected at least one storage (local path or remote endpoint). The MCP server reads the same preference node (`/com/cromoteca/bfts`) as the desktop app and reuses those connections, including transmission and file-encryption passwords.

## Using with an MCP Client

1. Build the module (see above). The build produces `target/bfts-mcp-1.0-SNAPSHOT.jar` plus a `target/lib/` folder containing the runtime dependencies. The manifest of the jar already points to `lib/…`, so keep the directory structure when deploying.
2. Register the server with your MCP client configuration. The exact syntax depends on the client, but every client ultimately needs a command to execute. For example:

   ```json
   {
     "servers": {
       "bfts": {
         "command": "java",
         "args": [
           "-jar",
           "/path/to/bfts/mcp/target/bfts-mcp-1.0-SNAPSHOT.jar"
         ]
       }
     }
   }
   ```

   When the client starts an MCP session, it spawns this command and communicates via stdio until the session ends.

3. In the session:
   - Invoke `list-connected-storages` (no arguments) to retrieve the storage names currently connected by the desktop client.
   - Use one of those names (when more than one exists) in the `files-view-query` tool call, passing arguments in the JSON object you send via MCP. Example request body (client-specific envelope omitted for brevity):

```json
{
  "tool": "files-view-query",
  "arguments": {
    "columns": ["name", "size", "lastModifiedTime"],
    "where": "\"source.client\" = 'my-client'",
    "orderBy": "lastModifiedTime DESC",
    "limit": 25
  }
}
```

### `files-view-query` Arguments

| Field    | Type     | Description                                                             |
|----------|----------|-------------------------------------------------------------------------|
| columns  | string[] | Optional projection. Use `*` or omit to select the default column set.  |
| where    | string   | Optional `WHERE` clause (without the `WHERE` keyword).                  |
| orderBy  | string   | Optional `ORDER BY` clause (e.g. `"lastModifiedTime DESC"`).            |
| limit    | integer  | Max rows to return (default 50, capped at 500).                         |
| storage  | string   | Optional storage name when multiple connected storages are configured.  |

### Tool Responses

- `list-connected-storages` returns `{"storages": ["first", "second", ...]}`.
- `files-view-query` returns an object with:
  - `storage` – the storage that was queried
  - `columns` – the column names in the result set
  - `rowCount`
  - `rows` – array of rows (each row is an array of values)
- SQL syntax: all queries run against SQLite; ensure generated clauses follow SQLite rules.

## Troubleshooting

- If the server exits immediately, ensure there is at least one connected storage in the desktop client and that any required passwords have been set.
- If multiple storages are configured, pass the desired storage name with the `storage` argument.
