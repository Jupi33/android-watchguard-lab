package com.codex.watchguard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class PermissionRescueActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        AppState.appendLog(this, "permission rescue opened");
    }

    private ScrollView buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(0xFF050505);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(18));
        scroll.addView(content);

        TextView title = text("Overlay Rescue", 21, Color.WHITE, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(title);

        TextView help = text(
                "Si un permiso no se puede confirmar, normalmente hay una app flotante encima. "
                        + "Abre los sospechosos, fuerza cierre o desactiva mostrar encima, "
                        + "vuelve y prueba el permiso.",
                12,
                0xFFDDECEF,
                false
        );
        help.setPadding(0, dp(8), 0, dp(8));
        content.addView(help);

        addButton(content, "Abrir overlays especiales", new Runnable() {
            @Override
            public void run() {
                openOverlayList();
            }
        }, true);

        content.addView(section("Sospechosos overlay"));
        List<OverlayInspector.Suspect> suspects;
        try {
            suspects = OverlayInspector.findSuspects(this);
        } catch (Throwable error) {
            AppState.appendLog(this, "permission rescue scan crashed: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
            content.addView(text(
                    "El escaneo fallo en este firmware. Usa Abrir overlays especiales y revisa Smart Island / apps flotantes manualmente.",
                    12,
                    0xFFFFC4C4,
                    false
            ));
            return scroll;
        }
        if (suspects.isEmpty()) {
            content.addView(text("No encontre apps que declaren SYSTEM_ALERT_WINDOW.", 12, 0xFFB8C0C7, false));
        }
        for (final OverlayInspector.Suspect suspect : suspects) {
            content.addView(suspectView(suspect));
        }
        return scroll;
    }

    private LinearLayout suspectView(final OverlayInspector.Suspect suspect) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(7), dp(8), dp(7));
        box.setBackgroundColor(suspect.riskScore >= 70 ? 0xFF322226 : 0xFF171B1E);

        TextView title = text(suspect.label, 14, Color.WHITE, true);
        box.addView(title);
        box.addView(text(
                suspect.packageName
                        + "\n" + suspect.reason
                        + " | overlay=" + suspect.appOpModeName
                        + " | riesgo=" + suspect.riskScore,
                10,
                0xFFC9D7DA,
                false
        ));
        addButton(box, "Abrir ajustes de app", new Runnable() {
            @Override
            public void run() {
                openAppDetails(suspect.packageName);
            }
        }, false);
        addButton(box, "Abrir permiso overlay", new Runnable() {
            @Override
            public void run() {
                openOverlaySettings(suspect.packageName);
            }
        }, false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(4), 0, dp(4));
        box.setLayoutParams(params);
        return box;
    }

    private void openAppDetails(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        startActivity(intent);
    }

    private void openOverlaySettings(String packageName) {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
        );
        if (!OverlayInspector.hasResolvableActivity(this, intent)) {
            openOverlayList();
            return;
        }
        startActivity(intent);
    }

    private void openOverlayList() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        if (!OverlayInspector.hasResolvableActivity(this, intent)) {
            toast("Este firmware no expone lista overlay directa");
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            return;
        }
        startActivity(intent);
    }

    private void addButton(LinearLayout content, String label, final Runnable action, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(primary ? 14 : 11);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(primary ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        button.setBackgroundColor(primary ? 0xFF0B8E8E : 0xFF146C72);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(primary ? 46 : 38)
        );
        params.setMargins(0, dp(3), 0, dp(3));
        content.addView(button, params);
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
