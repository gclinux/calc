package net.typeblog.shelter.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import net.typeblog.shelter.R;
import net.typeblog.shelter.util.LocalStorageManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class CalculatorActivity extends AppCompatActivity {
    private static final long DOUBLE_TAP_WINDOW_MS = 1500;
    private static final int UNLOCK_TAP_COUNT = 5;
    private static final String PREF_PASSWORD_HASH = "calc_password_hash";
    private static final String PREF_KEY_SOUND_ENABLED = "calc_key_sound_enabled";

    private TextView mDisplay;
    private TextView mStatus;
    private Toolbar mToolbar;
    private AlertDialog mSettingsDialog;

    private ToneGenerator mToneGenerator;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Calculator state
    private String mCurrentInput = "0";
    private String mPreviousValue = null;
    private String mPendingOperator = null;
    private boolean mWaitingForNewInput = false;
    private boolean mJustCalculated = false;

    // Unlock state
    private boolean mIsUnlockMode = false;
    private int mUpdateTapCount = 0;
    private long mLastUpdateTapTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        mToolbar = findViewById(R.id.calc_toolbar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle(R.string.calc_title);
        }

        mToolbar.setOnClickListener(v -> onTitleClicked());

        mDisplay = findViewById(R.id.calc_display);
        mStatus = findViewById(R.id.calc_input_status);

        mToneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);

        // Key sound defaults to enabled; getBoolean(pref, true) handles both
        // "never set" and the stored boolean value without type conflicts.
        setupButtons();
    }

    private void setupButtons() {
        int[] numberIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3,
                R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7,
                R.id.btn_8, R.id.btn_9, R.id.btn_dot
        };

        for (int id : numberIds) {
            findViewById(id).setOnClickListener(this::onNumberClick);
        }

        findViewById(R.id.btn_plus).setOnClickListener(v -> onOperatorClick("+"));
        findViewById(R.id.btn_minus).setOnClickListener(v -> onOperatorClick("-"));
        findViewById(R.id.btn_multiply).setOnClickListener(v -> onOperatorClick("×"));
        findViewById(R.id.btn_divide).setOnClickListener(v -> onOperatorClick("÷"));
        findViewById(R.id.btn_equals).setOnClickListener(v -> onEqualsClick());
        findViewById(R.id.btn_clear).setOnClickListener(v -> onClearClick());
        findViewById(R.id.btn_sign).setOnClickListener(v -> onSignClick());
        findViewById(R.id.btn_percent).setOnClickListener(v -> onPercentClick());
    }

    private void onNumberClick(View v) {
        playKeySound();

        String value = ((Button) v).getText().toString();

        if (mIsUnlockMode) {
            // In unlock mode, digits become password input
            if (mCurrentInput.length() < 12) {
                if (mCurrentInput.isEmpty()) {
                    mCurrentInput = value;
                } else {
                    mCurrentInput = mCurrentInput + value;
                }
                // Mask password visually
                mDisplay.setText(maskPassword(mCurrentInput));
            }
            return;
        }

        if (mJustCalculated) {
            mCurrentInput = "0";
            mPreviousValue = null;
            mPendingOperator = null;
            mStatus.setText("");
            mJustCalculated = false;
        }

        if (mWaitingForNewInput) {
            mCurrentInput = value.equals(".") ? "0." : value;
            mWaitingForNewInput = false;
        } else {
            if (value.equals(".")) {
                if (mCurrentInput.contains(".")) return;
                mCurrentInput = mCurrentInput + value;
            } else {
                if (mCurrentInput.equals("0")) {
                    mCurrentInput = value;
                } else {
                    mCurrentInput = mCurrentInput + value;
                }
            }
        }

        updateDisplay();
    }

    private void onOperatorClick(String op) {
        playKeySound();
        if (mIsUnlockMode) return;

        if (mPendingOperator != null && !mWaitingForNewInput) {
            calculate();
        }

        mPreviousValue = mCurrentInput;
        mPendingOperator = op;
        mWaitingForNewInput = true;
        mJustCalculated = false;
        updateStatus(formatDisplayForStatus(mPreviousValue) + " " + op);
    }

    private void onEqualsClick() {
        playKeySound();
        if (mIsUnlockMode) return;

        if (mPendingOperator == null || mPreviousValue == null) return;

        calculate();
    }

    private void onClearClick() {
        playKeySound();
        if (mIsUnlockMode) {
            exitUnlockMode();
            return;
        }

        if (!mCurrentInput.equals("0")) {
            mCurrentInput = "0";
            mWaitingForNewInput = false;
        } else {
            mPreviousValue = null;
            mPendingOperator = null;
            mStatus.setText("");
        }
        mJustCalculated = false;
        updateDisplay();
    }

    private void onSignClick() {
        playKeySound();
        if (mIsUnlockMode) return;

        if (mCurrentInput.startsWith("-")) {
            mCurrentInput = mCurrentInput.substring(1);
        } else if (!mCurrentInput.equals("0")) {
            mCurrentInput = "-" + mCurrentInput;
        }
        updateDisplay();
    }

    private void onPercentClick() {
        playKeySound();
        if (mIsUnlockMode) return;

        try {
            BigDecimal value = new BigDecimal(parseNumber(mCurrentInput));
            value = value.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            mCurrentInput = formatResult(value);
            updateDisplay();
            mWaitingForNewInput = true;
        } catch (Exception e) {
            mCurrentInput = "Error";
            updateDisplay();
        }
    }

    private void calculate() {
        if (mPendingOperator == null || mPreviousValue == null) return;

        try {
            BigDecimal a = new BigDecimal(parseNumber(mPreviousValue));
            BigDecimal b = new BigDecimal(parseNumber(mCurrentInput));
            String rhs = formatDisplayForStatus(mCurrentInput);
            BigDecimal result;

            switch (mPendingOperator) {
                case "+":
                    result = a.add(b);
                    break;
                case "-":
                    result = a.subtract(b);
                    break;
                case "×":
                    result = a.multiply(b);
                    break;
                case "÷":
                    if (b.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("Division by zero");
                    }
                    result = a.divide(b, 10, RoundingMode.HALF_UP);
                    break;
                default:
                    return;
            }

            mCurrentInput = formatResult(result);
            updateDisplay();
            updateStatus(formatDisplayForStatus(mPreviousValue) + " " + mPendingOperator + " " + rhs + " =");
            mJustCalculated = true;
            mPendingOperator = null;
            mPreviousValue = null;
            mWaitingForNewInput = true;
        } catch (ArithmeticException e) {
            mCurrentInput = "Error";
            updateDisplay();
            mPendingOperator = null;
            mPreviousValue = null;
            mStatus.setText("");
        } catch (Exception e) {
            mCurrentInput = "Error";
            updateDisplay();
        }
    }

    private String parseNumber(String input) {
        return input.replace("×", "*").replace("÷", "/").replace(",", "");
    }

    private String formatResult(BigDecimal value) {
        // Strip trailing zeros
        value = value.stripTrailingZeros();
        if (value.scale() < 0) {
            value = value.setScale(0);
        }
        String plain = value.toPlainString();

        // Format with grouping separators
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.getDefault());
        DecimalFormat formatter = new DecimalFormat("#,##0.##########", symbols);
        try {
            return formatter.format(value);
        } catch (Exception e) {
            return plain;
        }
    }

    private void updateDisplay() {
        mDisplay.setText(mCurrentInput);
    }

    private void updateStatus(String text) {
        mStatus.setText(text);
    }

    private void playKeySound() {
        if (LocalStorageManager.getInstance().getBoolean(PREF_KEY_SOUND_ENABLED, true)) {
            mToneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
        }
    }

    private String formatDisplayForStatus(String value) {
        return value.replace(",", "");
    }

    private String maskPassword(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            sb.append("•");
        }
        return sb.toString();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.calculator_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.calc_menu_settings) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String soundLabel(boolean enabled) {
        return (enabled ? "✓  " : "○  ") + getString(R.string.calc_key_sound);
    }

    /**
     * Settings panel. It stays open after each tap so "Check for updates" can be
     * tapped five times in a row (the hidden unlock gesture).
     */
    private void showSettingsDialog() {
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, pad / 2);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        Button soundBtn = new Button(this);
        soundBtn.setAllCaps(false);
        soundBtn.setText(soundLabel(LocalStorageManager.getInstance()
                .getBoolean(PREF_KEY_SOUND_ENABLED, true)));
        container.addView(soundBtn, lp);

        Button updateBtn = new Button(this);
        updateBtn.setAllCaps(false);
        updateBtn.setText(R.string.calc_check_update);
        container.addView(updateBtn, lp);

        Button aboutBtn = new Button(this);
        aboutBtn.setAllCaps(false);
        aboutBtn.setText(R.string.calc_about);
        container.addView(aboutBtn, lp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        mSettingsDialog = dialog;

        soundBtn.setOnClickListener(v -> {
            boolean enabled = !LocalStorageManager.getInstance()
                    .getBoolean(PREF_KEY_SOUND_ENABLED, true);
            LocalStorageManager.getInstance().setBoolean(PREF_KEY_SOUND_ENABLED, enabled);
            soundBtn.setText(soundLabel(enabled));
            if (enabled) playKeySound();
        });
        updateBtn.setOnClickListener(v -> {
            onCheckUpdateClicked();
            if (mIsUnlockMode) dialog.dismiss();
        });
        aboutBtn.setOnClickListener(v -> {
            String version = "";
            try {
                version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception ignored) {
            }
            String label = getString(R.string.app_name)
                    + (version == null || version.isEmpty() ? "" : " v" + version);
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void onCheckUpdateClicked() {
        playKeySound();

        long now = SystemClock.elapsedRealtime();
        if (now - mLastUpdateTapTime > DOUBLE_TAP_WINDOW_MS) {
            mUpdateTapCount = 1;
        } else {
            mUpdateTapCount++;
        }
        mLastUpdateTapTime = now;

        if (mUpdateTapCount >= UNLOCK_TAP_COUNT) {
            mUpdateTapCount = 0;
            enterUnlockMode();
        } else {
            // Show a fake update check
            Toast.makeText(this, R.string.calc_update_checking, Toast.LENGTH_SHORT).show();
            mHandler.postDelayed(() -> {
                if (!mIsUnlockMode) {
                    Toast.makeText(this, R.string.calc_update_up_to_date, Toast.LENGTH_SHORT).show();
                }
            }, 800);
        }
    }

    private void enterUnlockMode() {
        if (mSettingsDialog != null && mSettingsDialog.isShowing()) {
            mSettingsDialog.dismiss();
        }

        String savedHash = LocalStorageManager.getInstance().getString(PREF_PASSWORD_HASH);
        if (savedHash == null || savedHash.isEmpty()) {
            showSetPasswordDialog();
        } else {
            mIsUnlockMode = true;
            mCurrentInput = "";
            mPreviousValue = null;
            mPendingOperator = null;
            // Stay disguised as a calculator: keep the title and show no unlock hints.
            // Typed digits appear as dots, and tapping the title performs the unlock.
            mStatus.setText("");
            mDisplay.setText("0");
        }
    }

    private void exitUnlockMode() {
        resetCalculatorState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Re-entering the calculator (from the launcher, or via Back from Shelter)
        // must always show a clean calculator, never a half-typed password.
        if (mSettingsDialog != null && mSettingsDialog.isShowing()) {
            mSettingsDialog.dismiss();
        }
        resetCalculatorState();
    }

    /** Restore the fully neutral "just a calculator" state. */
    private void resetCalculatorState() {
        mIsUnlockMode = false;
        mCurrentInput = "0";
        mPreviousValue = null;
        mPendingOperator = null;
        mWaitingForNewInput = false;
        mJustCalculated = false;
        mUpdateTapCount = 0;
        mLastUpdateTapTime = 0;
        if (mStatus != null) mStatus.setText("");
        if (mDisplay != null) updateDisplay();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.calc_title);
        }
    }

    private void onTitleClicked() {
        if (!mIsUnlockMode) return;

        String input = mCurrentInput.trim();
        if (input.isEmpty()) {
            Toast.makeText(this, R.string.calc_password_hint, Toast.LENGTH_SHORT).show();
            return;
        }

        String savedHash = LocalStorageManager.getInstance().getString(PREF_PASSWORD_HASH);
        if (savedHash == null || savedHash.isEmpty()) {
            // Should not happen, but fallback to set password
            showSetPasswordDialog();
            return;
        }

        String inputHash = md5(input);
        if (inputHash != null && inputHash.equalsIgnoreCase(savedHash)) {
            unlockAndLaunchShelter();
        } else {
            Toast.makeText(this, R.string.calc_password_incorrect, Toast.LENGTH_SHORT).show();
            mCurrentInput = "";
            mDisplay.setText("0");
        }
    }

    private void showSetPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.calc_password_set_title);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        final EditText input = new EditText(this);
        input.setHint(R.string.calc_password_hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(input);

        final EditText confirm = new EditText(this);
        confirm.setHint(R.string.calc_password_confirm_hint);
        confirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(confirm);

        builder.setView(layout);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            exitUnlockMode();
        });
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = input.getText().toString().trim();
                String confirmPassword = confirm.getText().toString().trim();

                if (password.isEmpty()) {
                    input.setError(getString(R.string.calc_password_hint));
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    confirm.setError(getString(R.string.calc_password_mismatch));
                    return;
                }

                String hash = md5(password);
                if (hash != null) {
                    LocalStorageManager.getInstance().setString(PREF_PASSWORD_HASH, hash);
                    Toast.makeText(this, R.string.calc_password_set_success, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    // Now enter unlock mode so user can unlock
                    enterUnlockMode();
                }
            });
        });
        dialog.show();
    }

    private void unlockAndLaunchShelter() {
        // Return to a neutral calculator before leaving, and DO NOT finish this
        // activity: it must remain the root of the launcher task. On the next
        // launch from the home screen, singleTask + clearTaskOnLaunch will clear
        // Shelter's screens above it and show only the calculator.
        resetCalculatorState();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mToneGenerator != null) {
            mToneGenerator.release();
        }
    }
}
