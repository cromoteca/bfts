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
package com.cromoteca.bfts.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;

/**
 * Delegates to a {@code java.nio.file.Path} object, changing its behavior a bit
 * to be more platform independent. Most methods are the exact counterparts of
 * {@code java.nio.file.Path} and {@code java.nio.file.Files} methods.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class FilePath implements Comparable<FilePath> {
  private static final org.slf4j.Logger log
      = LoggerFactory.getLogger(FilePath.class);
  final Path p;
  private String asString;

  public FilePath(Path... paths) {
    if (paths.length == 0) {
      throw new IllegalArgumentException("No path provided");
    }

    Path path = paths[0];

    for (int i = 1; i < paths.length; i++) {
      path = path.resolve(paths[i]);
    }

    p = path;
  }

  /**
   * Creates a new FilePath.
   */
  public static FilePath get(String first, String... more) {
    return new FilePath(Paths.get(first, more));
  }

  public String getFileName() {
    return p.getFileName().toString();
  }

  public FilePath getParent() {
    Path parent = p.getParent();
    return parent == null ? null : new FilePath(parent);
  }

  public int getNameCount() {
    return p.getNameCount();
  }

  public String getName(int index) {
    return p.getName(index).toString();
  }

  public FilePath resolve(FilePath other) {
    return new FilePath(p.resolve(other.p));
  }

  public FilePath resolve(String other) {
    return new FilePath(p.resolve(other));
  }

  public boolean startsWith(FilePath other) {
    return p.startsWith(other.p);
  }

  public FilePath relativize(FilePath other) {
    return new FilePath(p.relativize(other.p));
  }

  public FilePath normalize() {
    return new FilePath(p.normalize());
  }

  public File toFile() {
    return p.toFile();
  }

  /**
   * Returns the path as String, using forward slashes.
   */
  @Override
  public String toString() {
    if (asString == null) {
      asString = p == null ? "" : p.toString().replace('\\', '/');
    }

    return asString;
  }

  public String getNativePath() {
    return p.toString();
  }

  @Override
  public int hashCode() {
    return 287 + Objects.hashCode(p);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    return Objects.equals(p, ((FilePath) o).p);
  }

  /**
   * Natural order for this class is different from the one of
   * <code>java.nio.file.Path</code>: it is platform independent and orders
   * items inside the same directory. For example:
   * <ul>
   * <li>a</li>
   * <li>a/a</li>
   * <li>a/a/a</li>
   * <li>a/a/b</li>
   * <li>a/b</li>
   * <li>a/b/a</li>
   * <li>a/c</li>
   * <li>b</li>
   * </ul>
   * Ordering is case-sensitive.
   */
  @Override
  public int compareTo(FilePath other) {
    if (other == null) {
      throw new NullPointerException();
    }

    int l1 = getNameCount();
    int l2 = other.getNameCount();
    int result;

    for (int i = 0; i < l1; i++) {
      if (i >= l2) {
        return 1;
      }

      result = getName(i).compareTo(other.getName(i));

      if (result != 0) {
        return result;
      }
    }

    return l2 > l1 ? -1 : 0;
  }

  public boolean exists() {
    return Files.exists(p);
  }

  public boolean isDirectory() {
    return Files.isDirectory(p);
  }

  public boolean isReadable() {
    return Files.isReadable(p);
  }

  public boolean isWritable() {
    return Files.isWritable(p);
  }

  public boolean isRegularFile() {
    return Files.isRegularFile(p);
  }

  public boolean isAbsolute() {
    return p.isAbsolute();
  }

  public FileTime getLastModifiedTime() throws IOException {
    return Files.getLastModifiedTime(p);
  }

  public FilePath setLastModifiedTime(FileTime time) throws IOException {
    return new FilePath(Files.setLastModifiedTime(p, time));
  }

  public long size() throws IOException {
    return Files.size(p);
  }

  public void delete() throws IOException {
    Files.delete(p);
  }

  public FilePath move(FilePath target, CopyOption... options)
      throws IOException {
    return new FilePath(Files.move(p, target.p, options));
  }

  public FilePath createDirectories() throws IOException {
    return new FilePath(Files.createDirectories(p));
  }

  public FilePath createFile() throws IOException {
    return new FilePath(Files.createFile(p));
  }

  public OutputStream newOutputStream() throws IOException {
    return Files.newOutputStream(p);
  }

  public InputStream newInputStream() throws IOException {
    return Files.newInputStream(p);
  }

  public BufferedWriter newAppendWriter() throws IOException {
    return Files.newBufferedWriter(p, StandardOpenOption.APPEND);
  }

  public byte[] readAllBytes() throws IOException {
    return Files.readAllBytes(p);
  }

  public FilePath write(byte[] bytes) throws IOException {
    return new FilePath(Files.write(p, bytes));
  }

  public Stream<FilePath> list() throws IOException {
    return Files.list(p).map(FilePath::new);
  }

  public Stream<FilePath> walk() throws IOException {
    return Files.walk(p).map(FilePath::new);
  }

  public boolean isMatchedBy(PathMatcher... matchers) {
    return Arrays.stream(matchers).anyMatch(m -> m.matches(p));
  }

  /**
   * Changes the hidden attribute in Windows.
   *
   * @param hidden true or false to hide or unhide the file
   */
  public void setHiddenInWindows(boolean hidden) {
    DosFileAttributeView view
        = Files.getFileAttributeView(p, DosFileAttributeView.class);

    if (view != null) {
      try {
        view.setHidden(hidden);
      } catch (IOException ex) {
        log.debug(null, ex);
      }
    }
  }
}
