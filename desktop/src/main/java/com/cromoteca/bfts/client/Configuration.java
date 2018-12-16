/*
 * Copyright (C) 2014-2019 Luciano Vernaschi (luciano at cromoteca.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.cromoteca.bfts.client;

import com.cromoteca.bfts.storage.EncryptionType;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Stores client configuration values using
 * <code>java.util.prefs.Preferences</code>.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Configuration {
  private static final String LONG_OPERATION_DURATION
      = "longOperationDuration";
  private static final int DEFAULT_LONG_OPERATION_DURATION = 30;
  private final Preferences p;

  /**
   * Creates a new configuration.
   *
   * @param p the preferences object used to store configuration values
   */
  public Configuration(Preferences p) {
    this.p = p;
  }

  /**
   * Name of the client
   */
  public String getClientName() {
    return p.get("name", null);
  }

  public void setClientName(String name) {
    p.put("name", name);
  }

  @Override
  public String toString() {
    return getClientName();
  }

  /**
   * Remove all configuration
   */
  public void remove() {
    try {
      p.removeNode();
    } catch (BackingStoreException ex) {
      throw new ConfigurationException(ex);
    }
  }

  /**
   * Returns a suggested duration for long operations like uploads and
   * downloads.
   *
   * @return the duration in seconds
   */
  public int getLongOperationDuration() {
    return p.getInt(LONG_OPERATION_DURATION, DEFAULT_LONG_OPERATION_DURATION);
  }

  private Preferences localStorageNode() {
    return p.node("localstorages");
  }

  public void setLocalStoragePath(String name, String path) {
    localStorageNode().node(name).put("path", path);
  }

  /**
   * Returns the path of a local storage.
   *
   * @param name local storage name
   */
  public String getLocalStoragePath(String name) {
    return localStorageNode().node(name).get("path", null);
  }

  public void setLocalStoragePort(String name, int port) {
    localStorageNode().node(name).putInt("port", port);
  }

  /**
   * Returns the HTTP port used to publish a local storage for remote clients.
   *
   * @param name local storage name
   * @return the port number, or 0 if the storage is not published
   */
  public int getLocalStoragePort(String name) {
    return localStorageNode().node(name).getInt("port", 0);
  }

  /**
   * Returns the names of all local storages.
   */
  public String[] getLocalStorages() {
    try {
      return localStorageNode().childrenNames();
    } catch (BackingStoreException ex) {
      throw new ConfigurationException(ex);
    }
  }

  private Preferences connectedStorageNode() {
    return p.node("connectedstorages");
  }

  public void setConnectedStoragePath(String name, String path) {
    connectedStorageNode().node(name).put("path", path);
  }

  /**
   * Returns the path of a connected storage.
   *
   * @param name connected storage name
   * @return the path, if any, in the form /path/to/directory for a local
   *         connection, or example.com:12345 for a remote one
   */
  public String getConnectedStoragePath(String name) {
    return connectedStorageNode().node(name).get("path", null);
  }

  public void setConnectedStorageEncryptionType(String name, EncryptionType type) {
    connectedStorageNode().node(name).put("encryption", type.toString());
  }

  /**
   * Returns true when data is backed up with encryption in a connected storage.
   *
   * @param name connected storage name
   */
  public EncryptionType getConnectedStorageEncryptionType(String name) {
    return EncryptionType.fromString(connectedStorageNode().node(name)
        .get("encryption", null));
  }

  /**
   * Returns the names of all connected storages.
   */
  public String[] getConnectedStorages() {
    try {
      return connectedStorageNode().childrenNames();
    } catch (BackingStoreException ex) {
      throw new ConfigurationException(ex);
    }
  }
}
