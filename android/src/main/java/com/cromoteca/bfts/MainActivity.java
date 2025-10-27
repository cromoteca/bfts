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
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.cromoteca.bfts.model.Stats;
import com.cromoteca.bfts.storage.EncryptedStorages;
import com.cromoteca.bfts.storage.EncryptionType;
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
    private static final int MANAGE_STORAGE_PERMISSION_REQUEST = 2;
    Logger log = LoggerFactory.getLogger(MainActivity.class);

    private ForegroundBackupService backupService;
    private Button toggleButton;
    private boolean bound;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            log.debug("Connecting to service");
            backupService = ((ForegroundBackupService.LocalBinder) iBinder).getInstance();
            bound = true;
            updateToggleButton();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            log.debug("Disconnecting from service");
            backupService = null;
            bound = false;
            updateToggleButton();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ensureStorageAccess(false);
        ensureBatteryOptimizationExemption(false);

        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(e -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
        toggleButton = findViewById(R.id.toggleButton);
        toggleButton.setOnClickListener(e -> {
            ConfigBean config = new ConfigBean(PreferenceManager
                    .getDefaultSharedPreferences(MainActivity.this));
            boolean active = config.isServiceActive();
            if (active) {
                stopBackupService();
            } else {
                startBackupService();
            }
            updateToggleButton();
        });
        updateToggleButton();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent serviceIntent = new Intent(this, ForegroundBackupService.class);
        bindService(serviceIntent, mConnection, 0);
        updateToggleButton();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            unbindService(mConnection);
            bound = false;
        }
        backupService = null;
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onResume() {
        super.onResume();

        ensureStorageAccess(false);
        ensureBatteryOptimizationExemption(false);
        updateToggleButton();

        TextView statsText = findViewById(R.id.statsText);
        statsText.setText("Waiting for server status...");

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String status;

                try {
                    ConfigBean config = new ConfigBean(PreferenceManager
                            .getDefaultSharedPreferences(MainActivity.this));
                    char[] transmissionPassword = config.getTransmissionPassword().toCharArray();
                    if (transmissionPassword.length == 0) {
                        throw new IllegalStateException("Transmission password not configured");
                    }
                    Storage storage = RemoteStorage.create(config.getServerName(),
                            config.getServerPort(), transmissionPassword);

                    EncryptionType encryptionType = config.getEncryptionType();
                    switch (encryptionType) {
                        case DATA:
                        case FULL:
                            char[] dataPassword = config.getDataPassword().toCharArray();
                            if (dataPassword.length == 0) {
                                throw new IllegalStateException("Data encryption password not configured");
                            }
                            boolean encryptStrings = encryptionType == EncryptionType.FULL;
                            storage = EncryptedStorages.getEncryptedStorage(storage, dataPassword,
                                    encryptStrings);
                            break;
                        case NONE:
                            // Ignore NONE as per GUI behaviour
                            break;
                    }
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
                    status = t.getMessage() == null
                            ? "Backup server is unreachable at the moment"
                            : t.getMessage();
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
                boolean granted = grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                log.info("Storage access permission granted: " + granted);
                if (!granted) {
                    Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MANAGE_STORAGE_PERMISSION_REQUEST) {
            if (!ensureStorageAccess(false)) {
                Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show();
            } else {
                log.debug("Manage all files access granted");
            }
            updateToggleButton();
        }
    }

    private boolean ensureStorageAccess(boolean promptIfNeeded) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasAllFilesAccess = Environment.isExternalStorageManager();
            if (!hasAllFilesAccess && promptIfNeeded) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivityForResult(intent, MANAGE_STORAGE_PERMISSION_REQUEST);
                } catch (ActivityNotFoundException ex) {
                    log.warn("Failed to open app-specific all files access settings, using fallback", ex);
                    Intent fallbackIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(fallbackIntent, MANAGE_STORAGE_PERMISSION_REQUEST);
                }
            }
            return hasAllFilesAccess;
        } else {
            boolean hasWrite = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!hasWrite && promptIfNeeded) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        STORAGE_WRITE_PERMISSION_REQUEST);
            }
            return hasWrite;
        }
    }

    private boolean ensureBatteryOptimizationExemption(boolean promptIfNeeded) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return true;
        }

        boolean ignoring = powerManager.isIgnoringBatteryOptimizations(getPackageName());
        if (!ignoring && promptIfNeeded) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException ex) {
                log.warn("Failed to request battery optimization exemption directly", ex);
                Intent settingsIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                try {
                    startActivity(settingsIntent);
                } catch (ActivityNotFoundException | SecurityException inner) {
                    log.error("Cannot open battery optimization settings", inner);
                }
            }
        }

        return ignoring;
    }

    private void startBackupService() {
        if (!ensureStorageAccess(true)) {
            Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (!ensureBatteryOptimizationExemption(true)) {
            Toast.makeText(this, R.string.battery_permission_required, Toast.LENGTH_LONG).show();
            return;
        }
        log.debug("Starting foreground service");
        Intent serviceIntent = new Intent(this, ForegroundBackupService.class);
        startForegroundService(serviceIntent);
        if (!bound) {
            bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
        }
        log.debug("Foreground service started");
    }

    private void stopBackupService() {
        log.debug("Stopping foreground service");
        Intent serviceIntent = new Intent(this, ForegroundBackupService.class);
        stopService(serviceIntent);
        log.debug("Foreground service stopped");

        if (backupService != null) {
            log.debug("Stopping backup scheduler");
            backupService.stopScheduler();
        }
        if (bound) {
            unbindService(mConnection);
            bound = false;
        }
        backupService = null;

        ConfigBean config = new ConfigBean(PreferenceManager
                .getDefaultSharedPreferences(this));
        config.setServiceActive(false);
    }

    private void updateToggleButton() {
        if (toggleButton == null) {
            return;
        }
        ConfigBean config = new ConfigBean(PreferenceManager
                .getDefaultSharedPreferences(this));
        setToggleButtonText(config.isServiceActive());
    }

    private void setToggleButtonText(boolean active) {
        if (toggleButton != null) {
            toggleButton.setText(active ? R.string.stop_backup : R.string.start_backup);
        }
    }
}
