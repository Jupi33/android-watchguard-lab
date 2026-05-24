package com.codex.watchguard;

import android.app.Application;
import android.os.Process;

public class WatchGuardApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                AppState.recordCrash(WatchGuardApplication.this, thread, throwable);
                if (previous != null) {
                    previous.uncaughtException(thread, throwable);
                    return;
                }
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }
}
