package com.cromoteca.bfts.control;

import java.util.Collections;
import java.util.Map;

/**
 * Snapshot of the control state, including scheduler status for storages.
 */
public class ControlStatus {
  private final boolean owner;
  private final int port;
  private final Map<String, Boolean> storageStates;

  public ControlStatus(boolean owner, int port, Map<String, Boolean> storageStates) {
    this.owner = owner;
    this.port = port;
    this.storageStates = storageStates == null
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(storageStates);
  }

  public boolean isOwner() {
    return owner;
  }

  public int getPort() {
    return port;
  }

  public Map<String, Boolean> getStorageStates() {
    return storageStates;
  }
}
