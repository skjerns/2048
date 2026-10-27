package com.tpcstld.twozerogame;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

/**
 * Vibration on or off, the animation speed, and resetting the high scores.
 */
class SettingsDialog extends GameDialog {

    private static final String VIBRATION = "vibration";
    private static final String ANIMATION_SPEED = "animation speed";
    // Animation speed in percent: 50% to 400% in steps of 25%
    private static final int MIN_SPEED = 50;
    private static final int MAX_SPEED = 400;
    private static final int SPEED_STEP = 25;

    private final MainView view;

    SettingsDialog(Context context, MainView view) {
        super(context);
        this.view = view;
    }

    static int getAnimationSpeed(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(ANIMATION_SPEED, 100);
    }

    static boolean isVibrationEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(VIBRATION, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setUpContent(R.layout.dialog_settings);

        SwitchCompat vibration = findViewById(R.id.settings_vibration);
        TextView reset = findViewById(R.id.settings_reset_high_scores);
        TextView close = findViewById(R.id.settings_close);
        final TextView speedValue = findViewById(R.id.settings_speed_value);
        SeekBar speed = findViewById(R.id.settings_speed);
        setFont((TextView) findViewById(R.id.settings_title), vibration, reset, close, speedValue,
                (TextView) findViewById(R.id.settings_speed_label));

        speed.setMax((MAX_SPEED - MIN_SPEED) / SPEED_STEP);
        speed.setProgress((getAnimationSpeed(getContext()) - MIN_SPEED) / SPEED_STEP);
        speedValue.setText(getAnimationSpeed(getContext()) + "%");
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = MIN_SPEED + progress * SPEED_STEP;
                speedValue.setText(percent + "%");
                PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                        .putInt(ANIMATION_SPEED, percent)
                        .apply();
                view.setAnimationSpeed(percent / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        vibration.setChecked(isVibrationEnabled(getContext()));
        vibration.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                        .putBoolean(VIBRATION, checked)
                        .apply();
                if (checked) {
                    // A taste of what it feels like
                    button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            }
        });

        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmReset();
            }
        });
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
    }

    private void confirmReset() {
        new AlertDialog.Builder(getContext())
                .setPositiveButton(R.string.reset, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        view.game.resetHighScores();
                        Toast.makeText(getContext(), R.string.high_scores_reset, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.continue_game, null)
                .setTitle(R.string.reset_high_scores_dialog_title)
                .setMessage(R.string.reset_high_scores_dialog_message)
                .show();
    }
}
