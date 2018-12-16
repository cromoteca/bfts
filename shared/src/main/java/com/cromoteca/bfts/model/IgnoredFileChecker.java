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

import com.cromoteca.bfts.util.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class IgnoredFileChecker {
  private Collection<FilenameChecker> filenameCheckers = new ArrayList<>();
  private Collection<PathChecker> pathCheckers = new ArrayList<>();

  public IgnoredFileChecker(String... patterns) {
    Arrays.stream(patterns)
        .filter(s -> s != null && s.length() > 0)
        .flatMap(s -> Arrays.stream(s.split(";")))
        .forEach(s -> {
          switch (s.charAt(0)) {
            case '/':
              pathCheckers.add(new PathChecker(new Path(s)));
              break;
            case '*':
              filenameCheckers.add(new ExtensionChecker(s.substring(1)));
              break;
            default:
              filenameCheckers.add(new WholeFilenameChecker(s));
          }
        });

    filenameCheckers.add(new ExtensionChecker(File.BFTS_SUFFIX));
  }

  public boolean checkNotMatched(String filename) {
    return !filenameCheckers.stream().anyMatch(c -> c.check(filename));
  }

  public boolean checkNotMatched(String filename, String parent) {
    return !pathCheckers.stream().anyMatch(c -> c.check(filename, parent));
  }

  public boolean checkMatched(File file) {
    return filenameCheckers.stream().anyMatch(c -> c.check(file.getName()))
        || pathCheckers.stream().anyMatch(c -> c.check(file.getName(),
        file.getParent()));
  }

  private interface FilenameChecker {
    boolean check(String filename);
  }

  private static class WholeFilenameChecker implements FilenameChecker {
    private String filename;

    public WholeFilenameChecker(String filename) {
      this.filename = filename;
    }

    @Override
    public boolean check(String filename) {
      return this.filename.equalsIgnoreCase(filename);
    }
  }

  private static class ExtensionChecker implements FilenameChecker {
    private String extension;
    private int len;

    public ExtensionChecker(String extension) {
      this.extension = extension;
      len = extension.length();
    }

    @Override
    public boolean check(String filename) {
      int l = filename.length();
      return l >= len && filename.substring(l - len).equalsIgnoreCase(extension);
    }
  }

  private static class PathChecker {
    private Path path;

    public PathChecker(Path path) {
      this.path = path;
    }

    private boolean check(String filename, String parent) {
      return new Path(parent, filename).isContainedIn(path);
    }
  }
}
