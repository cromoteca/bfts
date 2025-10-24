package com.cromoteca.bfts.gui;

import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Holds the active {@link LogCollector} when the desktop GUI is running and
 * provides helpers to retrieve collected log lines.
 */
public final class GuiLogManager {
  private static final AtomicReference<LogCollector> COLLECTOR
      = new AtomicReference<>();

  private GuiLogManager() {
    // utility class
  }

  public static void register(LogCollector collector) {
    COLLECTOR.set(collector);
  }

  public static void unregister(LogCollector collector) {
    COLLECTOR.compareAndSet(collector, null);
  }

  public static ObjectNode collect(ObjectMapper mapper, long afterId) {
    LogCollector collector = COLLECTOR.get();
    if (collector == null) {
      ObjectNode node = mapper.createObjectNode();
      node.put("available", false);
      node.put("lastId", afterId);
      node.set("entries", mapper.createArrayNode());
      return node;
    }

    return collector.toJson(mapper, afterId);
  }
}
