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

import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.storage.FileStatus;
import com.cromoteca.bfts.util.TaskDuration;
import com.cromoteca.bfts.util.Util;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedule execution of methods of a {@link ClientActivities} instance.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class ClientScheduler {
  private static final Logger log
      = LoggerFactory.getLogger(ClientScheduler.class);
  /**
   * A minimum pause that must always occur between executions in the same
   * thread
   */
  private static final long MIN_PAUSE = 100;
  private final ClientActivities activity;
  /**
   * Used to stop thread when false
   */
  private boolean running = true;
  private boolean fast = false;
  private long fileDelay;
  private long hashDelay;
  private long chunkDelay;
  private long realtimeDelay;
  private final int filesystemScanMinPause;
  private final int filesystemScanMaxPause;
  private Thread fileThread;
  private Thread hashThread;
  private Thread chunkThread;
  private Thread realtimeThread;
  private int fastCounter;

  public ClientScheduler(ClientActivities activity, int filesystemScanMinPause,
      int filesystemScanMaxPause) {
    this.activity = activity;
    this.filesystemScanMinPause = filesystemScanMinPause;
    this.filesystemScanMaxPause = filesystemScanMaxPause;
    hashDelay = chunkDelay = fileDelay = realtimeDelay = filesystemScanMinPause;
  }

  /**
   * When fast, pauses are shorter
   */
  public boolean isFast() {
    return fast;
  }

  public void setFast(boolean fast) {
    if (fast) {
      // incremented to interrupt current pauses (see pause())
      fastCounter++;
      // reset pause duration
      fileDelay = filesystemScanMinPause;
      hashDelay = chunkDelay = MIN_PAUSE;
    }

    this.fast = fast;
  }

  public void start() {
    fileThread = new Thread(this::fileRunnable);
    fileThread.setName(activity.getClient() + "-files");
    fileThread.start();

    hashThread = new Thread(this::hashRunnable);
    hashThread.setName(activity.getClient() + "-hashes");
    hashThread.start();

    chunkThread = new Thread(this::chunkRunnable);
    chunkThread.setName(activity.getClient() + "-chunks");
    chunkThread.start();

    if (activity.isFileWatchingAvailable()) {
      realtimeThread = new Thread(this::realtimeRunnable);
      realtimeThread.setName(activity.getClient() + "-realtime");
      realtimeThread.start();
    }
  }

  // sends file information, then syncs
  private void fileRunnable() {
    TaskDuration duration = new TaskDuration();

    while (running) {
      duration.restart();
      // by default, pause doubles each time
      long nextDelay = fileDelay * 2;

      try {
        // select source to update
        Source source = activity.selectSource(false);

        if (source != null) {
          int count = activity.sendFiles(source);
          count += activity.syncDeletions(source, false).size();
          count += activity.syncAdditions(source, false).size();

          // if the source was empty and is still empty, it will always be
          // selected first, so let's process another one too
          if (count == 0 && !source.hasFilesInBackup()) {
            source = activity.selectSource(true);

            if (source != null) {
              count = activity.sendFiles(source);
              count += activity.syncDeletions(source, false).size();
              count += activity.syncAdditions(source, false).size();
            }
          }

          if (count > 0) {
            // something has changed, shorten the pause
            nextDelay = fast ? filesystemScanMinPause : fileDelay / 5;
          } else if (source != null) {
            // TODO: find a better location for this activity
            activity.collectTrash(source);
          }
        }
      } catch (Exception ex) {
        log.error(null, ex);
      }

      // make sure that the pause is longer than minimum and shorter than a
      // reasonable maximum
      fileDelay = Util.constrain(nextDelay, filesystemScanMinPause,
          filesystemScanMaxPause);
      // if executing fast, subtract the operation duration
      long actualPause = fast ? Math.max(MIN_PAUSE,
          fileDelay - duration.getMilliseconds()) : fileDelay;
      long paused = pause(actualPause);
      log.trace("{}: files thread paused for {} milliseconds",
          activity.getClient(), paused);
    }
  }

  // sends new hashes
  private void hashRunnable() {
    while (running) {
      // lenghten pause by default
      long nextDelay = hashDelay * 5;

      try {
        int hashes = activity.sendHashes(FileStatus.CURRENT);

        if (hashes > 0) {
          // hashes sent: shorten the pause
          nextDelay = fast ? MIN_PAUSE : filesystemScanMinPause;
        }
      } catch (Exception ex) {
        log.error(null, ex);
      }

      // make sure that the pause is not longer than the file thread one,
      // so if some new file is loaded, hashes will be sent in a short time
      hashDelay = Math.min(nextDelay, fileDelay);
      long paused = pause(hashDelay);
      log.trace("{}: hashes thread paused for {} milliseconds",
          activity.getClient(), paused);
    }
  }

  // stores new chunks
  private void chunkRunnable() {
    while (running) {
      // pause management is identical to the hash thread
      long nextDelay = chunkDelay * 5;

      try {
        int chunks = activity.uploadChunks(FileStatus.CURRENT);

        if (chunks > 0) {
          nextDelay = fast ? MIN_PAUSE : filesystemScanMinPause;
        }
      } catch (Exception ex) {
        log.error(null, ex);
      }

      chunkDelay = Math.min(nextDelay, hashDelay);
      long paused = pause(chunkDelay);
      log.trace("{}: chunks thread paused for {} milliseconds",
          activity.getClient(), paused);
    }
  }

  private void realtimeRunnable() {
    while (running) {
      try {
        int added = activity.processRealtimeChanges();

        if (added > 0) {
          for (int n = 1; n > 0; n = activity.sendHashes(FileStatus.REALTIME));
          for (int n = 1; n > 0; n = activity.uploadChunks(FileStatus.REALTIME));
        }
      } catch (Exception ex) {
        log.error(null, ex);
      }

      long paused = pause(realtimeDelay);
      log.trace("{}: realtime thread paused for {} milliseconds",
          activity.getClient(), paused);
    }
  }

  private long pause(long delay) {
    int lastCounter = fastCounter;
    // actual pause
    long paused = 0;

    while (running // make sure that the scheduler has not been stopped
        && lastCounter == fastCounter // setFast(true) has not been called
        && paused < delay) { // pause is not complete
      try {
        long pause = Math.min(1000, delay - paused);
        Thread.sleep(pause);
        paused += pause;
      } catch (InterruptedException ex) {
        log.warn(null, ex);
        Thread.currentThread().interrupt();
      }
    }

    return paused;
  }

  public void stop() {
    running = false;

    Stream<Thread> threads
        = Stream.of(fileThread, hashThread, chunkThread, realtimeThread);
    fileThread = hashThread = chunkThread = realtimeThread = null;

    threads.parallel().filter(Objects::nonNull).forEach(t -> {
      try {
        t.join();
      } catch (InterruptedException ex) {
        log.warn(null, ex);
        Thread.currentThread().interrupt();
      }
    });
  }
}
