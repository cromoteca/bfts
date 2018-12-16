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
package com.cromoteca.bfts.testutil;

import com.cromoteca.bfts.util.FilePath;
import com.cromoteca.bfts.util.Util;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.Iterator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class to perform operations on the contents of a directory.&nbsp;Similar
 * to <code>java.nio.file.FileVisitor</code>, but it allows to sort file while
 * walking.
 *
 * Override the methods {@link #preWalkDirectory}, {@link #postWalkDirectory}
 * and {@link #walkFile} to define the actions to be taken for files and
 * directories included in the walked directory. You can also override
 * {@link #preWalk} and {@link #postWalk} to do additional operations before and
 * after the directory parsing.
 *
 * Then you can start the parsing by calling {@link #startWalk}.
 *
 * @author Luciano Vernaschi
 */
public class DirectoryWalker {
  private static final Logger log
      = LoggerFactory.getLogger(DirectoryWalker.class);
  private final boolean sorted;

  /**
   * Creates a <code>DirectoryWalker</code>.
   *
   * @param sorted if true, contents will be sorted when entering a directory.
   */
  public DirectoryWalker(boolean sorted) {
    this.sorted = sorted;
  }

  /**
   * Returns whether files and directories will be sorted or not.
   */
  public boolean isSorted() {
    return sorted;
  }

  /**
   * Starts walking.
   *
   * @param path the path to be walked
   */
  public void startWalk(FilePath path) throws IOException {
    if (!path.isDirectory()) {
      return;
    }

    if (beforeWalk()) {
      walk(path);
    }

    afterWalk();
  }

  private void walk(FilePath path) throws IOException {
    if (path.isDirectory()) {
      if (preVisitDirectory(path)) {
        try (Stream<FilePath> list = path.list()) {
          Stream<FilePath> stream
              = sorted ? list.sorted(Util.orderBy(FilePath::getFileName)) : list;
          Iterator<FilePath> i = stream.iterator();

          while (i.hasNext()) {
            walk(i.next());
          }
        }
      }

      postVisitDirectory(path);
    } else {
      visitFile(path);
    }
  }

  /**
   * Called during the walk, but before any element has been walked. If it
   * returns false, no walking will take place. The base implementation does
   * nothing and returns true.
   *
   * @return if false, no walking will happen at all
   */
  protected boolean beforeWalk() throws IOException {
    return true;
  }

  /**
   * Called at the end of the walking. It is called even if {@link #beforeWalk}
   * returned false. The base implementation does nothing and returns true.
   */
  protected void afterWalk() throws IOException {
  }

  /**
   * Called before visiting a directory. The default implementation does nothing
   * and returns true.
   *
   * @param path the directory
   *
   * @return true if the directory must be walked, false otherwise
   */
  protected boolean preVisitDirectory(FilePath path) throws IOException {
    return true;
  }

  /**
   * Called after a directory has been visited.
   *
   * @param path the directory
   */
  protected void postVisitDirectory(FilePath path) throws IOException {
  }

  /**
   * Called for any file found while parsing the base directory.
   *
   * @param path the file
   */
  protected void visitFile(FilePath path) throws IOException {
  }

  /**
   * Removes a directory, even if not empty.
   *
   * @param path the directory
   *
   * @throws IOException if thrown when deleting single files or directories
   */
  public static void rmDir(FilePath path) throws IOException {
    if (!path.exists()) {
      throw new NoSuchFileException(path.toString());
    }

    if (!path.isDirectory()) {
      throw new NotDirectoryException(path.toString());
    }

    new DirectoryRemover().removeDirectory(path);
  }

  public static void rmDirSilent(FilePath path) {
    if (!path.exists()) {
      return;
    }

    if (!path.isDirectory()) {
      return;
    }

    try {
      new DirectoryRemover().removeDirectory(path);
    } catch (IOException ex) {
      log.debug(null, ex);
    }
  }

  private static class DirectoryRemover extends DirectoryWalker {
    public DirectoryRemover() {
      super(false);
    }

    @Override
    protected void postVisitDirectory(FilePath path) throws IOException {
      path.delete();
    }

    @Override
    protected void visitFile(FilePath path) throws IOException {
      path.delete();
    }

    public void removeDirectory(FilePath path) throws IOException {
      startWalk(path);
    }
  }
}
