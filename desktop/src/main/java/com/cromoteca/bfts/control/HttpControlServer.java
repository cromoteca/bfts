package com.cromoteca.bfts.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local HTTP control endpoint used to coordinate multiple JVM instances.
 */
public class HttpControlServer {
  private static final Logger log = LoggerFactory.getLogger(HttpControlServer.class);
  private final int port;
  private final ControlOperations operations;
  private final HttpServer server;
  private final ExecutorService executor;
  private final ObjectMapper mapper = new ObjectMapper();

  public HttpControlServer(int port, ControlOperations operations) throws IOException {
    this.port = port;
    this.operations = operations;
    InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
    server = HttpServer.create(address, 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext("/status", new StatusHandler());
    server.createContext("/start", new CommandHandler(() -> {
      String name = CURRENT_NAME.get();
      if (name == null) {
        operations.startAllLocal();
      } else {
        operations.startLocal(name);
      }
      return "started";
    }));
    server.createContext("/stop", new CommandHandler(() -> {
      String name = CURRENT_NAME.get();
      if (name == null) {
        operations.stopAllLocal();
      } else {
        operations.stopLocal(name);
      }
      return "stopped";
    }));
    server.createContext("/fast", new CommandHandler(() -> {
      operations.fastLocal();
      return "fast";
    }));
    server.createContext("/nice", new CommandHandler(() -> {
      operations.niceLocal();
      return "nice";
    }));
  }

  public void start() {
    server.start();
    log.info("Control server listening on 127.0.0.1:{}", port);
  }

  public void stop(int delaySeconds) {
    server.stop(delaySeconds);
    executor.shutdownNow();
    log.info("Control server on port {} stopped", port);
  }

  private static final ThreadLocal<String> CURRENT_NAME = new ThreadLocal<>();

  private abstract class BaseHandler implements HttpHandler {
    @Override
    public final void handle(HttpExchange exchange) throws IOException {
      try {
        if (!isLoopback(exchange)) {
          sendResponse(exchange, 403, "Forbidden");
          return;
        }
        Map<String, String> params = parseQueryParams(exchange.getRequestURI());
        CURRENT_NAME.set(params.get("name"));
        handleRequest(exchange, params);
      } finally {
        CURRENT_NAME.remove();
      }
    }

    protected abstract void handleRequest(HttpExchange exchange,
        Map<String, String> params) throws IOException;
  }

  private class StatusHandler extends BaseHandler {
    @Override
    protected void handleRequest(HttpExchange exchange,
        Map<String, String> params) throws IOException {
      ControlStatus status = operations.status(port);
      if (status == null) {
        status = new ControlStatus(true, port, Collections.emptyMap());
      }
      String body = mapper.writeValueAsString(buildStatusNode(status));
      sendResponse(exchange, 200, body, "application/json");
    }
  }

  private class CommandHandler extends BaseHandler {
    private final Supplier<String> command;

    private CommandHandler(Supplier<String> command) {
      this.command = command;
    }

    @Override
    protected void handleRequest(HttpExchange exchange,
        Map<String, String> params) throws IOException {
      try {
        String result = command.get();
        sendResponse(exchange, 200, "{\"status\":\"" + result + "\"}", "application/json");
      } catch (Exception ex) {
        log.error("Control command failed", ex);
        sendResponse(exchange, 500, "{\"error\":\"" + safeMessage(ex) + "\"}",
            "application/json");
      }
    }
  }

  private static boolean isLoopback(HttpExchange exchange) {
    InetAddress address = exchange.getRemoteAddress().getAddress();
    return address != null && address.isLoopbackAddress();
  }

  private static Map<String, String> parseQueryParams(URI uri) {
    String query = uri.getRawQuery();
    if (query == null || query.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> params = new HashMap<>();
    for (String pair : query.split("&")) {
      int idx = pair.indexOf('=');
      if (idx == -1) {
        params.put(decode(pair), "");
      } else {
        String key = decode(pair.substring(0, idx));
        String value = decode(pair.substring(idx + 1));
        params.put(key, value);
      }
    }
    return params;
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private Map<String, Object> buildStatusNode(ControlStatus status) {
    Map<String, Object> map = new HashMap<>();
    map.put("owner", status.isOwner());
    map.put("port", status.getPort());
    map.put("storages", status.getStorageStates().entrySet().stream()
        .map(entry -> {
          Map<String, Object> node = new HashMap<>();
          node.put("name", entry.getKey());
          node.put("running", entry.getValue());
          return node;
        })
        .toList());
    return map;
  }

  private static void sendResponse(HttpExchange exchange, int statusCode, String body)
      throws IOException {
    sendResponse(exchange, statusCode, body, "text/plain");
  }

  private static void sendResponse(HttpExchange exchange, int statusCode, String body,
      String contentType) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static String safeMessage(Exception ex) {
    String message = ex.getMessage();
    return message == null ? ex.getClass().getSimpleName() : message.replace("\"", "'");
  }
}
