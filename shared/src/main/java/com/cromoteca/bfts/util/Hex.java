package com.cromoteca.bfts.util;

/**
 * Copied from javax.xml.bind.DatatypeConverterImpl
 */
public class Hex {
  private static final char[] hexCode = "0123456789abcdef".toCharArray();

  private static int hexToBin(char ch) {
    if ('0' <= ch && ch <= '9') {
      return ch - '0';
    }
    if ('A' <= ch && ch <= 'F') {
      return ch - 'A' + 10;
    }
    if ('a' <= ch && ch <= 'f') {
      return ch - 'a' + 10;
    }
    return -1;
  }

  public static byte[] parseHexBinary(String s) {
    if (s == null) {
      return null;
    }

    final int len = s.length();

    // "111" is not a valid hex encoding.
    if (len % 2 != 0) {
      throw new IllegalArgumentException("hexBinary needs to be even-length: " + s);
    }

    byte[] out = new byte[len / 2];

    for (int i = 0; i < len; i += 2) {
      int h = hexToBin(s.charAt(i));
      int l = hexToBin(s.charAt(i + 1));
      if (h == -1 || l == -1) {
        throw new IllegalArgumentException("contains illegal character for hexBinary: " + s);
      }

      out[i / 2] = (byte) (h * 16 + l);
    }

    return out;
  }

  public static String printHexBinary(byte[] data) {
    if (data == null) {
      return null;
    }

    return printHexBinary(data, 0, data.length);
  }

  public static String printHexBinary(byte[] data, int offset, int length) {
    if (data == null) {
      return null;
    }

    StringBuilder r = new StringBuilder(data.length * 2);
    byte b;

    for (int i = offset; i < offset + length; i++) {
      b = data[i];
      r.append(hexCode[(b >> 4) & 0xF]);
      r.append(hexCode[(b & 0xF)]);
    }
    return r.toString();
  }
}
