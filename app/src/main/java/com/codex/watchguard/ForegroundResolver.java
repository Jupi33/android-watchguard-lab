package com.codex.watchguard;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.provider.Settings;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ForegroundResolver {
    static final int SCREEN_APP = 1;
    static final int SCREEN_MENU = 2;
    static final int SCREEN_UNKNOWN = 3;

    private static final long LOOKBACK_MS = 10L * 60L * 1000L;
    private static final long USAGE_HISTORY_ONLY_MS = 60_000L;
    private static final long RAW_CONFLICT_RECENCY_MS = 8_000L;
    private static final long NAVIGATION_HOME_RECENCY_MS = 6_000L;
    private static final String PREFERRED_EXTERNAL_HOME = "com.dw.launcher";

    private ForegroundResolver() {
    }

    static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static Intent usageAccessSettingsIntent() {
        return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }

    static VisibleScreen resolveVisibleScreen(Context context) {
        TaskSignal taskSignal = readTaskSignal(context);
        UsageSignal usageSignal = readUsageSignal(context);

        String taskApp = taskSignal.usablePackage;
        String usageApp = usageSignal.usablePackage;
        String rawPackage = usageSignal.rawPackage;

        VisibleScreen chosen;
        if (taskApp != null && shouldUseTaskCandidate(context, taskSignal, usageSignal)) {
            chosen = new VisibleScreen(SCREEN_APP, taskApp, System.currentTimeMillis(), "tasks");
        } else if (usageApp != null && shouldUseUsageCandidate(context, usageSignal)) {
            chosen = new VisibleScreen(SCREEN_APP, usageApp, usageSignal.usableAt, "usage");
        } else if (rawPackage != null
                && isHomePackage(context, rawPackage)
                && System.currentTimeMillis() - usageSignal.rawAt < NAVIGATION_HOME_RECENCY_MS) {
            chosen = new VisibleScreen(SCREEN_MENU, null, usageSignal.rawAt, "usage_home");
        } else if (rawPackage != null
                && "navigation".equals(usageSignal.rawReason)
                && System.currentTimeMillis() - usageSignal.rawAt < NAVIGATION_HOME_RECENCY_MS) {
            chosen = new VisibleScreen(SCREEN_MENU, null, usageSignal.rawAt, "system_navigation");
        } else {
            chosen = new VisibleScreen(SCREEN_UNKNOWN, null, 0L, "none");
        }

        AppState.appendLog(context, "screen_candidates task_app=" + none(taskApp)
                + " usage_app=" + none(usageApp)
                + " raw=" + none(rawPackage)
                + " raw_reason=" + none(usageSignal.rawReason)
                + " chosen=" + chosen.typeName()
                + ":" + none(chosen.packageName)
                + " source=" + chosen.source);
        if (taskSignal.rejects.length() > 0) {
            AppState.appendLog(context, "task_rejects " + taskSignal.rejects);
        }

        if (chosen.type == SCREEN_APP) {
            AppState.recordVisibleAppCache(context, chosen.packageName, chosen.source);
            AppState.appendLog(context, "screen=APP package=" + chosen.packageName
                    + " source=" + chosen.source + " age=" + ageMs(chosen.eventTimeMs));
        } else if (chosen.type == SCREEN_MENU) {
            AppState.recordMenuSeenAfterCachedApp(context, chosen.source);
            if ("system_navigation".equals(chosen.source)) {
                AppState.appendLog(context, "screen_system_navigation package=" + rawPackage
                        + " source=" + chosen.source);
            } else {
                AppState.appendLog(context, "screen=MENU package=" + rawPackage
                        + " source=" + chosen.source);
            }
        } else {
            AppState.appendLog(context, "screen=UNKNOWN source=" + chosen.source);
        }
        return chosen;
    }

    static VisibleScreen resolveCameraScreen(Context context) {
        TaskSignal taskSignal = readTaskSignal(context);
        UsageSignal usageSignal = readUsageSignal(context);

        String taskApp = isCameraPackageName(taskSignal.usablePackage)
                ? taskSignal.usablePackage
                : null;
        String usageApp = isCameraPackageName(usageSignal.usablePackage)
                ? usageSignal.usablePackage
                : null;
        boolean rawCamera = isCameraPackageName(usageSignal.rawPackage)
                && "usable".equals(usageSignal.rawReason);

        VisibleScreen chosen;
        if (taskApp != null && rawCamera && shouldUseTaskCandidate(context, taskSignal, usageSignal)) {
            chosen = new VisibleScreen(SCREEN_APP, taskApp, System.currentTimeMillis(), "camera_tasks");
        } else if (usageApp != null && rawCamera) {
            chosen = new VisibleScreen(SCREEN_APP, usageApp, usageSignal.usableAt, "camera_usage");
        } else if (rawCamera) {
            chosen = new VisibleScreen(SCREEN_APP, usageSignal.rawPackage, usageSignal.rawAt, "camera_raw");
        } else {
            chosen = new VisibleScreen(SCREEN_UNKNOWN, null, 0L, "none");
        }

        AppState.appendLog(context, "camera_screen_candidates task_app=" + none(taskApp)
                + " usage_app=" + none(usageApp)
                + " raw=" + none(usageSignal.rawPackage)
                + " raw_reason=" + none(usageSignal.rawReason)
                + " chosen=" + chosen.typeName()
                + ":" + none(chosen.packageName)
                + " source=" + chosen.source);
        return chosen;
    }

    static String findVisiblePackage(Context context) {
        VisibleScreen screen = resolveVisibleScreen(context);
        return screen.type == SCREEN_APP ? screen.packageName : null;
    }

    static String findLastForegroundPackage(Context context) {
        UsageSignal signal = readUsageSignal(context);
        AppState.appendLog(context, "usage resolved previous app="
                + (signal.usablePackage == null ? "none" : signal.usablePackage));
        return signal.usablePackage;
    }

    static boolean latestForegroundIsHome(Context context) {
        VisibleScreen screen = resolveVisibleScreen(context);
        return screen.type == SCREEN_MENU;
    }

    static String findRecentUsablePackageSince(Context context, long sinceMs) {
        return readRestoreSignalSince(context, sinceMs).usablePackage;
    }

    static RestoreSignal readRestoreSignalSince(Context context, long sinceMs) {
        TaskSignal taskSignal = readTaskSignal(context);
        RestoreSignal signal = new RestoreSignal();
        signal.taskPackage = taskSignal.usablePackage;
        signal.taskSource = taskSignal.usableSource;
        if (!hasUsageAccess(context)) {
            AppState.appendLog(context, "restore_signal task=" + none(signal.taskPackage)
                    + " usage=none raw=none reason=no_usage_access");
            return signal;
        }

        UsageStatsManager usage = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usage == null) {
            AppState.appendLog(context, "restore_signal task=" + none(signal.taskPackage)
                    + " usage=none raw=none reason=no_usage_service");
            return signal;
        }

        long start = Math.max(0L, sinceMs);
        long now = System.currentTimeMillis();
        UsageEvents events = usage.queryEvents(start, now + 1_000L);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND) {
                continue;
            }
            String packageName = event.getPackageName();
            if (packageName != null && !packageName.equals(context.getPackageName())) {
                signal.rawPackage = packageName;
                signal.rawAt = event.getTimeStamp();
                signal.rawReason = candidateReason(context, packageName);
            }
            if ("usable".equals(candidateReason(context, packageName))) {
                signal.usablePackage = packageName;
                signal.usableAt = event.getTimeStamp();
            }
        }
        AppState.appendLog(context, "restore_signal task=" + none(signal.taskPackage)
                + " usage=" + none(signal.usablePackage)
                + " raw=" + none(signal.rawPackage)
                + " raw_reason=" + none(signal.rawReason)
                + " raw_age=" + ageMs(signal.rawAt));
        return signal;
    }

    static boolean canLaunch(Context context, String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return false;
        }
        PackageManager pm = context.getPackageManager();
        return pm.getLaunchIntentForPackage(packageName) != null;
    }

    static boolean launchPackage(Context context, String packageName) {
        if (!canLaunch(context, packageName)) {
            AppState.appendLog(context, "cannot launch target " + packageName);
            return false;
        }
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            context.startActivity(intent);
            AppState.appendLog(context, "launched target " + packageName);
            return true;
        } catch (RuntimeException error) {
            AppState.appendLog(context, "launch target failed " + packageName + ": "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
            return false;
        }
    }

    static boolean restorePackage(Context context, String packageName) {
        if (moveTaskToFront(context, packageName)) {
            return true;
        }
        AppState.appendLog(context, "restore fallback launch target " + packageName);
        return launchPackage(context, packageName);
    }

    static boolean moveExistingTaskToFront(Context context, String packageName) {
        return moveTaskToFront(context, packageName);
    }

    static String externalHomePackageName(Context context) {
        ResolveInfo info = findExternalHome(context);
        if (info != null && info.activityInfo != null) {
            return info.activityInfo.packageName;
        }
        return null;
    }

    static String defaultHomePackageName(Context context) {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = context.getPackageManager().resolveActivity(
                home,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        if (info == null || info.activityInfo == null) {
            return null;
        }
        return info.activityInfo.packageName;
    }

    static boolean isWatchGuardDefaultHome(Context context) {
        String packageName = defaultHomePackageName(context);
        return context.getPackageName().equals(packageName);
    }

    static boolean launchExternalHome(Context context) {
        ResolveInfo info = findExternalHome(context);
        if (info == null || info.activityInfo == null) {
            AppState.recordHomeDelegate(context, null);
            AppState.appendLog(context, "home_delegate unavailable");
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setComponent(new ComponentName(
                info.activityInfo.packageName,
                info.activityInfo.name
        ));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            context.startActivity(intent);
            AppState.recordHomeDelegate(context, info.activityInfo.packageName);
            AppState.appendLog(context, "home_delegate activity="
                    + info.activityInfo.packageName + "/" + info.activityInfo.name);
            return true;
        } catch (RuntimeException error) {
            AppState.recordHomeDelegate(context, info.activityInfo.packageName);
            AppState.appendLog(context, "home_delegate failed "
                    + info.activityInfo.packageName + ": "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
            return false;
        }
    }

    private static boolean shouldUseUsageCandidate(Context context, UsageSignal signal) {
        long now = System.currentTimeMillis();
        long usageAge = now - signal.usableAt;
        boolean rawSameUsable = signal.rawPackage != null
                && signal.rawPackage.equals(signal.usablePackage)
                && "usable".equals(signal.rawReason);
        boolean rawConflict = signal.rawPackage != null
                && !signal.rawPackage.equals(signal.usablePackage)
                && now - signal.rawAt >= 0L
                && now - signal.rawAt <= RAW_CONFLICT_RECENCY_MS
                && !isHomePackage(context, signal.rawPackage)
                && !"system".equals(signal.rawReason)
                && !"navigation".equals(signal.rawReason)
                && !"self".equals(signal.rawReason);

        if (signal.rawPackage != null
                && "navigation".equals(signal.rawReason)
                && now - signal.rawAt >= 0L
                && now - signal.rawAt <= RAW_CONFLICT_RECENCY_MS) {
            AppState.appendLog(context, "ignored_stale_app usage=" + signal.usablePackage
                    + " raw_navigation=" + signal.rawPackage
                    + " usage_age=" + usageAge);
            return false;
        }

        if (rawConflict) {
            AppState.appendLog(context, "ignored_stale_app usage=" + signal.usablePackage
                    + " raw_recent=" + signal.rawPackage
                    + " raw_reason=" + signal.rawReason
                    + " usage_age=" + usageAge);
            return false;
        }
        if (usageAge > USAGE_HISTORY_ONLY_MS && !rawSameUsable) {
            AppState.appendLog(context, "ignored_stale_app usage=" + signal.usablePackage
                    + " usage_age=" + usageAge
                    + " raw=" + none(signal.rawPackage)
                    + " raw_reason=" + none(signal.rawReason));
            return false;
        }
        return true;
    }

    private static boolean shouldUseTaskCandidate(
            Context context,
            TaskSignal taskSignal,
            UsageSignal usageSignal
    ) {
        if (taskSignal.usablePackage == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean rawConflict = usageSignal.rawPackage != null
                && !usageSignal.rawPackage.equals(taskSignal.usablePackage)
                && now - usageSignal.rawAt >= 0L
                && now - usageSignal.rawAt <= RAW_CONFLICT_RECENCY_MS
                && !isHomePackage(context, usageSignal.rawPackage)
                && !"system".equals(usageSignal.rawReason)
                && !"navigation".equals(usageSignal.rawReason)
                && !"self".equals(usageSignal.rawReason);
        if (usageSignal.rawPackage != null
                && "navigation".equals(usageSignal.rawReason)
                && now - usageSignal.rawAt >= 0L
                && now - usageSignal.rawAt <= RAW_CONFLICT_RECENCY_MS) {
            AppState.appendLog(context, "ignored_stale_app task=" + taskSignal.usablePackage
                    + " raw_navigation=" + usageSignal.rawPackage);
            return false;
        }
        if (rawConflict && !"usable".equals(usageSignal.rawReason)) {
            AppState.appendLog(context, "ignored_stale_app task=" + taskSignal.usablePackage
                    + " raw_recent=" + usageSignal.rawPackage
                    + " raw_reason=" + usageSignal.rawReason);
            return false;
        }
        if (rawConflict && "usable".equals(usageSignal.rawReason)) {
            AppState.appendLog(context, "task_conflict_using_usage task=" + taskSignal.usablePackage
                    + " raw_recent=" + usageSignal.rawPackage);
            return false;
        }
        return true;
    }

    private static TaskSignal readTaskSignal(Context context) {
        TaskSignal signal = new TaskSignal();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return signal;
        }
        try {
            List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(8);
            if (tasks == null) {
                return signal;
            }
            int inspected = 0;
            for (ActivityManager.RunningTaskInfo task : tasks) {
                inspected++;
                if (acceptTaskCandidate(context, signal, packageNameOf(task.topActivity), "top")) {
                    break;
                }
                if (acceptTaskCandidate(context, signal, packageNameOf(task.baseActivity), "base")) {
                    break;
                }
                if (inspected >= 6) {
                    break;
                }
            }
        } catch (SecurityException error) {
            AppState.appendLog(context, "running tasks denied: " + error.getMessage());
        } catch (RuntimeException error) {
            AppState.appendLog(context, "running tasks failed: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
        return signal;
    }

    private static boolean moveTaskToFront(Context context, String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return false;
        }
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }
        try {
            List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(12);
            if (tasks == null) {
                return false;
            }
            for (ActivityManager.RunningTaskInfo task : tasks) {
                String topPackage = packageNameOf(task.topActivity);
                String basePackage = packageNameOf(task.baseActivity);
                if (packageName.equals(topPackage) || packageName.equals(basePackage)) {
                    activityManager.moveTaskToFront(
                            task.id,
                            ActivityManager.MOVE_TASK_NO_USER_ACTION
                    );
                    AppState.appendLog(context, "moved task to front target=" + packageName
                            + " task=" + task.id);
                    return true;
                }
            }
        } catch (SecurityException error) {
            AppState.appendLog(context, "move task denied " + packageName + ": "
                    + error.getMessage());
        } catch (RuntimeException error) {
            AppState.appendLog(context, "move task failed " + packageName + ": "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
        return false;
    }

    private static ResolveInfo findExternalHome(Context context) {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = context.getPackageManager().queryIntentActivities(home, 0);
        if (homes == null || homes.size() == 0) {
            return null;
        }

        ResolveInfo firstExternal = null;
        for (ResolveInfo info : homes) {
            if (info == null || info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(context.getPackageName())) {
                continue;
            }
            if (PREFERRED_EXTERNAL_HOME.equals(packageName)) {
                return info;
            }
            if (firstExternal == null) {
                firstExternal = info;
            }
        }
        return firstExternal;
    }

    private static boolean acceptTaskCandidate(
            Context context,
            TaskSignal signal,
            String packageName,
            String position
    ) {
        String reason = candidateReason(context, packageName);
        if ("usable".equals(reason)) {
            signal.usablePackage = packageName;
            signal.usableSource = position;
            return true;
        }
        if (packageName != null && packageName.length() > 0) {
            appendReject(signal, packageName + ":" + reason + ":" + position);
        }
        return false;
    }

    private static UsageSignal readUsageSignal(Context context) {
        UsageSignal signal = new UsageSignal();
        if (!hasUsageAccess(context)) {
            AppState.appendLog(context, "usage access missing; cannot resolve screen");
            return signal;
        }
        UsageStatsManager usage = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usage == null) {
            return signal;
        }

        long now = System.currentTimeMillis();
        UsageEvents events = usage.queryEvents(now - LOOKBACK_MS, now + 1_000L);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND) {
                continue;
            }
            String packageName = event.getPackageName();
            if (packageName != null && !packageName.equals(context.getPackageName())) {
                signal.rawPackage = packageName;
                signal.rawAt = event.getTimeStamp();
                signal.rawReason = candidateReason(context, packageName);
            }
            if ("usable".equals(candidateReason(context, packageName))) {
                signal.usablePackage = packageName;
                signal.usableAt = event.getTimeStamp();
            }
        }
        return signal;
    }

    private static String packageNameOf(ComponentName componentName) {
        return componentName == null ? null : componentName.getPackageName();
    }

    private static boolean isCameraPackageName(String packageName) {
        return "com.android.camera2".equals(packageName)
                || "com.android.camera".equals(packageName);
    }

    private static String candidateReason(Context context, String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return "empty";
        }
        if (packageName.equals(context.getPackageName())) {
            return "self";
        }
        if (isSystemNavigationPackage(packageName)) {
            return "navigation";
        }
        if (packageName.equals("android")
                || packageName.equals("com.android.systemui")
                || packageName.equals("com.google.android.inputmethod.latin")
                || packageName.equals("com.dw.smartisland")) {
            return "system";
        }
        if (isHomePackage(context, packageName)) {
            return "home";
        }
        if (!canLaunch(context, packageName)) {
            return "not_launchable";
        }
        return "usable";
    }

    private static boolean isHomePackage(Context context, String packageName) {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = context.getPackageManager().queryIntentActivities(home, 0);
        if (homes == null) {
            return false;
        }
        Set<String> homePackages = new HashSet<>();
        for (ResolveInfo info : homes) {
            if (info != null && info.activityInfo != null && info.activityInfo.packageName != null) {
                homePackages.add(info.activityInfo.packageName);
            }
        }
        return homePackages.contains(packageName);
    }

    private static boolean isSystemNavigationPackage(String packageName) {
        return "android.settings".equals(packageName)
                || "com.android.settings".equals(packageName)
                || "com.android.packageinstaller".equals(packageName)
                || "com.dw.recents".equals(packageName)
                || "com.dw.setting".equals(packageName)
                || "com.sprd.powersavemodelauncher".equals(packageName);
    }

    private static void appendReject(TaskSignal signal, String value) {
        if (signal.rejects.length() > 0) {
            signal.rejects.append(",");
        }
        signal.rejects.append(value);
    }

    private static String none(String value) {
        return value == null || value.length() == 0 ? "none" : value;
    }

    private static long ageMs(long timestampMs) {
        return timestampMs <= 0L ? -1L : Math.max(0L, System.currentTimeMillis() - timestampMs);
    }

    static final class VisibleScreen {
        final int type;
        final String packageName;
        final long eventTimeMs;
        final String source;

        VisibleScreen(int type, String packageName, long eventTimeMs, String source) {
            this.type = type;
            this.packageName = packageName;
            this.eventTimeMs = eventTimeMs;
            this.source = source;
        }

        String typeName() {
            if (type == SCREEN_APP) {
                return "APP";
            }
            if (type == SCREEN_MENU) {
                return "MENU";
            }
            return "UNKNOWN";
        }

        String describe() {
            return "screen=" + typeName()
                    + " package=" + none(packageName)
                    + " source=" + source
                    + " event_age=" + ageMs(eventTimeMs);
        }
    }

    static final class RestoreSignal {
        String taskPackage;
        String taskSource;
        String usablePackage;
        long usableAt;
        String rawPackage;
        long rawAt;
        String rawReason;

        boolean rawIsHome() {
            return "home".equals(rawReason);
        }

        String rawPackageLabel() {
            return none(rawPackage);
        }

        long rawAgeMs() {
            return ageMs(rawAt);
        }
    }

    private static final class TaskSignal {
        String usablePackage;
        String usableSource;
        final StringBuilder rejects = new StringBuilder();
    }

    private static final class UsageSignal {
        String usablePackage;
        long usableAt;
        String rawPackage;
        long rawAt;
        String rawReason;
    }
}
