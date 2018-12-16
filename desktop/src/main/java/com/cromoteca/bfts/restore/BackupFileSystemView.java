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

import com.cromoteca.bfts.model.Chunk;
import com.cromoteca.bfts.model.File;
import com.cromoteca.bfts.model.Pair;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.storage.Storage;
import com.cromoteca.bfts.util.Cache;
import com.cromoteca.bfts.util.Compression;
import com.cromoteca.bfts.util.Counter;
import com.cromoteca.bfts.util.Path;
import com.cromoteca.bfts.util.lambdas.IOSupplier;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A FileSystemView as needed by the Apache FtpServer
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class BackupFileSystemView implements FileSystemView {
  private static final Logger log
      = LoggerFactory.getLogger(BackupFileSystemView.class);
  public static final SimpleDateFormat FORMATTER = new SimpleDateFormat(
      "yyyy-MM-dd_HH-mm-ss");
  private final String clientName;
  private final Map<String, Storage> storages;
  private String workingDir = "/";

  public BackupFileSystemView(String clientName, Map<String, Storage> storages,
      boolean cacheEnabled) {
    if (cacheEnabled) {
      Cache cache = new Cache();
      storages = storages.entrySet().stream()
          .collect(Collectors.toMap(e -> e.getKey(),
              e -> cache.cached(e.getValue(), "selectSources", "getFiles")));
    }

    this.clientName = clientName;
    this.storages = storages;
  }

  @Override
  public BackupFtpFile getWorkingDirectory() throws FtpException {
    return new BackupFtpFile(workingDir);
  }

  @Override
  public boolean changeWorkingDirectory(String dir) throws FtpException {
    BackupFtpFile newDir = getFile(dir);

    if (log.isDebugEnabled()) {
      log.debug("changing from {} to {}: {} exists = {}", workingDir, dir,
          newDir, newDir.exists);
    }

    if (newDir.exists) {
      workingDir = newDir.getAbsolutePath();
      return true;
    } else {
      return false;
    }
  }

  @Override
  public BackupFtpFile getHomeDirectory() throws FtpException {
    return new BackupFtpFile("");
  }

  @Override
  public BackupFtpFile getFile(String file) throws FtpException {
    Path filePath;

    if (file.length() > 0 && file.charAt(0) == '/') {
      filePath = new Path(file);
    } else {
      filePath = new Path(workingDir, file);
    }

    return new BackupFtpFile(filePath);
  }

  @Override
  public boolean isRandomAccessible() throws FtpException {
    return false;
  }

  @Override
  public void dispose() {
  }

  /**
   * Implementation of an Apache FtpServer FtpFile
   */
  public class BackupFtpFile implements FtpFile {

    private final Path path;
    private Date time = new Date();
    private File file;
    private Storage storage;
    private boolean exists = true;
    private Supplier<Stream<String>> list;

    private BackupFtpFile(String f) {
      this(new Path(f));
    }

    private BackupFtpFile(Path path) {
      this.path = path;
      int count = path.getElementCount();

      log.debug("Creation of new BackupFtpFile with path {} ({} elements)",
          path, count);

      if (count == 0 || path.getLastElement().length() == 0) {
        list = () -> Stream.of(FORMATTER.format(time));
      } else {
        try {
          time = FORMATTER.parse(path.getElementAt(0));
        } catch (ParseException ex) {
          throw new RuntimeException(ex);
        }

        if (count == 1) {
          list = () -> storages.keySet().stream();
        } else {
          storage = storages.get(path.getElementAt(1));

          if (storage == null) {
            exists = false;
          } else if (count == 2) {
            list = () -> getSources(clientName).stream()
                .map(Source::getName);
          } else {
            Optional<Source> source = getSources(clientName).stream()
                .filter(s -> path.getElementAt(2).equals(s.getName())).findAny();

            if (!source.isPresent()) {
              exists = false;
            } else if (count == 3) {
              list = () -> loadFiles(source.get().getId(), "",
                  time.getTime()).stream().map(File::getName);
            } else {
              String parent;

              if (count == 4) {
                parent = "";
              } else {
                parent = path.getSubPath(3, count - 1).toString();
              }

              int sourceId = source.get().getId();
              file = loadFiles(sourceId, parent, time.getTime()).stream()
                  .filter(f -> path.getLastElement().equals(f.getName()))
                  .findFirst().orElse(null);

              if (file == null) {
                exists = false;
              } else if (file.isDirectory()) {
                list = () -> loadFiles(sourceId, file.toString(),
                    time.getTime()).stream().map(File::getName);
              }
            }
          }
        }
      }
    }

    private List<Source> getSources(String client) {
      return storage.selectSources(client);
    }

    private List<File> loadFiles(int sourceId, String parent, long instant) {
      Map<String, List<File>> files
          = storage.getFiles(sourceId, instant).stream()
              .collect(Collectors.groupingBy(File::getParent));
      List<File> loadedFiles = files.get(parent);
      return loadedFiles == null ? Collections.emptyList() : loadedFiles;
    }

    @Override
    public boolean isDirectory() {
      return file == null || file.isDirectory();
    }

    @Override
    public boolean isFile() {
      return file != null && file.isFile();
    }

    @Override
    public boolean doesExist() {
      return exists;
    }

    @Override
    public boolean isReadable() {
      return true;
    }

    @Override
    public long getLastModified() {
      return file == null ? System.currentTimeMillis()
          : Optional.ofNullable(file.getLastModified()).orElse(time.getTime());
    }

    @Override
    public boolean setLastModified(long time) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getSize() {
      return file == null ? 0 : file.getSize();
    }

    @Override
    public Object getPhysicalFile() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<BackupFtpFile> listFiles() {
      if (list == null) {
        return null;
      } else {
        return Collections.unmodifiableList(list.get().sorted()
            .map(n -> new BackupFtpFile(path.add(n)))
            .collect(Collectors.toList()));
      }
    }

    @Override
    public InputStream createInputStream(long offset) throws IOException {
      Counter counter = new Counter();
      List<Chunk> chunks = storage.getFileChunks(file.getHash().getMain());
      List<Pair<Long, byte[]>> hashList = chunks.stream()
          .map(chunk -> new Pair<>(counter.increment(), chunk.getHash()))
          .collect(Collectors.toList());
      IOSupplier<byte[]> supplier = storage.getChunkSupplier(hashList,
          Integer.MAX_VALUE);

      return new InputStream() {
        byte[] buf = new byte[0];
        int pos = 0;
        int chunkIndex = 0;

        @Override
        public int read() throws IOException {
          if (pos == buf.length && !getMoreBytes()) {
            return -1;
          }

          return ((int) buf[pos++]) & 0x000000FF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
          if (len == 0) {
            return 0;
          }

          if (pos == buf.length && !getMoreBytes()) {
            return -1;
          }

          int available = buf.length - pos;
          int result = Math.min(available, len);
          System.arraycopy(buf, pos, b, off, result);
          pos += result;

          return result;
        }

        private boolean getMoreBytes() throws IOException {
          if (chunkIndex < chunks.size()) {
            int chunkLength = chunks.get(chunkIndex).getLength();

            byte[] data = supplier.get();

            if (data != null) {
              if (data.length < chunkLength) {
                // if a chunk is smaller than expected, it is compressed
                data = Compression.decompress(data);
              }

              if (data.length != chunkLength) {
                log.warn("Chunk {} has wrong size", chunkIndex);
              } else {
                chunkIndex++;
                pos = 0;
                buf = data;
                return true;
              }
            }
          }

          return false;
        }
      };
    }

    @Override
    public String getAbsolutePath() {
      return "/" + path + (!path.isRoot() && isDirectory() ? "/" : "");
    }

    @Override
    public String getName() {
      return path.getLastElement();
    }

    @Override
    public boolean isHidden() {
      return false;
    }

    @Override
    public boolean isWritable() {
      return false;
    }

    @Override
    public boolean isRemovable() {
      return false;
    }

    @Override
    public String getOwnerName() {
      return "bfts";
    }

    @Override
    public String getGroupName() {
      return "bfts";
    }

    @Override
    public int getLinkCount() {
      return 1;
    }

    @Override
    public boolean mkdir() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean delete() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean move(FtpFile destination) {
      throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream createOutputStream(long offset) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "BackupFtpFile{" + "path=" + path + ", time=" + time + ", file="
          + file + '}';
    }
  }

  private interface FileStorage {
    Source getSource(int id);

    List<File> getFiles(int sourceId, long instant);
  }

  private static class FileStorageImpl implements FileStorage {
    @Override
    public Source getSource(int id) {
      return null;
    }

    @Override
    public List<File> getFiles(int sourceId, long instant) {
      return null;
    }
  }
}
