package com.cromoteca.bfts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.cromoteca.bfts.client.ClientActivities;
import com.cromoteca.bfts.client.ClientScheduler;
import com.cromoteca.bfts.client.Filesystem;
import com.cromoteca.bfts.storage.EncryptedStorages;
import com.cromoteca.bfts.storage.EncryptionType;
import com.cromoteca.bfts.storage.RemoteStorage;
import com.cromoteca.bfts.storage.Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForegroundBackupService extends Service {
    private static final long DAY = 1000L * 60 * 60 * 24;
    private static final int MIN_PAUSE = 60 * 1000; // 1 minute
    private static final int MAX_PAUSE = 15 * 60 * 1000; // 15 minutes

    static Logger log = LoggerFactory.getLogger(ForegroundBackupService.class);

    private ClientScheduler scheduler;

    private LocalBinder mBinder = new LocalBinder();

    public ForegroundBackupService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundActivity();
        startScheduler();

        return START_REDELIVER_INTENT;
    }

    private void startScheduler() {
        if (scheduler == null)  {
            ConfigBean config = new ConfigBean(PreferenceManager
                    .getDefaultSharedPreferences(ForegroundBackupService.this));
            String clientName = config.getClientName();
            log.debug("Running backup with client name {} on server {}:{}",
                    clientName, config.getServerName(), config.getServerPort());
            char[] transmissionPassword = config.getTransmissionPassword().toCharArray();
            if (transmissionPassword.length == 0) {
                log.warn("Transmission password is empty; cannot start backup scheduler");
                return;
            }

            Storage storage = RemoteStorage.create(config.getServerName(),
                    config.getServerPort(), transmissionPassword);

            EncryptionType encryptionType = config.getEncryptionType();
            switch (encryptionType) {
                case DATA:
                case FULL:
                    char[] dataPassword = config.getDataPassword().toCharArray();
                    if (dataPassword.length == 0) {
                        log.warn("Data encryption password is empty for encryption type {}", encryptionType);
                        break;
                    }
                    boolean encryptStrings = encryptionType == EncryptionType.FULL;
                    storage = EncryptedStorages.getEncryptedStorage(storage, dataPassword, encryptStrings);
                    break;
                case NONE:
                    log.warn("Encryption type NONE is not supported on Android; defaulting to unencrypted storage");
                    break;
            }
            Filesystem filesystem = new Filesystem();
            ClientActivities backup = new ClientActivities(clientName,
                    filesystem, storage, config.getServerName(), 120);
            backup.setMaxNumberOfChunksToStore(100);
            scheduler = new ClientScheduler(backup, MIN_PAUSE, MAX_PAUSE);
            scheduler.start();
            config.setServiceActive(true);
        }
    }

    public void stopScheduler() {
        ConfigBean config = new ConfigBean(PreferenceManager
                .getDefaultSharedPreferences(ForegroundBackupService.this));
        if (scheduler != null) {
            scheduler.stop();
            scheduler = null;
        }
        config.setServiceActive(false);
    }

    private void startForegroundActivity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Backup Service";
            String description = "Notification channel for the persisting backup service";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("8715", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification =
                new Notification.Builder(this, "8715")
                        .setContentTitle("Backup running")
                        .setContentText("Backup runs in the background")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(pendingIntent)
                        .setTicker("BFTS Ticker")
                        .build();

        startForeground(1, notification);
    }

    public class LocalBinder extends Binder {
        public ForegroundBackupService getInstance() {
            return ForegroundBackupService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScheduler();
    }
}
