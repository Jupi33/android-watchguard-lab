package com.codex.watchguard;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.ExtractEditText;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethod;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Locale;

public class TinyInputMethodService extends InputMethodService {
    private static final long MANUAL_CLICK_WINDOW_MS = 1_200L;
    private static final long MANUAL_SHOW_SETTLE_MS = 1_200L;
    private static final long HIDE_SELF_DELAY_MS = 80L;
    private static final long USER_HIDE_NAV_SUPPRESS_MS = 900L;
    private static final long USER_HIDE_DOUBLE_TAP_ACCEPT_MIN_MS = 120L;
    private static final long USER_HIDE_DOUBLE_TAP_ACCEPT_MAX_MS = 800L;
    private static final long DEL_REPEAT_INITIAL_MS = 350L;
    private static final long DEL_REPEAT_MS = 75L;
    private static final long NAVIGATION_SUPPRESS_MS = 6_000L;
    private static final long START_INPUT_CHURN_WINDOW_MS = 1_800L;
    private static final int START_INPUT_CHURN_THRESHOLD = 3;
    private static final int PREVIEW_MAX_CHARS = 42;

    private static WeakReference<TinyInputMethodService> activeService =
            new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable deleteRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!deleteRepeatActive) {
                return;
            }
            deleteOnce();
            handler.postDelayed(this, DEL_REPEAT_MS);
        }
    };

    private boolean symbols;
    private boolean shifted;
    private boolean expanded;
    private String currentFieldKey = "";
    private String manualClickFieldKey = "";
    private String manualShowFieldKey = "";
    private boolean manualClickAllowsAnyField;
    private long manualClickUntilUptime;
    private long manualShowSettleUntilUptime;
    private long lastHiddenAtUptime;
    private long userHiddenAtUptime;
    private long lastSuppressedExplicitAtUptime;
    private long navigationSuppressUntilUptime;
    private long lastStartInputAtUptime;
    private int startInputChurnCount;
    private int currentInputType = InputType.TYPE_NULL;
    private int currentFieldId = -1;
    private String currentPackageName = "";
    private boolean deleteRepeatActive;
    private String userHiddenFieldKey = "";
    private String lastSuppressedExplicitFieldKey = "";
    private String lastNavigationFieldKey = "";
    private FrameLayout inputRoot;
    private TextView previewText;

    static void notifyRealTapFromAccessibility(String packageName, String field) {
        TinyInputMethodService service = activeService.get();
        if (service == null) {
            return;
        }
        service.handler.post(new Runnable() {
            @Override
            public void run() {
                service.handleAccessibilityTap(packageName, field);
            }
        });
    }

    static boolean canShowForGate(String packageName) {
        TinyInputMethodService service = activeService.get();
        return service != null && service.canAcceptGate(packageName);
    }

    static void notifyGateTap(final String packageName) {
        final TinyInputMethodService service = activeService.get();
        if (service == null) {
            return;
        }
        service.handler.post(new Runnable() {
            @Override
            public void run() {
                service.handleGateTap(packageName);
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeService = new WeakReference<>(this);
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        activeService = new WeakReference<>(this);
        updateCurrentInput(info);
        recordStartInputChurn(currentFieldKey);
        AppState.recordSafeImeStartInput(this, currentFieldKey, restarting);
        if (isStrictRealTextInput(info)) {
            AppState.recordSafeImeLastStrictInput(
                    this,
                    currentPackageName,
                    currentFieldKey,
                    currentInputType
            );
        }
        if (isNavigationOrNonTextInput(info)) {
            armNavigationSuppress(currentFieldKey, navigationReason(info));
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        updateCurrentInput(info);
        long now = SystemClock.uptimeMillis();
        AppState.recordSafeImeStartInputView(this, currentFieldKey, restarting);
        setCandidatesViewShown(false);

        if (isManualShowSettling(now)) {
            renderInputRoot("start_input_keep");
            refreshPreview();
        } else {
            hideForAutofocusStrict(currentFieldKey, "start_input_view");
        }
    }

    @Override
    public View onCreateInputView() {
        setCandidatesViewShown(false);
        try {
            inputRoot = new FrameLayout(this);
            inputRoot.setBackgroundColor(Color.TRANSPARENT);
            renderInputRoot("create_input_view");
            return inputRoot;
        } catch (RuntimeException error) {
            AppState.recordSafeImeCrashGuard(this, "create_input_view", error);
            return buildHiddenView();
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        AppState.recordSafeImeEvaluateFullscreen(this, false);
        return false;
    }

    @Override
    public View onCreateExtractTextView() {
        AppState.recordSafeImeCreateExtract(this);
        try {
            return buildSafeExtractView();
        } catch (RuntimeException error) {
            AppState.recordSafeImeCrashGuard(this, "create_extract_view", error);
            return buildEmergencyExtractView();
        }
    }

    @Override
    public void onUpdateExtractingVisibility(EditorInfo ei) {
        AppState.recordSafeImeUpdateExtractingVisibility(this, "visibility", fieldKey(ei));
    }

    @Override
    public void onUpdateExtractingViews(EditorInfo ei) {
        AppState.recordSafeImeUpdateExtractingVisibility(this, "views", fieldKey(ei));
    }

    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        long now = SystemClock.uptimeMillis();
        boolean explicitShow = hasExplicitShowFlag(flags);
        boolean a11yTap = AppState.consumeSafeImeA11yTap(
                this,
                currentFieldKey,
                "show_input_requested"
        );
        boolean keepManualWindow = isManualShowSettling(now);
        boolean manualClickSignal = isRecentManualClickForCurrentField(now);
        boolean strictGatePackage = explicitShow
                && AppState.isStrictSafeImePackage(currentPackageName);
        boolean gateAccepted = strictGatePackage
                && (AppState.consumeSafeImeGateToken(
                        this,
                        currentFieldKey,
                        currentPackageName,
                        "show_input_requested"
                ) || AppState.consumeSafeImeGateFocusPass(
                        this,
                        currentPackageName,
                        "show_input_requested"
                ));
        String gateSuppressReason = strictGatePackage && !gateAccepted
                ? "missing_gate_token"
                : null;
        String churnReason = explicitShow
                && gateSuppressReason == null
                && !gateAccepted
                && !manualClickSignal
                ? explicitSuppressionReason(now)
                : null;
        boolean suppressChurn = churnReason != null;
        boolean suppressExplicit = explicitShow
                && gateSuppressReason == null
                && !gateAccepted
                && !suppressChurn
                && !manualClickSignal
                && shouldSuppressExplicitAfterUserHide(now, flags, "show_input_requested");
        boolean manualWindow = (explicitShow
                && gateSuppressReason == null
                && !suppressChurn
                && !suppressExplicit)
                || a11yTap
                || keepManualWindow;
        String reason = explicitShow
                ? gateSuppressReason != null ? "explicit_without_gate_suppressed"
                : suppressChurn ? "explicit_suppressed_churn"
                : suppressExplicit ? "explicit_suppressed_navigation" : "explicit_show"
                : a11yTap
                ? "a11y_tap"
                : keepManualWindow ? "manual_settle" : "hidden_state";
        AppState.recordSafeImeShowInputRequested(
                this,
                flags,
                configChange,
                manualWindow,
                reason
        );
        if (gateSuppressReason != null) {
            AppState.recordSafeImeExplicitWithoutGateSuppressed(
                    this,
                    currentFieldKey,
                    currentPackageName,
                    flags,
                    gateSuppressReason
            );
            renderInputRoot("explicit_without_gate_suppressed");
            if (!strictGatePackage) {
                scheduleHideSelf("explicit_without_gate_suppressed", currentFieldKey);
            }
        } else if (suppressChurn) {
            AppState.recordSafeImeExplicitSuppressedChurn(this, currentFieldKey, flags, churnReason);
            renderInputRoot("explicit_suppressed_churn");
            scheduleHideSelf("explicit_suppressed_churn", currentFieldKey);
        } else if (suppressExplicit) {
            renderInputRoot("explicit_suppressed_navigation");
            scheduleHideSelf("explicit_suppressed_navigation", currentFieldKey);
        } else if (explicitShow) {
            if (gateAccepted) {
                AppState.recordSafeImeGateShow(
                        this,
                        currentPackageName,
                        currentFieldKey,
                        "show_input_requested"
                );
            }
            showKeyboardFromExplicitShow(flags, "show_input_requested");
        } else if (a11yTap) {
            showKeyboardFromManualSignal("a11y_tap_show_request", false, flags, false);
        } else if (keepManualWindow) {
            renderInputRoot("show_input_keep");
            refreshPreview();
        }
        if (!manualWindow) {
            AppState.recordSafeImeManualSignalAbsent(this, currentFieldKey, "show_input_requested");
        }
        return manualWindow;
    }

    @Override
    public void onViewClicked(boolean focusChanged) {
        super.onViewClicked(focusChanged);
        long now = SystemClock.uptimeMillis();
        manualClickFieldKey = currentFieldKey == null ? "" : currentFieldKey;
        manualClickAllowsAnyField = focusChanged || manualClickFieldKey.length() == 0;
        manualClickUntilUptime = now + MANUAL_CLICK_WINDOW_MS;
        AppState.recordSafeImeViewClicked(
                this,
                manualClickFieldKey,
                focusChanged,
                MANUAL_CLICK_WINDOW_MS
        );
        AppState.recordSafeImeViewClickedIgnored(this, manualClickFieldKey, focusChanged,
                "wait_for_explicit_show");
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        AppState.setSafeImeKeyboardVisible(this, false, "finish_input_view");
        previewText = null;
    }

    @Override
    public void onDestroy() {
        TinyInputMethodService current = activeService.get();
        if (current == this) {
            activeService = new WeakReference<>(null);
        }
        inputRoot = null;
        handler.removeCallbacksAndMessages(null);
        AppState.setSafeImeKeyboardVisible(this, false, "destroy");
        super.onDestroy();
    }

    @Override
    public void onUpdateSelection(
            int oldSelStart,
            int oldSelEnd,
            int newSelStart,
            int newSelEnd,
            int candidatesStart,
            int candidatesEnd
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        refreshPreview();
    }

    private View buildHiddenView() {
        View hidden = new View(this);
        hidden.setBackgroundColor(Color.TRANSPARENT);
        hidden.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
        ));
        return hidden;
    }

    private View buildSafeExtractView() {
        LinearLayout root = new LinearLayout(this);
        root.setId(android.R.id.extractArea);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setBackgroundColor(0xFF101416);

        ExtractEditText editText = new ExtractEditText(this);
        editText.setId(android.R.id.inputExtractEditText);
        editText.setTextColor(0xFFE7F7F4);
        editText.setHintTextColor(0xFF7B8C91);
        editText.setTextSize(13);
        editText.setGravity(Gravity.CENTER_VERTICAL);
        editText.setSingleLine(false);
        editText.setMinLines(1);
        editText.setBackgroundColor(0xFF101416);
        editText.setPadding(dp(6), 0, dp(6), 0);
        root.addView(editText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        LinearLayout accessories = new LinearLayout(this);
        accessories.setId(android.R.id.inputExtractAccessories);
        accessories.setOrientation(LinearLayout.HORIZONTAL);
        accessories.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        accessories.setBackgroundColor(0xFF101416);
        Button action = new Button(this);
        action.setId(android.R.id.inputExtractAction);
        action.setText("");
        action.setVisibility(View.GONE);
        accessories.addView(action, new LinearLayout.LayoutParams(dp(1), dp(1)));
        root.addView(accessories, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        ));
        return root;
    }

    private View buildEmergencyExtractView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101416);
        ExtractEditText editText = new ExtractEditText(this);
        editText.setId(android.R.id.inputExtractEditText);
        editText.setTextColor(0xFFE7F7F4);
        editText.setBackgroundColor(0xFF101416);
        root.addView(editText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        ));
        return root;
    }

    private View buildKeyboard() {
        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(2), dp(2), dp(2), dp(2));
        keyboard.setBackgroundColor(0xFF101416);

        addPreviewRow(keyboard);
        if (symbols) {
            addRow(keyboard, new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9"});
            addRow(keyboard, new String[]{"0", "@", "#", "$", "_", "&", "-", "+", "/"});
            addRow(keyboard, new String[]{"ABC", "?", "!", "'", ":", ";", "/", "DEL"});
            addBottomRow(keyboard);
        } else {
            addRow(keyboard, new String[]{"q", "w", "e", "r", "t", "y", "u"});
            addRow(keyboard, new String[]{"i", "o", "p", "a", "s", "d", "f"});
            addRow(keyboard, new String[]{"g", "h", "j", "k", "l", "\u00f1", "z"});
            addRow(keyboard, new String[]{"x", "c", "v", "b", "n", "m"});
            addBottomRow(keyboard);
        }
        return keyboard;
    }

    private void addPreviewRow(LinearLayout keyboard) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        keyboard.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
        ));

        previewText = new TextView(this);
        previewText.setTextColor(Color.WHITE);
        previewText.setTextSize(13);
        previewText.setGravity(Gravity.CENTER_VERTICAL);
        previewText.setSingleLine(true);
        previewText.setPadding(dp(8), 0, dp(8), 0);
        previewText.setBackground(keyBackground(false));
        row.addView(previewText, weightedParams(3.6f));
        row.addView(key("HIDE"), weightedParams(1.35f));
    }

    private void addRow(LinearLayout keyboard, String[] labels) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        keyboard.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        ));

        for (String label : labels) {
            row.addView(key(label), weightedParams(1f));
        }
    }

    private void addBottomRow(LinearLayout keyboard) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        keyboard.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
        ));
        row.addView(key(symbols ? "ABC" : "?123"), weightedParams(1.25f));
        row.addView(key("SPACE"), weightedParams(4.0f));
        row.addView(key("DEL"), weightedParams(1.55f));
    }

    private TextView key(final String label) {
        TextView view = new TextView(this);
        view.setText(displayLabel(label));
        view.setTextColor(Color.WHITE);
        view.setTextSize(keyTextSize(label));
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setSingleLine(true);
        view.setClickable(true);
        view.setBackground(keyBackground(isCommand(label)));
        if ("DEL".equals(label)) {
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event == null) {
                        return true;
                    }
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        beginDeleteRepeat();
                        return true;
                    }
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        endDeleteRepeat();
                        return true;
                    }
                    return true;
                }
            });
        } else {
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleKey(label);
                }
            });
        }
        return view;
    }

    private void handleKey(String label) {
        if ("HIDE".equals(label)) {
            expanded = false;
            manualClickUntilUptime = 0L;
            manualShowSettleUntilUptime = 0L;
            manualClickAllowsAnyField = false;
            lastHiddenAtUptime = SystemClock.uptimeMillis();
            boolean strictPackage = AppState.isStrictSafeImePackage(currentPackageName);
            if (strictPackage) {
                userHiddenAtUptime = 0L;
                userHiddenFieldKey = "";
            } else {
                userHiddenAtUptime = lastHiddenAtUptime;
                userHiddenFieldKey = currentFieldKey == null ? "" : currentFieldKey;
            }
            lastSuppressedExplicitAtUptime = 0L;
            lastSuppressedExplicitFieldKey = "";
            AppState.recordSafeImeHideButton(
                    this,
                    currentFieldKey,
                    strictPackage ? 0L : USER_HIDE_NAV_SUPPRESS_MS
            );
            AppState.setSafeImeKeyboardVisible(this, false, "hide_button");
            renderInputRoot("hide_button");
            scheduleHideSelf("hide_button", currentFieldKey);
            return;
        }
        if ("DEL".equals(label)) {
            deleteOnce();
            return;
        }
        if ("SHIFT".equals(label)) {
            shifted = !shifted;
            rebuildKeyboard("shift");
            refreshPreview();
            return;
        }
        if ("?123".equals(label)) {
            symbols = true;
            rebuildKeyboard("symbols");
            refreshPreview();
            return;
        }
        if ("ABC".equals(label)) {
            symbols = false;
            rebuildKeyboard("abc");
            refreshPreview();
            return;
        }
        if ("SPACE".equals(label)) {
            commit(" ");
            return;
        }
        if ("ENTER".equals(label)) {
            enter();
            return;
        }

        String text = shifted && label.length() == 1
                ? label.toUpperCase(new Locale("es", "ES"))
                : label;
        commit(text);
        if (shifted && label.length() == 1) {
            shifted = false;
            rebuildKeyboard("shift_commit");
        }
        refreshPreview();
    }

    private void commit(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(text, 1);
        }
        refreshPreview();
    }

    private void beginDeleteRepeat() {
        deleteRepeatActive = true;
        handler.removeCallbacks(deleteRepeatRunnable);
        deleteOnce();
        handler.postDelayed(deleteRepeatRunnable, DEL_REPEAT_INITIAL_MS);
    }

    private void endDeleteRepeat() {
        deleteRepeatActive = false;
        handler.removeCallbacks(deleteRepeatRunnable);
    }

    private void deleteOnce() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.deleteSurroundingText(1, 0);
        }
        refreshPreview();
    }

    private void enter() {
        InputConnection connection = getCurrentInputConnection();
        EditorInfo editor = getCurrentInputEditorInfo();
        if (connection == null) {
            return;
        }
        int action = editor == null
                ? EditorInfo.IME_ACTION_NONE
                : editor.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action != EditorInfo.IME_ACTION_NONE
                && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            connection.performEditorAction(action);
        } else {
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
        refreshPreview();
    }

    private void refreshPreview() {
        if (previewText == null || !expanded) {
            return;
        }
        try {
            String value = currentText();
            previewText.setText(value.length() == 0 ? " " : value);
        } catch (RuntimeException error) {
            AppState.recordSafeImeCrashGuard(this, "refresh_preview", error);
        }
    }

    private String currentText() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return "";
        }
        try {
            ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
            CharSequence text = extracted == null ? "" : extracted.text;
            String value = String.valueOf(text).replace('\n', ' ').replace('\r', ' ');
            if (value.length() > PREVIEW_MAX_CHARS) {
                value = value.substring(value.length() - PREVIEW_MAX_CHARS);
            }
            return value;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private int keyTextSize(String label) {
        if ("HIDE".equals(label)) {
            return 30;
        }
        if ("SPACE".equals(label)) {
            return 18;
        }
        if ("?123".equals(label) || "ABC".equals(label) || "DEL".equals(label)) {
            return 18;
        }
        return label != null && label.length() > 1 ? 22 : 25;
    }

    private String displayLabel(String label) {
        if ("SPACE".equals(label)) {
            return "espacio";
        }
        if ("HIDE".equals(label)) {
            return "\u25be";
        }
        if ("SHIFT".equals(label)) {
            return shifted ? "SHIFT*" : "SHIFT";
        }
        return label;
    }

    private boolean isCommand(String label) {
        return "DEL".equals(label)
                || "SHIFT".equals(label)
                || "?123".equals(label)
                || "ABC".equals(label)
                || "ENTER".equals(label)
                || "HIDE".equals(label);
    }

    private boolean isManualShowSettling(long now) {
        if (!expanded || manualShowSettleUntilUptime <= now) {
            return false;
        }
        return manualShowFieldKey.length() == 0 || manualShowFieldKey.equals(currentFieldKey);
    }

    private boolean isRecentManualClickForCurrentField(long now) {
        if (manualClickUntilUptime <= now) {
            return false;
        }
        String field = currentFieldKey == null ? "" : currentFieldKey;
        return manualClickAllowsAnyField
                || manualClickFieldKey.length() == 0
                || "unknown".equals(manualClickFieldKey)
                || manualClickFieldKey.equals(field);
    }

    private void showKeyboardFromManualClick(String source) {
        showKeyboardFromManualSignal(source, false, 0, true);
    }

    private void showKeyboardFromExplicitShow(int flags, String source) {
        showKeyboardFromManualSignal(source, true, flags, false);
    }

    private void showKeyboardFromManualSignal(
            String source,
            boolean explicit,
            int flags,
            boolean requestWindow
    ) {
        expanded = true;
        manualShowFieldKey = currentFieldKey == null ? "" : currentFieldKey;
        manualShowSettleUntilUptime = SystemClock.uptimeMillis() + MANUAL_SHOW_SETTLE_MS;
        if (explicit) {
            AppState.recordSafeImeExplicitShow(this, currentFieldKey, flags, source);
        } else {
            AppState.recordSafeImeManualClickShow(this, currentFieldKey, source);
        }
        AppState.setSafeImeKeyboardVisible(this, true, source);
        setCandidatesViewShown(false);
        rebuildKeyboard(explicit ? "explicit_show" : "manual_show");
        if (requestWindow) {
            try {
                showWindow(true);
            } catch (RuntimeException error) {
                AppState.recordSafeImeClickSignalMissing(
                        this,
                        currentFieldKey,
                        "show_window_failed:" + error.getClass().getSimpleName()
                );
            }
        }
        refreshPreview();
    }

    private void hideForAutofocusStrict(String fieldKey, String reason) {
        expanded = false;
        manualShowSettleUntilUptime = 0L;
        lastHiddenAtUptime = SystemClock.uptimeMillis();
        AppState.setSafeImeKeyboardVisible(this, false, reason);
        AppState.recordSafeImeAutofocusHiddenStrict(this, fieldKey, reason, HIDE_SELF_DELAY_MS);
        if (manualClickUntilUptime <= SystemClock.uptimeMillis()) {
            AppState.recordSafeImeClickSignalMissing(this, fieldKey, reason);
            AppState.recordSafeImeManualSignalAbsent(this, fieldKey, reason);
        }
        renderInputRoot("autofocus_hide");
        if (!AppState.isStrictSafeImePackage(currentPackageName)) {
            scheduleHideSelf("autofocus", fieldKey);
        }
    }

    private void handleAccessibilityTap(String packageName, String field) {
        boolean accepted = AppState.consumeSafeImeA11yTap(this, currentFieldKey, "a11y_notify");
        if (!accepted && packageMatchesCurrentField(packageName)) {
            armManualShow("a11y_notify_fallback");
            showKeyboardFromManualClick("a11y_tap");
            return;
        }
        if (accepted) {
            armManualShow("a11y_notify");
            showKeyboardFromManualClick("a11y_tap");
        }
    }

    private boolean canAcceptGate(String packageName) {
        if (!AppState.isStrictSafeImePackage(packageName)) {
            return false;
        }
        if (!packageMatchesCurrentField(packageName)) {
            return false;
        }
        int inputClass = currentInputType & InputType.TYPE_MASK_CLASS;
        if (inputClass == InputType.TYPE_NULL) {
            return false;
        }
        return getCurrentInputConnection() != null;
    }

    private void handleGateTap(String packageName) {
        if (!AppState.isStrictSafeImePackage(packageName)) {
            return;
        }
        if (!canAcceptGate(packageName)) {
            AppState.recordSafeImeGateNoConnection(this, packageName, "ime_not_ready");
            AppState.armSafeImeGateFocusPass(this, packageName, "ime_not_ready");
            return;
        }
        AppState.consumeSafeImeGateToken(this, currentFieldKey, packageName, "gate_direct_show");
        AppState.recordSafeImeGateShow(this, packageName, currentFieldKey, "gate_direct_show");
        showKeyboardFromManualClick("gate_tap");
    }

    private void rebuildKeyboard(String source) {
        renderInputRoot(source);
    }

    private void renderInputRoot(String source) {
        if (inputRoot == null) {
            return;
        }
        try {
            inputRoot.removeAllViews();
            View child = expanded ? buildKeyboard() : buildHiddenView();
            inputRoot.addView(child, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    expanded ? FrameLayout.LayoutParams.WRAP_CONTENT : 0
            ));
        } catch (RuntimeException error) {
            AppState.recordSafeImeCrashGuard(this, source + "_render_input", error);
            expanded = false;
            try {
                inputRoot.removeAllViews();
                inputRoot.addView(buildHiddenView(), new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        0
                ));
            } catch (RuntimeException second) {
                AppState.recordSafeImeCrashGuard(this, source + "_render_fallback", second);
            }
        }
    }

    private void armManualShow(String source) {
        manualClickFieldKey = currentFieldKey == null ? "" : currentFieldKey;
        manualClickAllowsAnyField = manualClickFieldKey.length() == 0
                || "unknown".equals(manualClickFieldKey);
        manualClickUntilUptime = SystemClock.uptimeMillis() + MANUAL_CLICK_WINDOW_MS;
        AppState.appendLog(this, "safe_ime_manual_armed source=" + source
                + " field=" + manualClickFieldKey);
    }

    private boolean packageMatchesCurrentField(String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return true;
        }
        return currentFieldKey == null
                || currentFieldKey.length() == 0
                || "unknown".equals(currentFieldKey)
                || currentFieldKey.startsWith(packageName + ":");
    }

    private void updateCurrentInput(EditorInfo info) {
        currentFieldKey = fieldKey(info);
        if (info == null) {
            currentPackageName = "";
            currentInputType = InputType.TYPE_NULL;
            currentFieldId = -1;
            return;
        }
        currentPackageName = info.packageName == null ? "" : info.packageName;
        currentInputType = info.inputType;
        currentFieldId = info.fieldId;
    }

    private void recordStartInputChurn(String fieldKey) {
        long now = SystemClock.uptimeMillis();
        if (lastStartInputAtUptime > 0L
                && now - lastStartInputAtUptime >= 0L
                && now - lastStartInputAtUptime <= START_INPUT_CHURN_WINDOW_MS) {
            startInputChurnCount++;
        } else {
            startInputChurnCount = 1;
        }
        lastStartInputAtUptime = now;
        if (startInputChurnCount >= START_INPUT_CHURN_THRESHOLD) {
            armNavigationSuppress(fieldKey, "start_input_churn_" + startInputChurnCount);
        }
    }

    private void armNavigationSuppress(String fieldKey, String reason) {
        navigationSuppressUntilUptime = SystemClock.uptimeMillis() + NAVIGATION_SUPPRESS_MS;
        lastNavigationFieldKey = fieldKey == null ? "" : fieldKey;
        AppState.recordSafeImeNavigationInput(this, lastNavigationFieldKey, reason,
                NAVIGATION_SUPPRESS_MS);
    }

    private String explicitSuppressionReason(long now) {
        if (currentPackageName == null || currentPackageName.length() == 0
                || "null".equals(currentPackageName)) {
            return "unknown_package";
        }
        if (isNavigationPackage(currentPackageName)) {
            return "navigation_package=" + currentPackageName;
        }
        int inputClass = currentInputType & InputType.TYPE_MASK_CLASS;
        if (inputClass == InputType.TYPE_NULL) {
            return "input_type_null field=" + currentFieldId;
        }
        if (navigationSuppressUntilUptime > now) {
            return "navigation_window_uptime field=" + lastNavigationFieldKey;
        }
        long wallUntil = AppState.getSafeImeNavigationSuppressUntil(this);
        long wallNow = System.currentTimeMillis();
        if (wallUntil > wallNow) {
            return "navigation_window_persisted field="
                    + String.valueOf(AppState.getSafeImeLastNavigationField(this));
        }
        if (startInputChurnCount >= START_INPUT_CHURN_THRESHOLD
                && lastStartInputAtUptime > 0L
                && now - lastStartInputAtUptime >= 0L
                && now - lastStartInputAtUptime <= START_INPUT_CHURN_WINDOW_MS) {
            return "start_input_churn_" + startInputChurnCount;
        }
        return null;
    }

    private boolean isNavigationOrNonTextInput(EditorInfo info) {
        if (info == null) {
            return false;
        }
        if (isNavigationPackage(String.valueOf(info.packageName))) {
            return true;
        }
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        return inputClass == InputType.TYPE_NULL;
    }

    private boolean isStrictRealTextInput(EditorInfo info) {
        if (info == null || !AppState.isStrictSafeImePackage(String.valueOf(info.packageName))) {
            return false;
        }
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        return inputClass != InputType.TYPE_NULL;
    }

    private String navigationReason(EditorInfo info) {
        if (info == null) {
            return "unknown";
        }
        String packageName = String.valueOf(info.packageName);
        if (isNavigationPackage(packageName)) {
            return "package=" + packageName;
        }
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        if (inputClass == InputType.TYPE_NULL) {
            return "input_type_null";
        }
        return "navigation";
    }

    private boolean isNavigationPackage(String packageName) {
        return "android".equals(packageName)
                || "android.settings".equals(packageName)
                || "com.android.settings".equals(packageName)
                || "com.android.packageinstaller".equals(packageName)
                || "com.android.systemui".equals(packageName)
                || "com.google.android.inputmethod.latin".equals(packageName)
                || "com.dw.launcher".equals(packageName)
                || "com.dw.recents".equals(packageName)
                || "com.dw.setting".equals(packageName)
                || "com.dw.smartisland".equals(packageName)
                || "com.sprd.powersavemodelauncher".equals(packageName);
    }

    private boolean shouldSuppressExplicitAfterUserHide(long now, int flags, String source) {
        if (userHiddenAtUptime <= 0L) {
            return false;
        }
        long age = now - userHiddenAtUptime;
        if (age < 0L || age > USER_HIDE_NAV_SUPPRESS_MS) {
            return false;
        }
        String field = currentFieldKey == null ? "" : currentFieldKey;
        if (userHiddenFieldKey.length() > 0 && !userHiddenFieldKey.equals(field)) {
            return false;
        }
        long suppressedAge = now - lastSuppressedExplicitAtUptime;
        if (field.equals(lastSuppressedExplicitFieldKey)
                && suppressedAge >= USER_HIDE_DOUBLE_TAP_ACCEPT_MIN_MS
                && suppressedAge <= USER_HIDE_DOUBLE_TAP_ACCEPT_MAX_MS) {
            userHiddenAtUptime = 0L;
            userHiddenFieldKey = "";
            lastSuppressedExplicitAtUptime = 0L;
            lastSuppressedExplicitFieldKey = "";
            AppState.appendLog(this, "safe_ime_explicit_double_tap_open"
                    + " age=" + suppressedAge
                    + " field=" + field);
            return false;
        }
        lastSuppressedExplicitAtUptime = now;
        lastSuppressedExplicitFieldKey = field;
        AppState.recordSafeImeExplicitSuppressedNavigation(this, field, flags, source);
        return true;
    }

    private boolean hasExplicitShowFlag(int flags) {
        return (flags & InputMethod.SHOW_EXPLICIT) != 0
                || (flags & InputMethod.SHOW_FORCED) != 0;
    }

    private void scheduleHideSelf(final String reason, final String fieldKey) {
        final String safeFieldKey = fieldKey == null ? "" : fieldKey;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!expanded && safeFieldKey.equals(currentFieldKey)) {
                    requestHideSelf(0);
                    try {
                        hideWindow();
                    } catch (RuntimeException error) {
                        AppState.recordSafeImeCrashGuard(
                                TinyInputMethodService.this,
                                reason + "_hide_window",
                                error
                        );
                    }
                    AppState.recordSafeImeHideSelfRequested(
                            TinyInputMethodService.this,
                            reason,
                            safeFieldKey
                    );
                }
            }
        }, HIDE_SELF_DELAY_MS);
    }

    private String fieldKey(EditorInfo info) {
        if (info == null) {
            return "unknown";
        }
        return String.valueOf(info.packageName)
                + ":"
                + info.fieldId
                + ":"
                + info.inputType
                + ":"
                + info.imeOptions;
    }

    private LinearLayout.LayoutParams weightedParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                weight
        );
        params.setMargins(dp(1), dp(1), dp(1), dp(1));
        return params;
    }

    private GradientDrawable keyBackground(boolean command) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(command ? 0xFF2A686A : 0xFF252B30);
        drawable.setStroke(1, command ? 0xFF66D3D5 : 0xFF41484E);
        drawable.setCornerRadius(dp(5));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
