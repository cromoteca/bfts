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
package com.cromoteca.bfts.model;

import java.security.SecureRandom;

/**
 * Configuration saved in the database (shared between all clients)
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class StorageConfiguration {
  public static final int SALT_LENGTH = 128;

  private byte[] id = new byte[32];
  private byte[] salt = new byte[SALT_LENGTH];
  private int databaseBackupIntervalMinutes = 240;
  private int databaseBackupsToKeep = 12;
  private byte[] encryptedPublicKey;

  {
    SecureRandom random = new SecureRandom();
    random.nextBytes(id);
    random.nextBytes(salt);
  }

  /**
   * Server id
   */
  public byte[] getId() {
    return id;
  }

  public void setId(byte[] id) {
    this.id = id;
  }

  /**
   * Salt for encryption
   */
  public byte[] getSalt() {
    return salt;
  }

  public void setSalt(byte[] salt) {
    this.salt = salt;
  }

  /**
   * Minutes between two database backups
   */
  public int getDatabaseBackupIntervalMinutes() {
    return databaseBackupIntervalMinutes;
  }

  public void setDatabaseBackupIntervalMinutes(int databaseBackupIntervalMinutes) {
    this.databaseBackupIntervalMinutes = databaseBackupIntervalMinutes;
  }

  /**
   * Number of copies of the database to keep
   */
  public int getDatabaseBackupsToKeep() {
    return databaseBackupsToKeep;
  }

  public void setDatabaseBackupsToKeep(int databaseBackupsToKeep) {
    this.databaseBackupsToKeep = databaseBackupsToKeep;
  }

  /**
   * Public key used to encrypt http communication. It is itself encrypted so
   * one need the password to send data to the remote storage.
   */
  public byte[] getEncryptedPublicKey() {
    return encryptedPublicKey;
  }

  public void setEncryptedPublicKey(byte[] encryptedPublicKey) {
    this.encryptedPublicKey = encryptedPublicKey;
  }
}
