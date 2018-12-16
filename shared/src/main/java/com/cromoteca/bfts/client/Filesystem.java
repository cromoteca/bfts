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

import com.cromoteca.bfts.model.Chunk;
import com.cromoteca.bfts.model.File;
import com.cromoteca.bfts.model.Hash;
import com.cromoteca.bfts.model.IgnoredFileChecker;
import com.cromoteca.bfts.model.Pair;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.util.Compression;
import com.cromoteca.bfts.util.Factory;
import com.cromoteca.bfts.util.FilePath;
import com.cromoteca.bfts.util.Hex;
import com.cromoteca.bfts.util.TaskDuration;
import com.cromoteca.bfts.util.lambdas.IOSupplier;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.cromoteca.bfts.util.Util.*;

/**
 * Interacts with the directories that must be backed up.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Filesystem {
  private static final Logger log = LoggerFactory.getLogger(Filesystem.class);
  /**
   * Algorithm used to create file hashes.
   */
  public static final String HASH_ALGORITHM = "SHA-1";
  public static final int DEFAULT_CHUNK_SIZE = 512 * 1024;

  private boolean skipRecycledHashCheck = true;
  private int filesystemScanSize = 800;
  private final int chunkSize;
  private final Factory hashBufferFactory;

  public Filesystem() {
    this(DEFAULT_CHUNK_SIZE);
  }

  public Filesystem(int chunkSize) {
    this.chunkSize = chunkSize;
    hashBufferFactory = new Factory();
    hashBufferFactory.registerThreadLocal(byte[].class, () -> new byte[chunkSize]);
  }

  /**
   * Maximum number of files listed on each backup scan
   */
  public int getFilesystemScanSize() {
    return filesystemScanSize;
  }

  public void setFilesystemScanSize(int filesystemScanSize) {
    this.filesystemScanSize = filesystemScanSize;
  }

  /**
   * Size of a file chunk
   */
  public int getChunkSize() {
    return chunkSize;
  }

  /**
   * Returns true if the hash check for recycled files will be skipped
   * (downloaded files are always checked).
   */
  public boolean isSkipRecycledHashCheck() {
    return skipRecycledHashCheck;
  }

  public void setSkipRecycledHashCheck(boolean skipRecycledHashCheck) {
    this.skipRecycledHashCheck = skipRecycledHashCheck;
  }

  /**
   * Collects files in a directory. BFTS requires that the files are backed up
   * in a fixed order, so deletions can be catched easily. For example, if the
   * files b,c,d,e,a are backed up, and then only b,c,e,a are sent to the
   * storage, it means that d has been deleted. This method will collect files
   * using alphabetical order for items in the same directory: a, a/b, b, b/a,
   * b/b, b/b/a, b/b/b, b/c, c and so on.
   *
   * @param root the directory path
   * @param from relative path of the last collected file
   * @param num  maximum number of files to collect
   * @return a sorted list of files
   */
  public List<File> getFiles(FilePath root, String from,
      IgnoredFileChecker checker) throws IOException {
    TaskDuration duration = new TaskDuration();

    List<File> files = new ArrayList<>();
    FilePath fromPath = root.resolve(from);
    FilePath start = fromPath;

    // we need a directory to start walking, so we must find the one that
    // contains our initial file. If it doesn't exist anymore, maybe even
    // some ancestors don't exist, so we may need to go up until we find one
    while (!start.isDirectory()) {
      start = start.getParent();
    }

    // search files that come after the initial file
    // start != null is useful when root is a filesystem root (e.g. f:\)
    while (files.size() < filesystemScanSize && start != null
        && start.startsWith(root)) {
      addFilesToList(files, p -> p.compareTo(fromPath) > 0, root, start, checker);
      start = start.getParent();
    }

    // if we haven't found enough files, we must "rewind" and start from the
    // root dir, until we get enough files or we reach the initial file again
    if (files.size() < filesystemScanSize) {
      addFilesToList(files, p -> p.compareTo(fromPath) <= 0, root, root, checker);
    }

    log.debug("{} files collected in {} seconds", files.size(), duration);
    return files;
  }

  /**
   * Recursively add files to the passed list. Items in the same directory are
   * ordered alphabetically.
   *
   * @param files     the list of files
   * @param condition must be satisfied to be included into the list
   * @param root      directory root
   * @param what      directory to add
   * @param max       maximum number of files
   */
  private void addFilesToList(List<File> files, Predicate<FilePath> condition,
      FilePath root, FilePath what, IgnoredFileChecker checker)
      throws IOException {
    try (Stream<FilePath> list = what.list()) {
      // order by file name
      Iterator<FilePath> i
          = list.sorted(orderBy(FilePath::getFileName)).iterator();

      while (files.size() < filesystemScanSize && i.hasNext()) {
        FilePath p = i.next();

        // only files satisfying the condition can be collected
        if (condition.test(p) && p.isReadable()) {
          File f = getFile(p, root, checker);

          if (f != null) {
            files.add(f);

            if (f.isDirectory()) {
              log.debug("Collected directory {}", f);
              addFilesToList(files, condition, root, p, checker);
            } else {
              log.debug("Collected File {}", f);
            }
          }
        }
      }
    }
  }

  public File getFile(FilePath path, FilePath root, IgnoredFileChecker checker)
      throws IOException {
    File file = null;
    String fileName = path.getFileName();

    if (checker == null || checker.checkNotMatched(fileName)) {
      FilePath relative = path.isAbsolute() ? root.relativize(path) : path;
      FilePath parent = relative.getParent();
      String fileDir = parent == null ? "" : parent.toString();

      if (checker == null || checker.checkNotMatched(fileName, fileDir)) {
        FilePath absolute = path.isAbsolute() ? path : root.resolve(path);

        if (absolute.isDirectory()) {
          file = new File(fileName, fileDir);
        } else if (absolute.isRegularFile()) {
          file = new File(fileName, fileDir, absolute.size(),
              absolute.getLastModifiedTime().to(TimeUnit.MILLISECONDS));
        }
      }
    }

    return file;
  }

  /**
   * Returns true if the file seems not to have changed: file must still exist
   * and its size and time must match.
   */
  private boolean isUnchanged(FilePath path, long size, long lastModified)
      throws IOException {
    return path.isRegularFile() && path.size() == size
        && path.getLastModifiedTime().to(TimeUnit.MILLISECONDS) == lastModified;
  }

  /**
   * Reads a file chunk.
   */
  public byte[] readChunk(Chunk chunk) {
    File file = chunk.getFile();
    FilePath path = FilePath.get(file.getSource().getRootPath())
        .resolve(file.getParent()).resolve(file.getName());
    long size = file.getSize();
    long lastModified = file.getLastModified();

    try {
      if (!isUnchanged(path, size, lastModified)) {
        log.debug("File {} changed before reading chunk #{}", file,
            chunk.getIndex());
      } else {
        // last chunk can be smaller than others
        int length = (int) Math.min(size - chunk.getIndex() * (long) chunkSize,
            chunkSize);

        if (length != chunk.getLength()) {
          log.warn("The chunk #{} of {} has a size of {} bytes, but was"
              + " expected to be {} bytes long", chunk.getIndex(), file, length,
              chunk.getLength());
        } else {
          MessageDigest md = getMessageDigest();

          try (RandomAccessFile is = new RandomAccessFile(path.toFile(), "r")) {
            // seek is much faster than FileInputStream.skip
            is.seek(chunk.getIndex() * (long) chunkSize);
            byte[] data = new byte[length];
            int n = is.read(data);

            if (n != length) {
              log.warn("Only {} bytes read instead of {}: file {}, chunk #{}",
                  n, length, file, chunk.getIndex());
            } else {
              md.update(data);
              // add the chunk length to the hash
              byte[] newHash = md.digest(longToBytes(length));

              // compare hash with the expected one
              if (!Arrays.equals(newHash, chunk.getHash())) {
                log.warn("Hash is different from expected: file {}, chunk #{}",
                    file, chunk.getIndex());
              } else {
                byte[] compressed = Compression.compress(data);

                // use compression if it makes the chunk smaller
                if (compressed.length < length) {
                  if (log.isDebugEnabled()) {
                    log.debug("Chunk {} compressed to {}% of its original"
                        + " size", chunk, compressed.length * 100 / length);
                  }

                  data = compressed;
                } else {
                  log.debug("Chunk {} not compressed", chunk);
                }

                if (isUnchanged(path, size, lastModified)) {
                  return data;
                } else {
                  log.debug("File {} changed after having read chunk #{}",
                      file, chunk.getIndex());
                }
              }
            }
          } catch (FileNotFoundException ex) {
            log.debug("File locked or deleted", ex);
          }
        }
      }
    } catch (IOException ex) {
      log.error(null, ex);
    }

    return null;
  }

  public DeletionOutcome deleteFile(File file, FilePath path, FilePath root)
      throws IOException {
    if (!path.exists()) {
      // file has already been deleted. This is considered a successful outcome,
      // since the backup storage has asked for deletion and that means that it
      // was not informed about this file not existing anymore
      return DeletionOutcome.ALREADY_DELETED;
    }

    if (file.isDirectory() && path.isDirectory() && isEmpty(path)) {
      // delete empty folders
      path.delete();
      return DeletionOutcome.DELETED;
    } else if (file.isFile()
        && isUnchanged(path, file.getSize(), file.getLastModified())) {
      if (file.getSize() == 0) {
        // delete empty files
        path.delete();
        return DeletionOutcome.DELETED;
      }

      // dispose the file so it can be recycled in case of file move
      FilePath recyclable = getRecycled(file.getHash().getMain(), root);

      if (recyclable != null) {
        FilePath recycleBin = recyclable.getParent();

        try {
          recycleBin.createDirectories();
          recycleBin.setHiddenInWindows(true);
        } catch (IOException ex) {
          log.warn("Can't create recycle bin directory {}: {}", recycleBin,
              ex.getMessage());
          recyclable = null;
        }
      }

      if (recyclable == null || recyclable.exists()) {
        // delete non recyclable files
        path.delete();
        return DeletionOutcome.DELETED;
      } else {
        path.move(recyclable);
        recyclable.setLastModifiedTime(FileTime.from(Instant.now()));
        return DeletionOutcome.DISPOSED;
      }
    }

    return DeletionOutcome.FAILED;
  }

  /**
   * Possible outcomes for a file deletion.
   */
  public static enum DeletionOutcome {
    FAILED, ALREADY_DELETED, DELETED, DISPOSED;
  }

  /**
   * Possible outcomes for a file creation.
   */
  public static enum CreationOutcome {
    FAILED, CREATED, DOWNLOADED, RECYCLED, POSTPONED;
  }

  /**
   * Creates directories and empty files, and recycles disposed files.
   *
   * @param files    list of files to be created
   * @param root     source root
   * @param callback called after a creation attempt
   * @return a list of files that haven't been created since their length is not
   *         zero and they weren't available in the recycle bin: those files
   *         must be downloaded
   */
  public List<Pair<FilePath, File>> createFiles(
      List<Pair<FilePath, File>> files, FilePath root,
      BiConsumer<File, CreationOutcome> callback) {
    // return object
    List<Pair<FilePath, File>> remaining = new ArrayList<>();

    for (Pair<FilePath, File> pair : files) {
      FilePath path = pair.getFirst();
      File file = pair.getSecond();

      CreationOutcome outcome = CreationOutcome.FAILED;

      try {
        if (root.isDirectory()) {
          if (file.isDirectory()) {
            if (path.exists()) {
              if (path.isDirectory()) {
                outcome = CreationOutcome.RECYCLED;
              }
            } else {
              path.createDirectories();
              outcome = CreationOutcome.CREATED;
            }
          } else if (file.isFile() && !path.exists()) {
            if (file.getSize() == 0) {
              path.getParent().createDirectories();
              path.createFile();
              outcome = CreationOutcome.CREATED;
            } else {
              // try to recycle the file if it has been previously disposed
              FilePath recycled = getRecycled(file.getHash().getMain(), root);

              if (recycled.exists() && recycled.size() == file.getSize()) {
                boolean ok = skipRecycledHashCheck;

                if (!ok) {
                  byte[] expectedHash = file.getHash().getMain();
                  byte[] actualHash = hash(recycled, false).getMain();
                  ok = Arrays.equals(expectedHash, actualHash);
                }

                if (ok) {
                  path.getParent().createDirectories();
                  recycled.move(path);
                  outcome = CreationOutcome.RECYCLED;
                } else {
                  // seems to be a wrong recyclable file (should never happen)
                  recycled.delete();
                }
              }
            }
          }
        }
      } catch (IOException ex) {
        log.error(null, ex);
      }

      if (file.isFile()) {
        if (outcome == CreationOutcome.FAILED) {
          if (file.getSize() > 0) {
            remaining.add(pair);
            outcome = CreationOutcome.POSTPONED;
          }
        } else {
          try {
            // use the modification time from the other client
            path.setLastModifiedTime(FileTime.from(file.getLastModified(),
                TimeUnit.MILLISECONDS));
            // not all filesystems will set the time exactly, so get the time
            // back again from the created file itself
            file.setLastModified(path.getLastModifiedTime()
                .to(TimeUnit.MILLISECONDS));
          } catch (IOException ex) {
            log.error(null, ex);
          }
        }
      }

      callback.accept(file, outcome);
    }

    return remaining;
  }

  /**
   * Download files from storage.
   *
   * @param files            list of files to be downloaded
   * @param root             source root
   * @param bytesFromStorage supplies chunk content
   * @param callback         called after a download attempt
   */
  public void getFilesFromStorage(List<Pair<FilePath, File>> files,
      FilePath root, IOSupplier<byte[]> bytesFromStorage,
      BiConsumer<File, CreationOutcome> callback) {
    MessageDigest verifyDigest = getMessageDigest();

    for (Pair<FilePath, File> pair : files) {
      FilePath path = pair.getFirst();
      File file = pair.getSecond();
      CreationOutcome outcome = CreationOutcome.FAILED;

      // root.isDirectory() is similar to isAvailable(source)
      if (root.isDirectory() && !path.exists()) {
        FilePath temp = null;

        try {
          temp = path;

          // search for a suitable directory to store the temporary download
          while (!temp.isDirectory()) {
            temp = temp.getParent();
          }

          temp = temp.resolve('.' + path.getFileName() + '_'
              + System.currentTimeMillis() + File.BFTS_SUFFIX);

          try {
            temp.createFile();
          } catch (FileAlreadyExistsException ex) {
            // we can accept an existing file, as long as it can be overwritten
            // this is *really* unlikely to happen anyway
            log.debug("Temp file already exists", ex);
          }

          if (!temp.isWritable()) {
            log.warn("Temp file {} is not writable and so file {} can't"
                + " be written", temp, path);
          } else {
            temp.setHiddenInWindows(true);

            try (OutputStream os = temp.newOutputStream()) {
              for (Chunk chunk : file.getHash().getChunks()) {
                // chunks are supplied in the right order
                byte[] data = bytesFromStorage.get();

                if (data != null) {
                  if (data.length < chunk.getLength()) {
                    // if a chunk is smaller than expected, it is compressed
                    data = Compression.decompress(data);
                  }

                  if (data.length != chunk.getLength()) {
                    log.warn("Chunk {} has wrong size", chunk);
                  } else {
                    verifyDigest.update(data);
                    byte[] dataHash
                        = verifyDigest.digest(longToBytes(data.length));

                    if (Arrays.equals(dataHash, chunk.getHash())) {
                      os.write(data);
                    } else {
                      log.warn("Hash does not match content in chunk {}", chunk);
                    }
                  }
                }
              }
            }

            if (temp.size() != file.getSize()) {
              log.warn("File {} written as {}, but its size does not match"
                  + " expected value", path, temp);
            } else {
              // compare hashes
              byte[] expectedHash = file.getHash().getMain();
              byte[] actualHash = hash(temp, false).getMain();

              if (!Arrays.equals(expectedHash, actualHash)) {
                log.warn("File {} written as {}, but its hash does not match"
                    + " expected value", path, temp);
              } else {
                temp.setLastModifiedTime(FileTime.from(file.getLastModified(),
                    TimeUnit.MILLISECONDS));
                // not all filesystems will set the time exactly
                file.setLastModified(temp.getLastModifiedTime()
                    .to(TimeUnit.MILLISECONDS));
                path.getParent().createDirectories();
                temp.move(path);
                path.setHiddenInWindows(false);
                outcome = CreationOutcome.DOWNLOADED;
              }
            }
          }
        } catch (IOException ex) {
          log.error(null, ex);
        } finally {
          // get rid of the temp file if something went wrong
          if (temp != null && temp.exists()) {
            try {
              temp.delete();
            } catch (IOException ex) {
              log.error(null, ex);
            }
          }
        }
      }

      // notify
      callback.accept(file, outcome);

      if (outcome == CreationOutcome.FAILED) {
        // stop downloading files after an error, as wrong chunks might be
        // received for the next files
        break;
      }
    }
  }

  /**
   * Deletes files older than 3 days from the recycle bin.
   *
   * @param source the source whose recycle bin must be cleaned
   */
  public void collectTrash(Source source) throws IOException {
    Instant threeDaysAgo = Instant.now().minus(Duration.ofDays(3));
    FilePath recycleBin = getRecycleBin(FilePath.get(source.getRootPath()));

    if (recycleBin.isDirectory()) {
      boolean empty = true;

      try (Stream<FilePath> list = recycleBin.list()) {
        // avoid lambda so IOExceptions can be thrown
        for (Iterator<FilePath> it = list.iterator(); it.hasNext();) {
          FilePath path = it.next();
          Instant lmt = path.getLastModifiedTime().toInstant();

          if (lmt.isBefore(threeDaysAgo)) {
            path.delete();
            log.debug("File {} has been removed from trash", path.getFileName());
          } else {
            empty = false;
          }
        }
      }

      if (empty) {
        recycleBin.delete();
      }
    }
  }

  /**
   * Returns the corresponding path in the recycle bin.
   */
  private FilePath getRecycled(byte[] hash, FilePath root) {
    return hash == null ? null
        : getRecycleBin(root).resolve(Hex.printHexBinary(hash));
  }

  /**
   * Returns the recycle bin dir.
   */
  private static FilePath getRecycleBin(FilePath root) {
    return root.resolve(".recycle-bin" + File.BFTS_SUFFIX);
  }

  /**
   * Hashes a file and its chunks, checking whether the file has changed in the
   * meanwhile.
   *
   * @param file      the file
   * @param chunkSize the desired chunk size
   * @return true if the hashes have been calculated
   * @throws IOException
   */
  public boolean hash(File file) throws IOException {
    FilePath path = FilePath.get(file.getSource().getRootPath())
        .resolve(file.getParent()).resolve(file.getName());
    log.debug("Hashing {}", path);
    long size = file.getSize();
    long lastModified = file.getLastModified();

    if (isUnchanged(path, size, lastModified)) {
      Hash hash = hash(path, true);

      if (hash != null && isUnchanged(path, size, lastModified)) {
        file.setHash(hash);
        return true;
      }
    }

    return false;
  }

  /**
   * Hashes a file.
   *
   * @param p         the file path
   * @param chunkSize the desired chunk size, or 0 if partial hashes must not be
   *                  computed
   */
  public Hash hash(FilePath p, boolean partialHashes) throws IOException {
    Hash h = new Hash();

    try {
      if (!p.exists()) {
        return null;
      }

      long fileSize = p.size();
      MessageDigest fullDigest = getMessageDigest();
      MessageDigest chunkDigest = getMessageDigest();

      try (final DigestInputStream dis
          = new DigestInputStream(p.newInputStream(), fullDigest)) {
        List<Chunk> chunks = new ArrayList<>();
        byte[] buf = hashBufferFactory.obtain(byte[].class);
        int read;

        while ((read = dis.read(buf)) != -1) {
          if (partialHashes) {
            chunkDigest.update(buf, 0, read);
            Chunk c = new Chunk();
            c.setLength(read);
            c.setHash(chunkDigest.digest(longToBytes(read)));
            chunks.add(c);
          }
        }

        h.setChunks(chunks);
      }

      h.setMain(fullDigest.digest(longToBytes(fileSize)));
    } catch (IOException ex) {
      log.debug("Can't hash file {}: {}", p, ex.getMessage());
      h = null;
    }

    return h;
  }

  private static MessageDigest getMessageDigest() {
    try {
      return MessageDigest.getInstance(HASH_ALGORITHM);
    } catch (NoSuchAlgorithmException ex) {
      // never happens if we use MD5, SHA-1 or SHA-256
      return null;
    }
  }

  public static boolean isEmpty(FilePath dir) throws IOException {
    try (Stream<FilePath> list = dir.list()) {
      return list.count() == 0;
    }
  }

  public static byte[] longToBytes(long l) {
    // Long.toHexString is lowercase
    return Long.toHexString(l).getBytes(StandardCharsets.UTF_8);
  }
}
