package com.codex.watchguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class StayAwakeService extends Service {
    private static final String CHANNEL_ID = "watch_guard_awake";
    private static final int NOTIFICATION_ID = 3904;
    private static final String ACTION_START = "com.codex.watchguard.action.AWAKE_START";
    private static final String ACTION_STOP = "com.codex.watchguard.action.AWAKE_STOP";

    private PowerManager.WakeLock wakeLock;

    static void start(Context context) {
        Intent intent = new Intent(context, StayAwakeService.class);
        intent.setAction(ACTION_START);
        startServiceCompat(context, intent);
    }

    static void stop(Context context) {
        Intent intent = new Intent(context, StayAwakeService.class);
        intent.setAction(ACTION_STOP);
        startServiceCompat(context, intent);
    }

    private static void startServiceCompat(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException error) {
            AppState.appendLog(context, "stay awake service start failed: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_STOP : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            ensureForeground();
            releaseWakeLock();
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureForeground();
        acquireWakeLock();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power == null) {
            return;
        }
        wakeLock = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                "WatchGuard:stayAwake"
        );
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
        AppState.appendLog(this, "stay awake wakelock acquired");
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            AppState.appendLog(this, "stay awake wakelock released");
        }
        wakeLock = null;
    }

    private void ensureForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WatchGuard pantalla",
                    NotificationManager.IMPORTANCE_MIN
            );
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("WatchGuard pantalla activa")
                .setContentText("La pantalla se mantiene encendida mientras el modo boton esta activo")
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }
}
