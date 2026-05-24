package com.codex.watchguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

public class GuardKeeperService extends Service {
    private static final String CHANNEL_ID = "watch_guard_keeper";
    private static final int NOTIFICATION_ID = 3902;
    private static final int ACCESSIBILITY_NOTIFICATION_ID = 3903;
    private static final long CHECK_INTERVAL_MS = 15_000L;
    private static final String EXTRA_REASON = "reason";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Boolean lastAccessibilityEnabled;
    private Boolean lastAccessibilityAlive;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            AppState.repairModeIfDesired(GuardKeeperService.this, "keeper_tick");
            boolean enabled = AppState.isGptAutomationAccessibilityEnabled(GuardKeeperService.this);
            boolean alive = AppState.isGptAutomationServiceAlive(GuardKeeperService.this);
            AppState.recordKeeperTick(GuardKeeperService.this, enabled, alive);
            if (lastAccessibilityEnabled == null || lastAccessibilityEnabled != enabled) {
                AppState.appendLog(GuardKeeperService.this,
                        "keeper gpt_accessibility " + (enabled ? "ON" : "OFF"));
                lastAccessibilityEnabled = enabled;
            }
            if (lastAccessibilityAlive == null || lastAccessibilityAlive != alive) {
                AppState.appendLog(GuardKeeperService.this,
                        "keeper gpt_accessibility_alive " + (alive ? "YES" : "NO"));
                lastAccessibilityAlive = alive;
            }
            if (enabled && !alive) {
                long taskRemovedAt = AppState.getKeeperTaskRemovedAt(GuardKeeperService.this);
                long heartbeatAt = AppState.getGptAccessibilityHeartbeatAt(GuardKeeperService.this);
                String reason = taskRemovedAt > 0L && taskRemovedAt >= heartbeatAt
                        ? "gpt_accessibility_stale_after_recents"
                        : "gpt_accessibility_stale";
                AppState.recordGptAccessibilityStale(GuardKeeperService.this, reason);
                notifyAccessibilityStale(reason);
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    static void start(Context context) {
        start(context, "start");
    }

    static void start(Context context, String reason) {
        AppState.recordKeeperStart(context, reason);
        Intent intent = new Intent(context, GuardKeeperService.class);
        intent.putExtra(EXTRA_REASON, reason);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, GuardKeeperService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String reason = intent == null ? "start_command_null" : intent.getStringExtra(EXTRA_REASON);
        AppState.recordKeeperStart(this, reason == null ? "start_command" : reason);
        ensureForeground();
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        AppState.recordKeeperTaskRemoved(this, "recents");
        if (AppState.isHomeTrapEnabled(this) || AppState.isModeUserDesiredEnabled(this)) {
            scheduleRestart("task_removed");
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (AppState.isHomeTrapEnabled(this) || AppState.isModeUserDesiredEnabled(this)) {
            scheduleRestart("destroy_while_enabled");
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensureForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WatchGuard guardian",
                    NotificationManager.IMPORTANCE_MIN
            );
            manager.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(this, MainActivity.class);
        openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openApp, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("WatchGuard armado")
                .setContentText("Vigilando automatizacion GPT y control del boton")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void notifyAccessibilityStale(String reason) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WatchGuard guardian",
                    NotificationManager.IMPORTANCE_MIN
            );
            manager.createNotificationChannel(channel);
        }
        Intent settings = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, settings, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Automatizacion GPT desconectada")
                .setContentText("Abre Accessibility para reconectar WatchGuard")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        if (manager != null) {
            manager.notify(ACCESSIBILITY_NOTIFICATION_ID, notification);
        }
        AppState.appendLog(this, "keeper_notify_accessibility_stale reason=" + reason);
    }

    private void scheduleRestart(String reason) {
        AppState.recordKeeperRestart(this, reason);
        Intent restart = new Intent(this, GuardKeeperService.class);
        restart.putExtra(EXTRA_REASON, reason);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getService(this, NOTIFICATION_ID, restart, flags);
        AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarm != null) {
            alarm.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1_000L,
                    pending
            );
        }
    }
}
