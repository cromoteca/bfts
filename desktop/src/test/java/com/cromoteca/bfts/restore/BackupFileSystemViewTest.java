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
package com.cromoteca.bfts.restore;

import com.cromoteca.bfts.model.File;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.restore.BackupFileSystemView.BackupFtpFile;
import com.cromoteca.bfts.storage.Storage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class BackupFileSystemViewTest {

  @Test
  public void testBackupFtpFileConstructor() throws Exception {
    String clientName = "client";

    Storage storage = mock(Storage.class, withSettings().verboseLogging());
    Map<String, Storage> storages = new HashMap<>();
    storages.put(clientName, storage);
    List<BackupFtpFile> children;

    BackupFileSystemView view
        = new BackupFileSystemView(clientName, storages, false);
    BackupFtpFile file = view.getFile("");
    assertEquals("", file.getName());
    assertEquals("/", file.getAbsolutePath());
    children = file.listFiles();
    assertEquals(1, children.size());

    BackupFtpFile timeLevel = children.get(0);
    children = timeLevel.listFiles();
    assertEquals(1, children.size());

    Source source = mock(Source.class);
    when(source.getId()).thenReturn(1);
    when(source.getName()).thenReturn("documents");
    when(storage.selectSources(clientName)).thenReturn(Arrays.asList(source));

    BackupFtpFile storageLevel = children.get(0);
    children = storageLevel.listFiles();
    assertEquals(1, children.size());

    long time = BackupFileSystemView.FORMATTER.parse(timeLevel.getName())
        .getTime();
    File dir1 = new File("dir1", "");
    when(storage.getFiles(1, time)).thenReturn(Arrays.asList(dir1));
    BackupFtpFile sourceLevel = children.get(0);
    children = sourceLevel.listFiles();
    assertEquals(1, children.size());

    List<File> ls = Arrays.asList(new File("dir2", "dir1"), new File("file2",
        "dir1", 123, 456));
    when(storage.getFiles(1, time)).thenReturn(ls);
    BackupFtpFile dirLevel = children.get(0);
    children = dirLevel.listFiles();
    assertEquals(2, children.size());
  }
}
