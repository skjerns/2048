package com.tpcstld.twozerogame;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

/**
 * Vibration on or off, and resetting the high scores.
 */
class SettingsDialog extends GameDialog {

    private static final String VIBRATION = "vibration";

    private final MainGame game;

    SettingsDialog(Context context, MainGame game) {
        super(context);
        this.game = game;
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
        setFont((TextView) findViewById(R.id.settings_title), vibration, reset, close);

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
                        game.resetHighScores();
                        Toast.makeText(getContext(), R.string.high_scores_reset, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.continue_game, null)
                .setTitle(R.string.reset_high_scores_dialog_title)
                .setMessage(R.string.reset_high_scores_dialog_message)
                .show();
    }
}
