package com.cromoteca.bfts.mcp;

import com.cromoteca.bfts.client.Configuration;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.storage.EncryptionType;
import com.cromoteca.bfts.storage.LocalStorage;
import com.cromoteca.bfts.util.FilePath;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class McpFilesViewQueryToolIntegrationTest {

  private Path tempDir;
  private LocalStorage storage;
  private String storageName;

  @Before
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("mcp-files-view-test");
    storage = LocalStorage.init(FilePath.get(tempDir.toString()), false, new StorageConfiguration());

    storageName = "mcp-integration";
    Configuration configuration = Configuration.load();
    configuration.setConnectedStoragePath(storageName, tempDir.toString());
    configuration.setConnectedStorageEncryptionType(storageName, EncryptionType.NONE);
  }

  @After
  public void tearDown() throws Exception {
    if (storage != null) {
      storage.close();
    }
    if (tempDir != null) {
      Files.walk(tempDir)
          .sorted(Comparator.reverseOrder())
          .forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
          });
    }
    Preferences prefs = Preferences.userRoot().node("/com/cromoteca/bfts/connectedstorages");
    try {
      if (prefs.nodeExists(storageName)) {
        prefs.node(storageName).removeNode();
        prefs.flush();
      }
    } catch (BackingStoreException | SecurityException ignored) {
    }
  }

  @Test
  public void toolReturnsStorageErrorMessageWithColumnName() throws Exception {
    FilesViewQueryService queryService =
        new ConnectedStorageFilesViewQueryService(Configuration.load());

    String expectedMessage = null;
    try {
      storage.queryFilesView(List.of("nonexisting"), null, null, 1);
      fail("Query with invalid column should fail");
    } catch (com.cromoteca.bfts.storage.StorageException ex) {
      expectedMessage = ex.getMessage();
    }

    assertNotNull("Expected storage exception message", expectedMessage);

    Method handle =
        McpServerLauncher.class.getDeclaredMethod(
            "handleFilesViewQuery", FilesViewQueryService.class, Map.class);
    handle.setAccessible(true);

    Map<String, Object> params = new HashMap<>();
    params.put("columns", List.of("nonexisting"));
    params.put("limit", 1);
    params.put("storage", storageName);

    CallToolResult result =
        (CallToolResult) handle.invoke(null, queryService, params);

    assertTrue(result.isError());
    assertEquals(1, result.content().size());
    TextContent messageContent = (TextContent) result.content().get(0);
    String actualMessage = messageContent.text();
    assertNotNull(actualMessage);
    assertTrue(actualMessage.contains("nonexisting"));
  }

  @Test
  public void toolWithValidColumnsLeadsToInvocationTargetExceptionMessage() throws Exception {
    FilesViewQueryService failingService =
        new FilesViewQueryService() {
          @Override
          public FilesViewQueryResult execute(FilesViewQueryRequest request) {
            assertEquals(List.of("name"), request.getColumns());
            throw new RuntimeException(
                new InvocationTargetException(new UnsupportedOperationException()));
          }
        };

    Method handle =
        McpServerLauncher.class.getDeclaredMethod(
            "handleFilesViewQuery", FilesViewQueryService.class, Map.class);
    handle.setAccessible(true);

    Map<String, Object> params = new HashMap<>();
    params.put("columns", List.of("name"));

    CallToolResult result =
        (CallToolResult) handle.invoke(null, failingService, params);

    assertTrue(result.isError());
    assertEquals(1, result.content().size());
    TextContent messageContent = (TextContent) result.content().get(0);
    assertTrue(messageContent.text().contains("InvocationTargetException"));
  }
}
