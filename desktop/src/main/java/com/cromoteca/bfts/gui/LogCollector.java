package com.cromoteca.bfts.gui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Collects log lines generated while the desktop GUI is running.
 * Lines are kept in memory with a configurable maximum to limit usage.
 */
public class LogCollector {
  private static final String STREAM_OUT = "stdout";
  private static final String STREAM_ERR = "stderr";

  private final Deque<LogEntry> entries = new ArrayDeque<>();
  private final int maxEntries;
  private long nextId = 1;

  public LogCollector(int maxEntries) {
    this.maxEntries = Math.max(10, maxEntries);
  }

  public OutputStream createStdoutStream() {
    return new CollectingOutputStream(STREAM_OUT);
  }

  public OutputStream createStderrStream() {
    return new CollectingOutputStream(STREAM_ERR);
  }

  public synchronized ObjectNode toJson(ObjectMapper mapper, long afterId) {
    ObjectNode result = mapper.createObjectNode();
    ArrayNode array = mapper.createArrayNode();

    for (LogEntry entry : entries) {
      if (entry.id > afterId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", entry.id);
        node.put("timestamp", entry.timestamp);
        node.put("stream", entry.stream);
        node.put("message", entry.line);
        array.add(node);
      }
    }

    result.put("available", true);
    result.put("lastId", nextId - 1);
    result.set("entries", array);
    return result;
  }

  private synchronized void append(String stream, String line) {
    LogEntry entry = new LogEntry(nextId++, stream, line, System.currentTimeMillis());
    entries.addLast(entry);

    while (entries.size() > maxEntries) {
      entries.removeFirst();
    }
  }

  private class CollectingOutputStream extends OutputStream {
    private final String stream;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private CollectingOutputStream(String stream) {
      this.stream = stream;
    }

    @Override
    public synchronized void write(int b) throws IOException {
      if (b == '\r') {
        return;
      }

      if (b == '\n') {
        flushBuffer();
      } else {
        buffer.write(b);
      }
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
      for (int i = off; i < off + len; i++) {
        write(b[i]);
      }
    }

    @Override
    public synchronized void flush() throws IOException {
      if (buffer.size() > 0) {
        flushBuffer();
      }
    }

    @Override
    public void close() throws IOException {
      flush();
    }

    private void flushBuffer() {
      if (buffer.size() == 0) {
        append(stream, "");
      } else {
        String line = buffer.toString(StandardCharsets.UTF_8);
        append(stream, line);
        buffer.reset();
      }
    }
  }

  private static class LogEntry {
    private final long id;
    private final String stream;
    private final String line;
    private final long timestamp;

    private LogEntry(long id, String stream, String line, long timestamp) {
      this.id = id;
      this.stream = stream;
      this.line = line;
      this.timestamp = timestamp;
    }
  }
}
