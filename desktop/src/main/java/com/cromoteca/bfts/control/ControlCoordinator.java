package com.cromoteca.bfts.control;

import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates ownership of the control port and forwards commands to the
 * appropriate JVM instance.
 */
public class ControlCoordinator implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(ControlCoordinator.class);
  private final ControlOperations operations;
  private int port;
  private HttpControlServer server;
  private HttpControlClient client;
  private boolean localOwner;

  public ControlCoordinator(ControlOperations operations, int port) {
    this.operations = operations;
    this.port = port;
    bindOrConnect(port);
  }

  public synchronized void setPort(int port) {
    if (this.port == port) {
      return;
    }
    shutdownServer();
    this.port = port;
    bindOrConnect(port);
  }

  public synchronized int getPort() {
    return port;
  }

  public synchronized boolean isLocalOwner() {
    return localOwner;
  }

  public void startAll() {
    if (isLocalOwner()) {
      operations.startAllLocal();
    } else {
      client.startAll();
    }
  }

  public void start(String name) {
    if (isLocalOwner()) {
      operations.startLocal(name);
    } else {
      client.start(name);
    }
  }

  public void stopAll() {
    if (isLocalOwner()) {
      operations.stopAllLocal();
    } else {
      client.stopAll();
    }
  }

  public void stop(String name) {
    if (isLocalOwner()) {
      operations.stopLocal(name);
    } else {
      client.stop(name);
    }
  }

  public void fast() {
    if (isLocalOwner()) {
      operations.fastLocal();
    } else {
      client.fast();
    }
  }

  public void nice() {
    if (isLocalOwner()) {
      operations.niceLocal();
    } else {
      client.nice();
    }
  }

  public ControlStatus status() {
    if (isLocalOwner()) {
      ControlStatus status = operations.status(port);
      if (status == null) {
        return new ControlStatus(true, port, Collections.emptyMap());
      }
      return status;
    } else {
      ControlStatus status = client.status();
      if (status == null) {
        return new ControlStatus(false, port, Collections.emptyMap());
      }
      return status;
    }
  }

  private synchronized void bindOrConnect(int desiredPort) {
    try {
      server = new HttpControlServer(desiredPort, operations);
      server.start();
      localOwner = true;
      client = new HttpControlClient(desiredPort);
      log.info("Acquired control port {}", desiredPort);
    } catch (BindException ex) {
      localOwner = false;
      server = null;
      client = new HttpControlClient(desiredPort);
      if (client.ping()) {
        log.info("Control port {} already owned by another process", desiredPort);
      } else {
        log.warn("Control port {} is busy but no owner responded", desiredPort);
      }
    } catch (IOException ex) {
      localOwner = false;
      server = null;
      client = new HttpControlClient(desiredPort);
      log.warn("Unable to start control server on port {}: {}", desiredPort, ex.getMessage());
    }
  }

  private synchronized void shutdownServer() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  @Override
  public synchronized void close() {
    shutdownServer();
  }
}
