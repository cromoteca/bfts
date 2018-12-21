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
package com.cromoteca.bfts;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.cromoteca.bfts.client.ClientActivities;
import com.cromoteca.bfts.client.Filesystem;
import com.cromoteca.bfts.model.Source;
import com.cromoteca.bfts.storage.EncryptedStorages;
import com.cromoteca.bfts.storage.FileStatus;
import com.cromoteca.bfts.storage.RemoteStorage;
import com.cromoteca.bfts.storage.Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Stream;

public class BackupService extends JobService {
    public static final int BACKUP_JOB_ID = 8715;
    private static final long DAY = 1000L * 60 * 60 * 24;
    static Logger log = LoggerFactory.getLogger(BackupService.class);

    public static void scheduleJob(Activity activity) {
        ConfigBean configBean = new ConfigBean(PreferenceManager.getDefaultSharedPreferences(activity));
        JobScheduler jobScheduler = (JobScheduler) activity.getSystemService(JOB_SCHEDULER_SERVICE);

        if (jobScheduler != null) {
            JobInfo.Builder jobBuilder = new JobInfo.Builder(BACKUP_JOB_ID,
                    new ComponentName(activity, BackupService.class))
                    .setRequiresCharging(configBean.isRequireCharging())
                    .setRequiredNetworkType(configBean.isRequireWiFi()
                            ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(5 * 60 * 1000) // will be clamped to 15 minutes
                    .setPersisted(true)
                    .setRequiresBatteryNotLow(true);
            JobInfo jobInfo = jobBuilder.build();
            jobScheduler.schedule(jobInfo);
            log.info("Scheduled backup job (charging only: {}, wifi only: {}",
                    configBean.isRequireCharging(), configBean.isRequireWiFi());
        }
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... params) {
                int count = 0;

                try {
                    ConfigBean config = new ConfigBean(PreferenceManager
                            .getDefaultSharedPreferences(BackupService.this));
                    String clientName = config.getClientName();
                    log.debug("Running backup with client name {} on server {}:{}",
                            clientName, config.getServerName(), config.getServerPort());
                    char[] password = config.getPassword().toCharArray();

                    Storage storage = RemoteStorage.create(config.getServerName(),
                            config.getServerPort(), password);
                    storage = EncryptedStorages.getEncryptedStorage(storage, password, false);
                    Filesystem filesystem = new Filesystem();
                    ClientActivities backup = new ClientActivities(clientName,
                            filesystem, storage, config.getServerName(), 120);
                    backup.setMaxNumberOfChunksToStore(100);

                    Source source = backup.selectSource(false);

                    if (source == null) {
                        String sourceName = clientName + "-externalStorage";
                        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
                        storage.addSource(clientName, sourceName, path);
                        storage.setSourceSyncAttributes(clientName, sourceName, true, true);
                        storage.setSourceIgnoredPatterns(clientName, sourceName, "/Android;.thumbnails");
                        log.info("Created source {} with path {} and synchronization active",
                                sourceName, path);
                    } else {
                        count += backup.sendFiles(source);

                        if (source.isSyncTarget()) {
                            String[] filePaths = Stream.of(backup.syncDeletions(source, false),
                                    backup.syncAdditions(source, false))
                                    .flatMap(List::stream)
                                    .map(f -> f.getPath(source.getRootPath()).toString())
                                    .distinct()
                                    .toArray(String[]::new);
                            count += filePaths.length;

                            // does not work for deletions
                            if (filePaths.length > 0) {
                                MediaScannerConnection.scanFile(BackupService.this.getApplicationContext(),
                                        filePaths, null, null);
                            }
                        }

                        count += backup.sendHashes(FileStatus.CURRENT);
                        count += backup.uploadChunks(FileStatus.CURRENT);

                        long today = System.currentTimeMillis() / DAY;
                        long lastDay = config.getLastTrashCollectionDay();

                        if (today > lastDay) {
                            backup.collectTrash(source);
                            config.setLastTrashCollectionDay(today);
                            log.info("Files older than 3 days have been removed from trash");
                        }
                    }
                } catch (Throwable t) {
                    log.error(null, t);
                    count = -1;
                } finally {
                    jobFinished(jobParameters, false);
                }

                log.debug("Backup completed with result " + count);
                return count;
            }
        }.execute();

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        log.info("Backup job stopped");
        return true;
    }
}
