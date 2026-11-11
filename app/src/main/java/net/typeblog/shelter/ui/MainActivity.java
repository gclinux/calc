package net.typeblog.shelter.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import net.typeblog.shelter.R;
import net.typeblog.shelter.util.LocalStorageManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String PREF_SECRET_PASSWORD_HASH = "secret_password_hash";
    private static final String PREF_CALC_SOUND_ENABLED = "calc_sound_enabled";
    private static final long UPDATE_CLICK_WINDOW_MS = 10000;
    private static final int UPDATE_CLICKS_TO_UNLOCK = 5;

    private TextView mCurrentDisplay;
    private TextView mFormulaDisplay;
    private TextView mTitle;
    private View mTitleUnderline;
    private ImageButton mBtnSettings;
    private AlertDialog mSettingsDialog;

    private StringBuilder mCurrentInput = new StringBuilder();
    private StringBuilder mFormulaText = new StringBuilder();
    private double mPreviousValue = Double.NaN;
    private String mPendingOperator = null;
    private boolean mJustEvaluated = false;
    private boolean mPasswordMode = false;
    private StringBuilder mPasswordInput = new StringBuilder();

    private final List<Long> mUpdateClickTimes = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private AudioManager mAudioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        mCurrentDisplay = findViewById(R.id.calc_current);
        mFormulaDisplay = findViewById(R.id.calc_formula);
        mTitle = findViewById(R.id.calc_title);
        mTitleUnderline = findViewById(R.id.calc_title_underline);
        mBtnSettings = findViewById(R.id.calc_btn_settings);

        setupHeader();
        setupKeypad();
        updateDisplay();
    }

    private void setupHeader() {
        mTitle.setOnClickListener(v -> onTitleClicked());
        mTitleUnderline.setOnClickListener(v -> onTitleClicked());
        mBtnSettings.setOnClickListener(v -> showSettingsMenu());
    }

    private void setupKeypad() {
        int[] digitIds = new int[]{
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9
        };
        for (int i = 0; i < digitIds.length; i++) {
            final int digit = i;
            findViewById(digitIds[i]).setOnClickListener(v -> {
                playClickSound();
                onDigit(digit);
            });
        }

        findViewById(R.id.btn_dot).setOnClickListener(v -> {
            playClickSound();
            onDot();
        });
        findViewById(R.id.btn_ac).setOnClickListener(v -> {
            playClickSound();
            onAllClear();
        });
        findViewById(R.id.btn_backspace).setOnClickListener(v -> {
            playClickSound();
            onBackspace();
        });
        findViewById(R.id.btn_plus_minus).setOnClickListener(v -> {
            playClickSound();
            onPlusMinus();
        });
        findViewById(R.id.btn_percent).setOnClickListener(v -> {
            playClickSound();
            onPercent();
        });
        findViewById(R.id.btn_divide).setOnClickListener(v -> {
            playClickSound();
            onOperator(getString(R.string.calc_op_divide));
        });
        findViewById(R.id.btn_multiply).setOnClickListener(v -> {
            playClickSound();
            onOperator(getString(R.string.calc_op_multiply));
        });
        findViewById(R.id.btn_minus).setOnClickListener(v -> {
            playClickSound();
            onOperator(getString(R.string.calc_op_minus));
        });
        findViewById(R.id.btn_plus).setOnClickListener(v -> {
            playClickSound();
            onOperator(getString(R.string.calc_op_plus));
        });
        findViewById(R.id.btn_equals).setOnClickListener(v -> {
            playClickSound();
            onEquals();
        });

        int[] memIds = new int[]{R.id.btn_mc, R.id.btn_mp, R.id.btn_mm, R.id.btn_mr};
        for (int id : memIds) {
            findViewById(id).setOnClickListener(v -> {
                playClickSound();
                Toast.makeText(this, R.string.calc_memory_hint, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void playClickSound() {
        if (LocalStorageManager.getInstance().getBoolean(PREF_CALC_SOUND_ENABLED)) {
            mAudioManager.playSoundEffect(AudioManager.FX_KEY_CLICK);
        }
    }

    // ==================== Calculator logic ====================

    private void onDigit(int digit) {
        if (mPasswordMode) {
            if (mPasswordInput.length() < 16) {
                mPasswordInput.append(digit);
                updatePasswordDisplay();
            }
            return;
        }

        if (mJustEvaluated) {
            mCurrentInput.setLength(0);
            mJustEvaluated = false;
        }
        if (mCurrentInput.length() == 1 && mCurrentInput.charAt(0) == '0') {
            mCurrentInput.setLength(0);
        }
        mCurrentInput.append(digit);
        updateDisplay();
    }

    private void onDot() {
        if (mPasswordMode) return;
        if (mJustEvaluated) {
            mCurrentInput.setLength(0);
            mCurrentInput.append('0');
            mJustEvaluated = false;
        }
        if (mCurrentInput.indexOf(".") < 0) {
            if (mCurrentInput.length() == 0) {
                mCurrentInput.append('0');
            }
            mCurrentInput.append('.');
        }
        updateDisplay();
    }

    private void onAllClear() {
        if (mPasswordMode) {
            mPasswordInput.setLength(0);
            updatePasswordDisplay();
            return;
        }
        mCurrentInput.setLength(0);
        mPreviousValue = Double.NaN;
        mPendingOperator = null;
        mFormulaText.setLength(0);
        mJustEvaluated = false;
        updateDisplay();
    }

    private void onBackspace() {
        if (mPasswordMode) {
            if (mPasswordInput.length() > 0) {
                mPasswordInput.setLength(mPasswordInput.length() - 1);
                updatePasswordDisplay();
            }
            return;
        }
        if (mCurrentInput.length() > 0) {
            mCurrentInput.setLength(mCurrentInput.length() - 1);
        } else if (mPendingOperator != null) {
            mPendingOperator = null;
            mFormulaText.setLength(0);
            mCurrentInput.append(formatValue(mPreviousValue));
            mPreviousValue = Double.NaN;
        }
        updateDisplay();
    }

    private void onPlusMinus() {
        if (mPasswordMode) return;
        if (mCurrentInput.length() == 0) return;
        if (mCurrentInput.charAt(0) == '-') {
            mCurrentInput.deleteCharAt(0);
        } else {
            mCurrentInput.insert(0, '-');
        }
        updateDisplay();
    }

    private void onPercent() {
        if (mPasswordMode) return;
        double value = parseCurrentValue();
        value = value / 100.0;
        mCurrentInput.setLength(0);
        mCurrentInput.append(formatValue(value));
        mJustEvaluated = true;
        updateDisplay();
    }

    private void onOperator(String op) {
        if (mPasswordMode) {
            Toast.makeText(this, R.string.calc_password_mode_disabled, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!mCurrentInput.isEmpty()) {
            double value = parseCurrentValue();
            if (!Double.isNaN(mPreviousValue) && mPendingOperator != null && !mJustEvaluated) {
                double result = calculate(mPreviousValue, value, mPendingOperator);
                mPreviousValue = result;
                mCurrentInput.setLength(0);
                mCurrentInput.append(formatValue(result));
                updateDisplay();
                mCurrentInput.setLength(0);
            } else {
                mPreviousValue = value;
            }
            mFormulaText.setLength(0);
            mFormulaText.append(formatValue(mPreviousValue)).append(' ').append(op);
        } else if (!Double.isNaN(mPreviousValue)) {
            mFormulaText.setLength(0);
            mFormulaText.append(formatValue(mPreviousValue)).append(' ').append(op);
        }
        mPendingOperator = op;
        mCurrentInput.setLength(0);
        mJustEvaluated = false;
        updateDisplay();
        updateFormulaDisplay();
    }

    private void onEquals() {
        if (mPasswordMode) return;
        if (mPendingOperator == null || mCurrentInput.isEmpty()) return;
        double value = parseCurrentValue();
        double result = calculate(mPreviousValue, value, mPendingOperator);
        String resultStr = formatValue(result);
        mFormulaText.setLength(0);
        mFormulaText.append(formatValue(mPreviousValue)).append(' ')
                .append(mPendingOperator).append(' ')
                .append(formatValue(value)).append(" = ").append(resultStr);
        mCurrentInput.setLength(0);
        mCurrentInput.append(resultStr);
        mPendingOperator = null;
        mPreviousValue = Double.NaN;
        mJustEvaluated = true;
        updateDisplay();
        updateFormulaDisplay();
    }

    private double parseCurrentValue() {
        if (mCurrentInput.length() == 0) return 0;
        try {
            return Double.parseDouble(mCurrentInput.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double calculate(double a, double b, String op) {
        if (op == null) return b;
        switch (op) {
            case "+":
                return a + b;
            case "-":
                return a - b;
            case "×":
                return a * b;
            case "÷":
                return b == 0 ? Double.NaN : a / b;
            default:
                return b;
        }
    }

    private String formatValue(double value) {
        if (Double.isNaN(value)) return "Error";
        if (Double.isInfinite(value)) return "Error";
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        String s = String.valueOf(value);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    private void updateDisplay() {
        if (mPasswordMode) {
            updatePasswordDisplay();
            return;
        }
        mCurrentDisplay.setText(mCurrentInput.length() == 0 ? "0" : mCurrentInput.toString());
    }

    private void updateFormulaDisplay() {
        mFormulaDisplay.setText(mFormulaText.toString());
    }

    private void updatePasswordDisplay() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mPasswordInput.length(); i++) {
            sb.append('*');
        }
        mFormulaDisplay.setText("");
        mCurrentDisplay.setText(sb.length() > 0 ? sb.toString() : getString(R.string.calc_password_placeholder));
    }

    // ==================== Password / unlock ====================

    private void onTitleClicked() {
        playClickSound();
        if (!mPasswordMode) return;
        if (mPasswordInput.length() == 0) return;
        String storedHash = LocalStorageManager.getInstance().getString(PREF_SECRET_PASSWORD_HASH);
        if (storedHash != null && storedHash.equals(hashPassword(mPasswordInput.toString()))) {
            enterShelter();
        } else {
            Toast.makeText(this, R.string.calc_password_wrong, Toast.LENGTH_SHORT).show();
            mPasswordInput.setLength(0);
            updatePasswordDisplay();
        }
    }

    private void enterShelter() {
        mPasswordMode = false;
        mPasswordInput.setLength(0);
        Intent intent = new Intent(this, ShelterMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return password;
        }
    }

    // ==================== Settings menu ====================

    private void showSettingsMenu() {
        playClickSound();
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings_menu, null);
        TextView soundItem = view.findViewById(R.id.menu_sound);
        TextView updateItem = view.findViewById(R.id.menu_update);
        TextView aboutItem = view.findViewById(R.id.menu_about);
        TextView cancelItem = view.findViewById(R.id.menu_cancel);

        final boolean[] soundOn = {LocalStorageManager.getInstance().getBoolean(PREF_CALC_SOUND_ENABLED)};
        updateSoundLabel(soundItem, soundOn[0]);

        soundItem.setOnClickListener(v -> {
            soundOn[0] = !soundOn[0];
            LocalStorageManager.getInstance().setBoolean(PREF_CALC_SOUND_ENABLED, soundOn[0]);
            updateSoundLabel(soundItem, soundOn[0]);
            playClickSound();
            Toast.makeText(this,
                    soundOn[0] ? R.string.calc_sound_enabled : R.string.calc_sound_disabled,
                    Toast.LENGTH_SHORT).show();
        });

        // Check for updates: do NOT close the menu here.
        updateItem.setOnClickListener(v -> onCheckUpdateClicked());

        aboutItem.setOnClickListener(v -> {
            if (mSettingsDialog != null) mSettingsDialog.dismiss();
            showAboutDialog();
        });

        cancelItem.setOnClickListener(v -> {
            if (mSettingsDialog != null) mSettingsDialog.dismiss();
        });

        mSettingsDialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .create();
        mSettingsDialog.show();
    }

    private void updateSoundLabel(TextView item, boolean enabled) {
        item.setText(enabled ? getString(R.string.calc_settings_sound_off)
                : getString(R.string.calc_settings_sound_on));
    }

    private void onCheckUpdateClicked() {
        long now = System.currentTimeMillis();
        while (!mUpdateClickTimes.isEmpty() && now - mUpdateClickTimes.get(0) > UPDATE_CLICK_WINDOW_MS) {
            mUpdateClickTimes.remove(0);
        }
        mUpdateClickTimes.add(now);

        int clickCount = mUpdateClickTimes.size();
        if (clickCount >= UPDATE_CLICKS_TO_UNLOCK) {
            mUpdateClickTimes.clear();
            onSecretTrigger();
        } else if (clickCount == 1) {
            Toast.makeText(this, R.string.calc_already_latest_version, Toast.LENGTH_SHORT).show();
        }
        // Clicks 2-4 within the window keep the menu open and show no toast.
    }

    private void onSecretTrigger() {
        String storedHash = LocalStorageManager.getInstance().getString(PREF_SECRET_PASSWORD_HASH);
        if (storedHash == null) {
            // No password yet -> set password, close the menu first.
            if (mSettingsDialog != null) mSettingsDialog.dismiss();
            showSetPasswordDialog();
        } else {
            // Password exists -> switch to password mode, close the menu first.
            if (mSettingsDialog != null) mSettingsDialog.dismiss();
            activatePasswordMode();
        }
    }

    private void showSetPasswordDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_set_password, null);
        EditText input = view.findViewById(R.id.password_input);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle(R.string.calc_set_password_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String password = input.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(this, R.string.calc_password_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    LocalStorageManager.getInstance().setString(PREF_SECRET_PASSWORD_HASH, hashPassword(password));
                    Toast.makeText(this, R.string.calc_password_set, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void activatePasswordMode() {
        mPasswordMode = true;
        mPasswordInput.setLength(0);
        mCurrentInput.setLength(0);
        mFormulaText.setLength(0);
        mPendingOperator = null;
        mPreviousValue = Double.NaN;
        mJustEvaluated = false;
        updatePasswordDisplay();
        Toast.makeText(this, R.string.calc_password_mode_entered, Toast.LENGTH_SHORT).show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.calc_about_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
