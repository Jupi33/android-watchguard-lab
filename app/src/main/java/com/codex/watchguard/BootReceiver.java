package com.codex.watchguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            boolean desired = AppState.isModeUserDesiredEnabled(context);
            boolean active = AppState.isHomeTrapEnabled(context);
            if (desired || active) {
                AppState.recordBootReceiver(context, action, "restore_mode");
                if (!desired) {
                    AppState.setModeUserDesiredEnabled(
                            context,
                            true,
                            "boot_package_migrate_active"
                    );
                }
                AppState.appendLog(context, "boot/package receiver -> restore mode");
                AppState.resetHomeTrapRuntime(context, action);
                AppState.clearHomeTrapLegacyTargets(context);
                AppState.setHomeTrapEnabled(context, true);
                AppState.setStayAwakeEnabled(context, true);
                AppState.enableIndefiniteScreenTimeout(context, action);
                StayAwakeService.start(context);
                GuardKeeperService.start(context, "boot_package_keep_mode");
                SafeImeGateService.start(context);
                TouchBlockerService.unlock(context);
                return;
            }
            AppState.recordBootReceiver(context, action, "boot_package_noop_mode_off");
            AppState.appendLog(context, "boot/package receiver -> noop mode off");
        }
    }
}
