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
package com.cromoteca.bfts.cryptography;

import com.cromoteca.bfts.model.StorageConfiguration;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class CryptographerTest {

  public static final String PASSWORD = "myNicePassword";
  private Cryptographer cryptographer;
  private String filePath = "/home/user/path/to/file.txt";

  @Before
  public void before() throws GeneralSecurityException {
    byte[] salt = new byte[StorageConfiguration.SALT_LENGTH];
    SecureRandom random = new SecureRandom();
    random.nextBytes(salt);
    cryptographer = new Cryptographer(salt, PASSWORD.toCharArray());
  }

  @Test
  public void testSymmetricEncryption() throws GeneralSecurityException {
    String enc1 = cryptographer.encrypt(filePath);
    assertNotNull(enc1);
    assertNotEquals(enc1, filePath);
    String dec1 = cryptographer.decrypt(enc1);
    assertNotNull(dec1);
    assertEquals(dec1, filePath);

    String enc2 = cryptographer.encrypt(filePath);
    assertNotNull(enc2);
    assertNotEquals(enc2, filePath);
    assertEquals(enc2, enc1);
    String dec2 = cryptographer.decrypt(enc2);
    assertNotNull(dec2);
    assertEquals(dec2, filePath);
  }

  @Test
  public void testAsymmetricEncryption() throws GeneralSecurityException {
    byte[][] keyPair = cryptographer.generateKeyPair();
    byte[] message = filePath.getBytes();
    byte[] encryptedMessage = cryptographer.encrypt(message, keyPair[0]);
    assertNotNull(encryptedMessage);
    assertThat(encryptedMessage, not(equalTo(message)));
    byte[] decryptedMessage = Cryptographer.decrypt(encryptedMessage, keyPair[1]);
    assertNotNull(decryptedMessage);
    assertArrayEquals(message, decryptedMessage);
  }
}
