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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
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

public class MainActivity extends Activity {
    private static final int STORAGE_WRITE_PERMISSION_REQUEST = 1;
    Logger log = LoggerFactory.getLogger(MainActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_WRITE_PERMISSION_REQUEST);
        }

        BackupService.scheduleJob(this);

        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(e -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
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
