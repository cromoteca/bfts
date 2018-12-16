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
package com.cromoteca.bfts.mappers;

import com.cromoteca.bfts.model.Chunk;
import com.cromoteca.bfts.model.StorageConfiguration;
import com.cromoteca.bfts.storage.FileStatus;
import com.cromoteca.bfts.util.Hex;
import java.io.InputStream;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class StorageMapperTest {
  private static SqlSessionFactory factory;
  private SqlSession session;
  private StorageMapper mapper;

  @BeforeClass
  public static void setUpClass() throws Exception {
    String dbConfig = "/com/cromoteca/bfts/mybatis.config.xml";

    try (InputStream stream
        = StorageMapperTest.class.getResourceAsStream(dbConfig)) {
      factory = new SqlSessionFactoryBuilder().build(stream, "memory",
          FileStatus.getAsProperties());
    }
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    factory = null;
  }

  @Before
  public void setUp() throws Exception {
    session = factory.openSession();

    InitMapper im = session.getMapper(InitMapper.class);

    im.dropServerTable();
    im.createServerTable();

    im.dropSourceTable();
    im.createSourceTable();

    im.dropFileTable();
    im.createFileTable();
    im.createFilePrimaryIndex();
    im.createFileSecondaryIndex();
    im.createFileTrigger();

    im.dropHashTable();
    im.createHashTable();
    im.createHashChunkIndex();
    im.createHashTrigger();

    im.addServer(new StorageConfiguration());

    mapper = session.getMapper(StorageMapper.class);
  }

  @After
  public void tearDown() throws Exception {
    mapper = null;
    session.commit();
    session.close();
    session = null;
  }

  @Test
  public void testGetUploadedChunks() {
    long instant = System.currentTimeMillis();
    byte[][] hashes = new byte[][] {
      Hex.parseHexBinary("000123"),
      Hex.parseHexBinary("0078AA"),
      Hex.parseHexBinary("010000"),
      Hex.parseHexBinary("013456"),
      Hex.parseHexBinary("01ABCD"),
      Hex.parseHexBinary("025555"),
      Hex.parseHexBinary("02AAAA"),
      Hex.parseHexBinary("FE2222"),
      Hex.parseHexBinary("FE8888"),
      Hex.parseHexBinary("FEBBBB"),
      Hex.parseHexBinary("FF0000"),
      Hex.parseHexBinary("FF7777"),
      Hex.parseHexBinary("FFFFFF")
    };

    byte[] main = Hex.parseHexBinary("cafedeca");

    for (int i = 0; i < hashes.length; i++) {
      byte[] hash = hashes[i];
      mapper.addChunk(main, i, 100, hash);
      mapper.markUploadedChunk(hash, instant);
    }

    byte[] b00 = new byte[] { 0x00 };
    byte[] b01 = new byte[] { 0x01 };
    byte[] b02 = new byte[] { 0x02 };
    byte[] bFD = new byte[] { (byte) 0xfd };
    byte[] bFE = new byte[] { (byte) 0xfe };
    byte[] bFF = new byte[] { (byte) 0xff };

    byte[][] up00 = getChunks(mapper, b00, b01);
    assertEquals(2, up00.length);
    assertArrayEquals(hashes[0], up00[0]);
    assertArrayEquals(hashes[1], up00[1]);

    byte[][] up01 = getChunks(mapper, b01, b02);
    assertEquals(3, up01.length);
    assertArrayEquals(hashes[2], up01[0]);
    assertArrayEquals(hashes[3], up01[1]);
    assertArrayEquals(hashes[4], up01[2]);

    byte[][] up02FD = getChunks(mapper, b02, bFD);
    assertEquals(2, up02FD.length);
    assertArrayEquals(hashes[5], up02FD[0]);
    assertArrayEquals(hashes[6], up02FD[1]);

    byte[][] upFD = getChunks(mapper, bFD, bFE);
    assertEquals(0, upFD.length);

    byte[][] upFE = getChunks(mapper, bFE, bFF);
    assertEquals(3, upFE.length);
    assertArrayEquals(hashes[7], upFE[0]);
    assertArrayEquals(hashes[8], upFE[1]);
    assertArrayEquals(hashes[9], upFE[2]);

    byte[][] upFF = getChunks(mapper, bFF, null);
    assertEquals(3, upFF.length);
    assertArrayEquals(hashes[10], upFF[0]);
    assertArrayEquals(hashes[11], upFF[1]);
    assertArrayEquals(hashes[12], upFF[2]);
  }

  private byte[][] getChunks(StorageMapper mapper, byte[] from, byte[] to) {
    return mapper.getUploadedChunks(from, to)
        .stream()
        .map(Chunk::getHash)
        .sorted((b1, b2) -> Hex.printHexBinary(b1).compareTo(Hex.printHexBinary(b2)))
        .toArray(byte[][]::new);
  }
}
