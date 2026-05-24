package com.codex.watchguard;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class OverlayInspector {
    private OverlayInspector() {
    }

    static List<Suspect> findSuspects(Context context) {
        try {
            return findSuspectsUnsafe(context);
        } catch (Throwable error) {
            AppState.appendLog(context, "overlay scan failed: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
            return new ArrayList<>();
        }
    }

    private static List<Suspect> findSuspectsUnsafe(Context context) {
        PackageManager pm = context.getPackageManager();
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        String homePackage = currentHomePackage(context);
        List<Suspect> suspects = new ArrayList<>();

        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        for (PackageInfo info : packages) {
            try {
                if (info == null || info.packageName == null || info.requestedPermissions == null) {
                    continue;
                }
                boolean requestsOverlay = false;
                for (String permission : info.requestedPermissions) {
                    if (android.Manifest.permission.SYSTEM_ALERT_WINDOW.equals(permission)) {
                        requestsOverlay = true;
                        break;
                    }
                }
                if (!requestsOverlay) {
                    continue;
                }

                ApplicationInfo appInfo = info.applicationInfo;
                if (appInfo == null) {
                    continue;
                }
                String label = String.valueOf(pm.getApplicationLabel(appInfo));
                int appOpMode = AppOpsManager.MODE_DEFAULT;
                if (appOps != null) {
                    try {
                        appOpMode = appOps.checkOpNoThrow(
                                AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                                appInfo.uid,
                                info.packageName
                        );
                    } catch (Throwable ignored) {
                        appOpMode = AppOpsManager.MODE_DEFAULT;
                    }
                }
                boolean systemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean currentHome = info.packageName.equals(homePackage);
                int risk = riskScore(label, info.packageName, currentHome, appOpMode, systemApp);
                suspects.add(new Suspect(
                        label,
                        info.packageName,
                        systemApp,
                        currentHome,
                        appOpMode,
                        modeName(appOpMode),
                        risk,
                        reason(label, info.packageName, currentHome, appOpMode)
                ));
            } catch (Throwable error) {
                AppState.appendLog(context, "overlay scan skipped package: "
                        + error.getClass().getSimpleName() + " " + error.getMessage());
            }
        }

        Collections.sort(suspects, new Comparator<Suspect>() {
            @Override
            public int compare(Suspect left, Suspect right) {
                int risk = right.riskScore - left.riskScore;
                if (risk != 0) {
                    return risk;
                }
                return left.label.compareToIgnoreCase(right.label);
            }
        });
        return suspects;
    }

    static JSONArray toJson(Context context) throws JSONException {
        JSONArray array = new JSONArray();
        try {
            for (Suspect suspect : findSuspects(context)) {
                array.put(suspect.toJson());
            }
        } catch (Throwable error) {
            JSONObject object = new JSONObject();
            object.put("scan_error", error.getClass().getSimpleName() + " " + error.getMessage());
            array.put(object);
        }
        return array;
    }

    static boolean hasResolvableActivity(Context context, Intent intent) {
        try {
            return intent.resolveActivity(context.getPackageManager()) != null;
        } catch (Throwable error) {
            AppState.appendLog(context, "resolve activity failed: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
            return false;
        }
    }

    private static String currentHomePackage(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        try {
            return context.getPackageManager().resolveActivity(intent, 0).activityInfo.packageName;
        } catch (Throwable error) {
            return "";
        }
    }

    private static int riskScore(
            String label,
            String packageName,
            boolean currentHome,
            int appOpMode,
            boolean systemApp
    ) {
        String haystack = (label + " " + packageName).toLowerCase(Locale.US);
        int score = 10;
        if (appOpMode == AppOpsManager.MODE_ALLOWED || appOpMode == AppOpsManager.MODE_DEFAULT) {
            score += 40;
        }
        if (currentHome) {
            score += 30;
        }
        if (containsAny(haystack, "smart", "island", "float", "floating", "assist",
                "ball", "gesture", "navigation", "dock", "launcher", "home", "bubble")) {
            score += 35;
        }
        if (containsAny(haystack, "systemui", "settings", "android")) {
            score += 10;
        }
        if (systemApp) {
            score += 5;
        }
        return score;
    }

    private static String reason(String label, String packageName, boolean currentHome, int appOpMode) {
        String haystack = (label + " " + packageName).toLowerCase(Locale.US);
        if (currentHome) {
            return "launcher actual con permiso overlay";
        }
        if (containsAny(haystack, "smart", "island")) {
            return "nombre coincide con Smart Island";
        }
        if (containsAny(haystack, "float", "floating", "assist", "ball", "dock", "bubble")) {
            return "parece burbuja/ventana flotante";
        }
        if (containsAny(haystack, "gesture", "navigation")) {
            return "parece app de gestos/navegacion";
        }
        if (appOpMode == AppOpsManager.MODE_ALLOWED) {
            return "overlay permitido";
        }
        return "solicita permiso overlay";
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String modeName(int mode) {
        if (mode == AppOpsManager.MODE_ALLOWED) {
            return "allowed";
        }
        if (mode == AppOpsManager.MODE_IGNORED) {
            return "ignored";
        }
        if (mode == AppOpsManager.MODE_ERRORED) {
            return "errored";
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return "default";
        }
        return String.valueOf(mode);
    }

    static final class Suspect {
        final String label;
        final String packageName;
        final boolean systemApp;
        final boolean currentHome;
        final int appOpMode;
        final String appOpModeName;
        final int riskScore;
        final String reason;

        Suspect(
                String label,
                String packageName,
                boolean systemApp,
                boolean currentHome,
                int appOpMode,
                String appOpModeName,
                int riskScore,
                String reason
        ) {
            this.label = label;
            this.packageName = packageName;
            this.systemApp = systemApp;
            this.currentHome = currentHome;
            this.appOpMode = appOpMode;
            this.appOpModeName = appOpModeName;
            this.riskScore = riskScore;
            this.reason = reason;
        }

        JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("label", label);
            object.put("package", packageName);
            object.put("system_app", systemApp);
            object.put("current_home", currentHome);
            object.put("app_op_mode", appOpModeName);
            object.put("risk_score", riskScore);
            object.put("reason", reason);
            return object;
        }
    }
}
