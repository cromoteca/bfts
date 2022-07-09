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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.widget.Button;
import android.widget.TextView;

import com.cromoteca.bfts.model.Stats;
import com.cromoteca.bfts.storage.EncryptedStorages;
import com.cromoteca.bfts.storage.RemoteStorage;
import com.cromoteca.bfts.storage.Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int STORAGE_WRITE_PERMISSION_REQUEST = 1;
    Logger log = LoggerFactory.getLogger(MainActivity.class);

    private ForegroundBackupService backupService;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            log.debug("Connecting to service");
            backupService = ((ForegroundBackupService.LocalBinder) iBinder).getInstance();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            log.debug("Disconnecting from service");
            if (backupService != null) {
                backupService.unbindService(this);
                backupService = null;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_WRITE_PERMISSION_REQUEST);
        }

        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(e -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(e -> {
            log.debug("Starting foreground service");
            Intent serviceIntent = new Intent(this, ForegroundBackupService.class);
            startForegroundService(serviceIntent);
            bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
            log.debug("Foreground service started");
        });

        Button stopButton = findViewById(R.id.stopButton);
        stopButton.setOnClickListener(e -> {
            log.debug("Stopping foreground service");
            Intent serviceIntent = new Intent(this, ForegroundBackupService.class);
            stopService(serviceIntent);
            log.debug("Foreground service stopped");

            if (backupService != null) {
                log.debug("Stopping backup scheduler");
                backupService.stopScheduler();
                unbindService(mConnection);
                backupService = null;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent serviceIntent = new Intent(this, ForegroundBackupService.class);
        bindService(serviceIntent, mConnection, 0);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mConnection);
        backupService = null;
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onResume() {
        super.onResume();

        TextView statsText = findViewById(R.id.statsText);
        statsText.setText("Waiting for server status...");

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String status;

                try {
                    ConfigBean config = new ConfigBean(PreferenceManager
                            .getDefaultSharedPreferences(MainActivity.this));
                    char[] password = config.getPassword().toCharArray();
                    Storage storage = RemoteStorage.create(config.getServerName(),
                            config.getServerPort(), password);
                    storage = EncryptedStorages.getEncryptedStorage(storage, password,false);
                    Stats stats = storage.getClientStats(config.getClientName());
                    DateFormat dateFormat = SimpleDateFormat.getDateTimeInstance(
                            SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM);
                    String time = stats.getLastUpdated() == 0 ? "never"
                            : dateFormat.format(new Date(stats.getLastUpdated()));
                    status = String.format(Locale.UK, "Last updated: %s\nFiles: %d\n"
                                    + "Files without hash: %d\nMissing file chunks: %d\n",
                            time, stats.getFiles(), stats.getFilesWithoutHash(),
                            stats.getMissingChunks());

                    Map<String, Long> lastUpdated = storage.getClientsLastUpdated();

                    for (String client : lastUpdated.keySet()) {
                        long num = lastUpdated.get(client);
                        String upd = num == 0 ? "never" : dateFormat.format(new Date(num));
                        status = String.format(Locale.UK, "%s\n%s: %s", status, client, upd);
                    }
                } catch (Throwable t) {
                    log.error(null, t);
                    status = "Backup server is unreachable at the moment";
                }

                return status;
            }

            @Override
            protected void onPostExecute(String status) {
                try {
                    statsText.setText(status);
                } catch (Throwable t) {
                    log.error(null, t);
                }
            }
        }.execute();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case STORAGE_WRITE_PERMISSION_REQUEST:
                log.info("Storage access permission granted: " +
                        (grantResults[0] == PackageManager.PERMISSION_GRANTED));
                break;
        }
    }
}
