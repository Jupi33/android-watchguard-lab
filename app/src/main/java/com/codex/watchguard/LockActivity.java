package com.codex.watchguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class LockActivity extends Activity {
    private static final String EXTRA_TIMEOUT_MS = "timeout_ms";
    private static final long DEFAULT_TIMEOUT_MS = 240_000L;
    private static final long MAX_TIMEOUT_MS = 240_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishLock = new Runnable() {
        @Override
        public void run() {
            AppState.appendLog(LockActivity.this, "activity lock safety timeout");
            finish();
        }
    };

    public static void start(Context context, long timeoutMs) {
        Intent intent = new Intent(context, LockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.putExtra(EXTRA_TIMEOUT_MS, timeoutMs);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        setImmersive();

        setContentView(buildContent());
        AppState.setLocked(this, true);
        AppState.appendLog(this, "activity lock ON; attempting screen pinning");

        try {
            startLockTask();
            AppState.appendLockModeResult(this, "pin_lock", "requested",
                    "startLockTask called; may require system confirmation");
        } catch (RuntimeException error) {
            AppState.appendLockModeResult(this, "pin_lock", "failed",
                    error.getClass().getSimpleName() + " " + error.getMessage());
            AppState.appendLog(this, "screen pinning unavailable: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }

        long timeoutMs = getIntent().getLongExtra(EXTRA_TIMEOUT_MS, DEFAULT_TIMEOUT_MS);
        handler.postDelayed(finishLock, clampTimeout(timeoutMs));
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(finishLock);
        try {
            stopLockTask();
        } catch (RuntimeException ignored) {
            // Normal when screen pinning was not accepted or not active.
        }
        AppState.setLocked(this, false);
        AppState.appendLockModeResult(this, "pin_lock", "stopped", "activity finished");
        AppState.appendLog(this, "activity lock OFF");
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersive();
        }
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClickable(true);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        TextView label = new TextView(this);
        label.setText(R.string.lock_label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(10);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0.65f);
        label.setPadding(dp(7), dp(3), dp(7), dp(3));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xAA000000);
        bg.setCornerRadius(dp(4));
        label.setBackground(bg);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(6);
        root.addView(label, params);
        return root;
    }

    private void setImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private long clampTimeout(long timeoutMs) {
        if (timeoutMs <= 0L) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.min(timeoutMs, MAX_TIMEOUT_MS);
    }
}
