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
            char[] password = config.getPassword().toCharArray();

            Storage storage = RemoteStorage.create(config.getServerName(),
                    config.getServerPort(), password);
            // Storage storage = RemoteStorage.create("10.0.2.2", 8715, password);
            // Storage storage = RemoteStorage.create("192.168.1.133", 8715, password);
            storage = EncryptedStorages.getEncryptedStorage(storage, password, false);
            Filesystem filesystem = new Filesystem();
            ClientActivities backup = new ClientActivities(clientName,
                    filesystem, storage, config.getServerName(), 120);
            backup.setMaxNumberOfChunksToStore(100);
            scheduler = new ClientScheduler(backup, MIN_PAUSE, MAX_PAUSE);
            scheduler.start();
        }
    }

    public void stopScheduler() {
        if (scheduler != null) {
            scheduler.stop();
            scheduler = null;
        }
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
}
