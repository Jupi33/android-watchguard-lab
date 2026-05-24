package com.codex.watchguard;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int REQUEST_CAMERA_PERMISSION = 3202;

    private TextView statusView;
    private TextView reportView;
    private TextView logView;
    private Button modeButton;
    private Button launcherButton;
    private String generatedReport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppState.repairModeIfDesired(this, "main_resume");
        if (AppState.isHomeTrapEnabled(this)) {
            GuardKeeperService.start(this, "main_resume");
        }
        refresh();
    }

    @Override
    protected void onPause() {
        AppState.recordControlUiPaused(this);
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean candidate = isButtonCandidate(event);
        AppState.appendKeyEvent(
                this,
                "ACTIVITY_" + (event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" : "UP"),
                KeyEvent.keyCodeToString(event.getKeyCode()),
                event.getKeyCode(),
                event.getScanCode(),
                event.getRepeatCount(),
                event.getDeviceId()
        );
        AppState.appendLog(this, "activity key "
                + KeyEvent.keyCodeToString(event.getKeyCode())
                + " scan=" + event.getScanCode()
                + " device=" + event.getDeviceId()
                + " candidate=" + candidate);
        if (candidate) {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
                AppState.appendLockModeResult(this, "activity_key", "ignored_in_control_ui",
                        KeyEvent.keyCodeToString(event.getKeyCode()));
                toast("Abre una app; ahi el boton congela");
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private ScrollView buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(0xFF050505);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(18));
        scroll.addView(content);

        TextView title = text("WatchGuard VP39", 22, Color.WHITE, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(title);

        TextView subtitle = text("Diagnostico sin USB", 13, 0xFFB8C0C7, true);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(subtitle);

        TextView instructions = text(
                "1. Activa modo boton.\n"
                        + "2. Elige WatchGuard como launcher/Home si Android lo pide.\n"
                        + "3. Abre cualquier app: el boton congela y el siguiente toque del boton descongela.\n"
                        + "En camara nativa: el boton abre camara WatchGuard; dentro de ella, el boton toma foto.\n"
                        + "Doble boton en camara WatchGuard envia las fotos de la sesion a GPT.\n"
                        + "Automatizacion GPT usa Accessibility solo para adjuntar/enviar en ChatGPT; Android puede apagarla por bateria/OEM.\n"
                        + "Para volver al menu normal: Desactivar y luego Restaurar Launcher.",
                12,
                0xFFE1ECEF,
                false
        );
        instructions.setPadding(0, dp(8), 0, dp(8));
        content.addView(instructions);

        statusView = text("", 13, 0xFFE8F6F6, false);
        statusView.setPadding(0, dp(10), 0, dp(6));
        content.addView(statusView);

        content.addView(section("1. Modo boton"));
        modeButton = addButton(content, "Activar boton", true, new Runnable() {
            @Override
            public void run() {
                handleModeButton();
            }
        });
        launcherButton = addButton(content, "Establecer Launcher", new Runnable() {
            @Override
            public void run() {
                openHomeSettings();
            }
        });

        content.addView(section("2. Permisos"));
        addButton(content, "Permiso uso de apps", new Runnable() {
            @Override
            public void run() {
                startActivity(ForegroundResolver.usageAccessSettingsIntent());
            }
        });
        addButton(content, "Permiso overlay", new Runnable() {
            @Override
            public void run() {
                openOverlaySettings();
            }
        });
        addButton(content, "Permiso camara", new Runnable() {
            @Override
            public void run() {
                requestCameraPermission();
            }
        });
        addButton(content, "Permiso pantalla", new Runnable() {
            @Override
            public void run() {
                openWriteSettings();
            }
        });
        addButton(content, "Teclado seguro", new Runnable() {
            @Override
            public void run() {
                configureSafeKeyboard();
            }
        });
        addButton(content, "Automatizacion GPT", new Runnable() {
            @Override
            public void run() {
                openGptAutomationSettings();
            }
        });
        addButton(content, "Bateria sin restricciones", new Runnable() {
            @Override
            public void run() {
                openBatterySettings();
            }
        });

        content.addView(section("3. Diagnostico"));
        addButton(content, "Generar reporte", new Runnable() {
            @Override
            public void run() {
                generateReport();
            }
        });
        addButton(content, "Copiar reporte", new Runnable() {
            @Override
            public void run() {
                copyReport();
            }
        });

        content.addView(section("Reporte"));
        reportView = text("", 10, 0xFFDCE7EA, false);
        reportView.setTypeface(Typeface.MONOSPACE);
        content.addView(reportView);

        content.addView(section("Log"));
        logView = text("", 10, 0xFFC8D6D8, false);
        logView.setTypeface(Typeface.MONOSPACE);
        content.addView(logView);

        return scroll;
    }

    private void refresh() {
        AppState.observeSafeImeDefault(this);
        String status = "Overlay: " + yesNo(Settings.canDrawOverlays(this))
                + "\nUso de apps: " + yesNo(ForegroundResolver.hasUsageAccess(this))
                + "\nCamara propia: " + yesNo(AppState.hasCameraPermission(this))
                + "\nFotos DCIM: " + yesNo(AppState.hasPublicPhotoPermission(this))
                + "\nPermiso pantalla: " + yesNo(AppState.canWriteSystemSettings(this))
                + "\nTeclado seguro: " + safeKeyboardText()
                + "\nPuerta teclado: " + keyboardGateText()
                + "\nAutomatizacion GPT: " + gptAutomationText()
                + "\nBateria: " + batteryText()
                + "\nIME silencioso: " + silentImeText()
                + "\nModo boton: " + (AppState.isHomeTrapEnabled(this) ? "ACTIVO" : "apagado")
                + "\nListo para activar: " + activationReadinessText()
                + "\nLauncher WatchGuard: " + yesNo(ForegroundResolver.isWatchGuardDefaultHome(this))
                + "\nPantalla permanente: " + screenTimeoutText()
                + "\nModo expira: " + modeExpiryText()
                + "\nCiclo actual: " + lockedCycleText()
                + "\nLauncher normal: " + nullText(ForegroundResolver.externalHomePackageName(this))
                + "\nRescates HOME: " + AppState.getHomeStormRescues(this)
                + "\nHOME invocaciones: " + AppState.getHomeTrapInvocations(this)
                + "\nHOME ignoradas: " + AppState.getHomeTrapIgnored(this)
                + "\nBloqueo tactil activo: " + yesNo(AppState.isLocked(this))
                + "\n\nActivo: boton congela/descongela. Activar boton queda gris hasta que todo este listo.";
        statusView.setText(status);
        refreshActionButtons();
        if (generatedReport.length() == 0) {
            reportView.setText(R.string.report_placeholder);
        } else {
            reportView.setText(generatedReport);
        }
        logView.setText(AppState.getLog(this));
    }

    private Button addButton(LinearLayout content, String label, final Runnable action) {
        return addButton(content, label, false, action);
    }

    private Button addButton(LinearLayout content, String label, boolean primary, final Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(primary ? 14 : 12);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(primary ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        button.setBackgroundColor(primary ? 0xFF0B8E8E : 0xFF146C72);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setOnClickListener(v -> {
            action.run();
            refresh();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(primary ? 48 : 42)
        );
        params.setMargins(0, dp(3), 0, dp(3));
        content.addView(button, params);
        return button;
    }

    private TextView section(String label) {
        TextView view = text(label, 14, 0xFF78E0E0, true);
        view.setPadding(0, dp(12), 0, dp(4));
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.05f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void openWriteSettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void requestCameraPermission() {
        ArrayList<String> missing = new ArrayList<>();
        if (!AppState.hasCameraPermission(this)) {
            missing.add(Manifest.permission.CAMERA);
        }
        if (!AppState.hasPublicPhotoPermission(this)) {
            missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (missing.isEmpty()) {
            toast("Camara y fotos DCIM OK");
            return;
        }
        requestPermissions(missing.toArray(new String[0]), REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA_PERMISSION) {
            return;
        }
        if (AppState.hasCameraPermission(this) && AppState.hasPublicPhotoPermission(this)) {
            toast("Camara y fotos DCIM OK");
            AppState.appendLog(this, "main camera/storage permission granted");
        } else {
            toast("Falta permiso camara o fotos");
            AppState.recordWatchCameraError(this, "main camera/storage permission missing");
        }
        refresh();
    }

    private void configureSafeKeyboard() {
        if (!AppState.isWatchKeyboardEnabled(this)) {
            toast("Activa WatchGuard teclado seguro");
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            return;
        }
        if (AppState.isWatchKeyboardSelected(this)) {
            toast("Teclado seguro activo");
            return;
        }
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            toast("Elige WatchGuard teclado seguro");
            manager.showInputMethodPicker();
        } else {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        }
    }

    private void handleModeButton() {
        if (AppState.isHomeTrapEnabled(this)) {
            disableHomeTrap();
        } else {
            enableHomeTrap();
        }
    }

    private void refreshActionButtons() {
        boolean active = AppState.isHomeTrapEnabled(this);
        boolean ready = activationReady();
        if (modeButton != null) {
            modeButton.setText(active ? "Desactivar" : "Activar boton");
            styleActionButton(modeButton, true, active || ready);
        }
        if (launcherButton != null) {
            launcherButton.setText(ForegroundResolver.isWatchGuardDefaultHome(this)
                    ? "Restaurar Launcher"
                    : "Establecer Launcher");
            styleActionButton(launcherButton, false, true);
        }
    }

    private void styleActionButton(Button button, boolean primary, boolean enabled) {
        button.setEnabled(enabled);
        button.setTextColor(enabled ? Color.WHITE : 0xFFB7C0C2);
        button.setBackgroundColor(enabled
                ? (primary ? 0xFF0B8E8E : 0xFF146C72)
                : 0xFF4D5558);
    }

    private boolean activationReady() {
        return Settings.canDrawOverlays(this)
                && ForegroundResolver.hasUsageAccess(this)
                && AppState.hasCameraPermission(this)
                && AppState.hasPublicPhotoPermission(this)
                && AppState.canWriteSystemSettings(this)
                && AppState.isWatchKeyboardSelected(this)
                && AppState.isGptAutomationServiceReady(this)
                && isBatteryUnrestricted()
                && ForegroundResolver.isWatchGuardDefaultHome(this);
    }

    private String activationReadinessText() {
        String missing = activationMissingText();
        return missing.length() == 0 ? "OK" : "FALTA - " + missing;
    }

    private String activationMissingText() {
        ArrayList<String> missing = new ArrayList<>();
        addMissing(missing, Settings.canDrawOverlays(this), "overlay");
        addMissing(missing, ForegroundResolver.hasUsageAccess(this), "uso apps");
        addMissing(missing, AppState.hasCameraPermission(this), "camara");
        addMissing(missing, AppState.hasPublicPhotoPermission(this), "fotos");
        addMissing(missing, AppState.canWriteSystemSettings(this), "pantalla");
        addMissing(missing, AppState.isWatchKeyboardSelected(this), "teclado");
        addMissing(missing, AppState.isGptAutomationServiceReady(this), "automatizacion GPT viva");
        addMissing(missing, isBatteryUnrestricted(), "bateria");
        addMissing(missing, ForegroundResolver.isWatchGuardDefaultHome(this), "launcher");
        StringBuilder builder = new StringBuilder();
        for (String value : missing) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private void addMissing(ArrayList<String> missing, boolean ready, String label) {
        if (!ready) {
            missing.add(label);
        }
    }

    private void enableHomeTrap() {
        if (!activationReady()) {
            toast("Falta: " + activationMissingText());
            refresh();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Primero activa Overlay");
            openOverlaySettings();
            return;
        }
        if (!ForegroundResolver.hasUsageAccess(this)) {
            toast("Primero activa Uso de apps");
            startActivity(ForegroundResolver.usageAccessSettingsIntent());
            return;
        }
        AppState.setModeUserDesiredEnabled(this, true, "enable_home_trap");
        AppState.resetHomeTrapRuntime(this, "enable_home_trap");
        AppState.clearHomeTrapLegacyTargets(this);
        TouchBlockerService.unlock(this);
        AppState.setHomeTrapEnabled(this, true);
        AppState.setHomeTrapSessionActive(this, false);
        AppState.markHomeTrapArmed(this);
        AppState.trySilentRestoreSafeImeDefault(this, "enable_home_trap", 0L);
        AppState.setStayAwakeEnabled(this, true);
        AppState.enableIndefiniteScreenTimeout(this, "enable_home_trap");
        StayAwakeService.start(this);
        GuardKeeperService.start(this, "enable_home_trap");
        SafeImeGateService.start(this);
        if (!AppState.isHomeTrapEnabled(this) || !AppState.isStayAwakeEnabled(this)) {
            AppState.recordModeRequiresManualRearm(this, "enable_verify_failed");
            AppState.repairModeIfDesired(this, "enable_verify_failed");
        }
        toast("Trampa HOME activa");
    }

    private void disableHomeTrap() {
        AppState.setModeUserDesiredEnabled(this, false, "disable_home_trap");
        AppState.setHomeTrapEnabled(this, false);
        AppState.setHomeTrapSessionActive(this, false);
        AppState.resetHomeTrapRuntime(this, "disable_home_trap");
        AppState.clearHomeTrapLegacyTargets(this);
        AppState.setStayAwakeEnabled(this, false);
        AppState.restoreScreenTimeout(this, "disable_home_trap");
        StayAwakeService.stop(this);
        GuardKeeperService.stop(this);
        SafeImeGateService.stop(this);
        TouchBlockerService.unlock(this);
        toast("Modo boton desactivado");
        refresh();
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (ActivityNotFoundException error) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(Intent.createChooser(intent, "Elegir launcher"));
            } catch (ActivityNotFoundException ignored) {
                toast("No encontre ajustes de launcher");
            }
        }
    }

    private void openGptAutomationSettings() {
        toast("Activa solo WatchGuard automatizacion GPT");
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (RuntimeException error) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (RuntimeException ignored) {
                toast("No encontre ajustes de bateria");
            }
        }
    }

    private void generateReport() {
        generatedReport = fullReport();
        toast("Reporte generado");
    }

    private void copyReport() {
        String report = ensureReport();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("watchguard-report", report));
            toast("Reporte copiado");
        }
    }

    private String fullReport() {
        return DeviceReport.build(this);
    }

    private String ensureReport() {
        if (generatedReport.length() == 0) {
            generatedReport = fullReport();
        }
        return generatedReport;
    }

    private boolean isButtonCandidate(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_SLEEP
                || keyCode == KeyEvent.KEYCODE_WAKEUP
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_APP_SWITCH
                || keyCode == 264) {
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_Z) {
            return false;
        }
        return event.getScanCode() > 0 || event.getDeviceId() > 0;
    }

    private String yesNo(boolean value) {
        return value ? "OK" : "FALTA";
    }

    private String nullText(String value) {
        return value == null || value.length() == 0 ? "no detectado" : value;
    }

    private String safeKeyboardText() {
        if (AppState.isWatchKeyboardSelected(this)) {
            return "ACTIVO";
        }
        if (AppState.isWatchKeyboardEnabled(this)
                && AppState.hasWriteSecureSettingsPermission(this)) {
            return "se restaura solo";
        }
        if (AppState.isWatchKeyboardEnabled(this)) {
            return "no seleccionado - toca Teclado seguro";
        }
        return "FALTA - toca Teclado seguro";
    }

    private String silentImeText() {
        if (!AppState.isWatchKeyboardEnabled(this)) {
            return "falta teclado";
        }
        if (AppState.hasWriteSecureSettingsPermission(this)) {
            return "listo ADB";
        }
        return "falta ADB";
    }

    private String keyboardGateText() {
        if (!AppState.isHomeTrapEnabled(this)) {
            return "apagada";
        }
        if (!Settings.canDrawOverlays(this)) {
            return "falta overlay";
        }
        if (!AppState.isWatchKeyboardSelected(this)) {
            return "falta teclado";
        }
        if (AppState.isSafeImeGateActive(this)) {
            String packageName = AppState.getSafeImeGatePackage(this);
            return "ON " + (packageName == null ? "" : packageName);
        }
        String reason = AppState.getSafeImeGateBlockReason(this);
        return reason == null ? "lista" : "OFF " + reason;
    }

    private String gptAutomationText() {
        if (AppState.isGptAutomationServiceReady(this)) {
            return "ACTIVA";
        }
        if (AppState.isGptAutomationAccessibilityEnabled(this)) {
            return "sin heartbeat - abre Accessibility";
        }
        return "opcional - toca Automatizacion GPT";
    }

    private String batteryText() {
        if (isBatteryUnrestricted()) {
            return "sin restricciones";
        }
        return "puede apagar Accessibility";
    }

    private boolean isBatteryUnrestricted() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        return power != null && power.isIgnoringBatteryOptimizations(getPackageName());
    }

    private String screenTimeoutText() {
        String service = AppState.isStayAwakeEnabled(this) ? "ACTIVA" : "apagada";
        if (AppState.isScreenTimeoutChanged(this)) {
            return service + " / sistema indefinido";
        }
        if (!AppState.canWriteSystemSettings(this)) {
            return service + " / falta Permiso pantalla";
        }
        return service + " / sistema normal";
    }

    private String ageText(long timestampMs) {
        if (timestampMs <= 0L) {
            return "sin tick";
        }
        long ageMs = Math.max(0L, System.currentTimeMillis() - timestampMs);
        long ageSeconds = ageMs / 1000L;
        if (ageSeconds < 60L) {
            return "OK hace " + ageSeconds + "s";
        }
        return "OK hace " + (ageSeconds / 60L) + "m";
    }

    private String ageUntilText(long timestampMs) {
        if (timestampMs <= 0L) {
            return "no activa";
        }
        long remainingMs = timestampMs - System.currentTimeMillis();
        if (remainingMs <= 0L) {
            return "vencida";
        }
        long remainingSeconds = remainingMs / 1000L;
        if (remainingSeconds < 60L) {
            return remainingSeconds + "s";
        }
        return (remainingSeconds / 60L) + "m";
    }

    private String modeExpiryText() {
        if (!AppState.isHomeTrapEnabled(this)) {
            return "no activo";
        }
        long expires = AppState.getHomeTrapExpires(this);
        if (expires <= 0L) {
            return "indefinido";
        }
        return ageUntilText(expires);
    }

    private String lockedCycleText() {
        if (AppState.isLockedCycleMenu(this)) {
            return "menu";
        }
        String target = AppState.getLockedCyclePackage(this);
        return target == null ? "ninguno" : target;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
