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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts and decrypts data.&nbsp;Java Cryptographer Extension (JCE) Unlimited
 * Strength is required.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Cryptographer {
  // PBKDF2WithHmacSHA512 requires Android 26
  public static final String PDKDF_ALGORITHM = "PBKDF2WithHmacSHA512";
  public static final int ITERATION_COUNT = 12288;
  public static final int IV_SIZE = 16;
  /**
   * Key size.
   */
  public static final int KEY_SIZE = 32;
  /**
   * Used transformation.
   */
  public static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
  /**
   * Used algorithm.
   */
  public static final String ALGORITHM
      = TRANSFORMATION.substring(0, TRANSFORMATION.indexOf('/'));
  /**
   * Transformation used for key pair operations.
   */
  public static final String KEY_PAIR_TRANSFORMATION
      = "RSA/ECB/PKCS1Padding";
  /**
   * Algorithm used for key pair operations.
   */
  public static final String KEY_PAIR_ALGORITHM = KEY_PAIR_TRANSFORMATION
      .substring(0, KEY_PAIR_TRANSFORMATION.indexOf('/'));
  private final SecretKey secretKey;

  static {
    // valid since Java SE 8u151
    Security.setProperty("crypto.policy", "unlimited");
  }

  /**
   * Creates an object whose key is based on passed salt and password.
   */
  public Cryptographer(byte[] salt, char[] password)
      throws GeneralSecurityException {
    PBEKeySpec keySpec = new PBEKeySpec(password, salt, ITERATION_COUNT,
        KEY_SIZE * 8);
    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(PDKDF_ALGORITHM);
    byte[] binaryKey = keyFactory.generateSecret(keySpec).getEncoded();
    secretKey = new SecretKeySpec(binaryKey, ALGORITHM);
  }

  /**
   * Creates an object passing the key directly.
   *
   * @param key the key, whose size must be 32 (see {@link #KEY_SIZE})
   */
  public Cryptographer(byte[] key) {
    if (key.length != KEY_SIZE) {
      throw new IllegalArgumentException("Key must be " + KEY_SIZE
          + " bytes long");
    }

    secretKey = new SecretKeySpec(key, ALGORITHM);
  }

  /**
   * Encrypts a string.
   */
  public String encrypt(String s) throws GeneralSecurityException {
    if (s != null) {
      byte[] data = s.getBytes(StandardCharsets.UTF_8);
      data = encrypt(data);
      s = Base64.getUrlEncoder().encodeToString(data);
    }

    return s;
  }

  /**
   * Decrypts a string.
   */
  public String decrypt(String s) throws GeneralSecurityException {
    if (s != null) {
      byte[] data = Base64.getUrlDecoder().decode(s);
      data = decrypt(data);
      s = new String(data, StandardCharsets.UTF_8);
    }

    return s;
  }

  /**
   * Encrypts a byte array.
   */
  public byte[] encrypt(byte[] b) throws GeneralSecurityException {
    byte[] result = null;

    if (b != null) {
      // a random IV shoud be used, but we need to get the same result when
      // encrypting the same message
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] iv = md.digest(b);

      IvParameterSpec ivspec = new IvParameterSpec(iv);
      Cipher c = Cipher.getInstance(TRANSFORMATION);
      c.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);

      byte[] enc = c.doFinal(b);
      result = new byte[IV_SIZE + enc.length];
      System.arraycopy(iv, 0, result, 0, IV_SIZE);
      System.arraycopy(enc, 0, result, IV_SIZE, enc.length);
    }

    return result;
  }

  /**
   * Decrypts a byte array.
   */
  public byte[] decrypt(byte[] b) throws GeneralSecurityException {
    byte[] result = null;

    if (b != null) {
      IvParameterSpec ivspec = new IvParameterSpec(b, 0, IV_SIZE);
      Cipher c = Cipher.getInstance(TRANSFORMATION);
      c.init(Cipher.DECRYPT_MODE, secretKey, ivspec);

      result = c.doFinal(b, IV_SIZE, b.length - IV_SIZE);
    }

    return result;
  }

  /**
   * Encrypts a byte array using a public key.
   *
   * @param data                      bytes to encrypt
   * @param encryptedEncodedPublicKey the public key, encrypted using the key
   *                                  used to create this object
   */
  public byte[] encrypt(byte[] data, byte[] encryptedEncodedPublicKey)
      throws GeneralSecurityException {
    byte[] encodedPublicKey = decrypt(encryptedEncodedPublicKey);
    X509EncodedKeySpec spec = new X509EncodedKeySpec(encodedPublicKey);
    KeyFactory keyFactory = KeyFactory.getInstance(KEY_PAIR_ALGORITHM);
    PublicKey publicKey = keyFactory.generatePublic(spec);
    Cipher cipher = Cipher.getInstance(KEY_PAIR_TRANSFORMATION);
    cipher.init(Cipher.ENCRYPT_MODE, publicKey);
    byte[] encryptedData = cipher.doFinal(data);
    return encryptedData;
  }

  /**
   * Decrypts a byte array using a private key
   *
   * @param encryptedData     bytes to decrypt
   * @param encodedPrivateKey the private key
   */
  public static byte[] decrypt(byte[] encryptedData, byte[] encodedPrivateKey)
      throws GeneralSecurityException {
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encodedPrivateKey);
    KeyFactory keyFactory = KeyFactory.getInstance(KEY_PAIR_ALGORITHM);
    PrivateKey privateKey = keyFactory.generatePrivate(spec);
    Cipher cipher = Cipher.getInstance(KEY_PAIR_TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, privateKey);
    byte[] decryptedData = cipher.doFinal(encryptedData);
    return decryptedData;
  }

  /**
   * Generates a new key pair.
   *
   * @return an array of byte arrays. The first one is the public key, encrypted
   *         using the key of this Cryptographer object, the second is the
   *         private key.
   */
  public byte[][] generateKeyPair() throws GeneralSecurityException {
    KeyPairGenerator generator
        = KeyPairGenerator.getInstance(KEY_PAIR_ALGORITHM);
    generator.initialize(4096);
    KeyPair keyPair = generator.generateKeyPair();
    PublicKey publicKey = keyPair.getPublic();
    PrivateKey privateKey = keyPair.getPrivate();
    byte[] encryptedEncodedPublicKey = encrypt(publicKey.getEncoded());
    byte[] encodedPrivateKey = privateKey.getEncoded();
    return new byte[][] { encryptedEncodedPublicKey, encodedPrivateKey };
  }
}
