package com.cromoteca.bfts.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client that forwards control commands to the JVM owning the control port.
 */
public class HttpControlClient {
  private static final Logger log = LoggerFactory.getLogger(HttpControlClient.class);
  private static final ObjectMapper mapper = new ObjectMapper();
  private final int port;

  public HttpControlClient(int port) {
    this.port = port;
  }

  public void startAll() {
    invoke("/start");
  }

  public void start(String name) {
    invoke("/start?name=" + encode(name));
  }

  public void stopAll() {
    invoke("/stop");
  }

  public void stop(String name) {
    invoke("/stop?name=" + encode(name));
  }

  public void fast() {
    invoke("/fast");
  }

  public void nice() {
    invoke("/nice");
  }

  public boolean ping() {
    return get("/status") != null;
  }

  public ControlStatus status() {
    String body = get("/status");
    if (body == null) {
      return null;
    }
    try {
      JsonNode node = mapper.readTree(body);
      boolean owner = node.path("owner").asBoolean(false);
      int reportedPort = node.path("port").asInt(port);
      Map<String, Boolean> storageStates = new HashMap<>();
      JsonNode storages = node.path("storages");
      if (storages.isArray()) {
        for (JsonNode item : storages) {
          String name = item.path("name").asText(null);
          if (name != null) {
            boolean running = item.path("running").asBoolean(false);
            storageStates.put(name, running);
          }
        }
      }
      return new ControlStatus(owner, reportedPort, storageStates);
    } catch (IOException ex) {
      log.warn("Failed to parse control status: {}", ex.getMessage());
      return null;
    }
  }

  private boolean invoke(String path) {
    return get(path) != null;
  }

  private String get(String path) {
    HttpURLConnection conn = null;
    try {
      URL url = new URL("http://127.0.0.1:" + port + path);
      conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(2000);
      conn.setReadTimeout(5000);
      conn.setRequestMethod("GET");
      conn.setDoInput(true);
      int code = conn.getResponseCode();
      String body;
      try (InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream()) {
        body = readBody(is);
      }

      if (code >= 200 && code < 300) {
        return body;
      } else {
        log.warn("Control request {} responded with status {}", path, code);
      }
    } catch (IOException ex) {
      log.warn("Control request {} failed: {}", path, ex.getMessage());
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
    return null;
  }

  private static String readBody(InputStream is) throws IOException {
    if (is == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    byte[] buffer = new byte[512];
    int read;
    while ((read = is.read(buffer)) != -1) {
      sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
    }
    return sb.toString();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
