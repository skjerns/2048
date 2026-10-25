package com.tpcstld.twozerogame;

import android.content.SharedPreferences;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

public class MainActivity extends AppCompatActivity {

    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String SCORE = "score";
    private static final String HIGH_SCORE = "high score temp";
    private static final String GAME_STATE = "game state";
    private static final String UNDO_HISTORY = "undo history";
    private static final String UNDO_COINS = "undo coins";
    private static final String MERGED_VALUES = "merged values";

    private static final String NO_LOGIN_PROMPT = "no_login_prompt";

    private static final int RC_SIGN_IN = 9001;

    private boolean firstLoginAttempt = false;

    private MainView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new MainView(this);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        view.hasSaveState = settings.getBoolean("save_state", false);

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("hasState")) {
                load();
            }
        }
        setContentView(view);

        // Set status bar color
        setStatusBarColor(getWindow(), getResources().getColor(R.color.status_background));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            //Do nothing
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            view.game.move(2);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            view.game.move(0);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            view.game.move(3);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            view.game.move(1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean("hasState", true);
        save();
    }

    protected void onPause() {
        super.onPause();
        save();
    }

    private void save() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        Tile[][] field = view.game.grid.field;
        editor.putInt(WIDTH, field.length);
        editor.putInt(HEIGHT, field[0].length);
        for (int xx = 0; xx < field.length; xx++) {
            for (int yy = 0; yy < field[0].length; yy++) {
                if (field[xx][yy] != null) {
                    editor.putInt(xx + " " + yy, field[xx][yy].getValue());
                } else {
                    editor.putInt(xx + " " + yy, 0);
                }
            }
        }
        editor.putLong(SCORE, view.game.score);
        editor.putLong(HIGH_SCORE, view.game.highScore);
        editor.putInt(GAME_STATE, view.game.gameState);
        editor.putString(UNDO_HISTORY, view.game.undoHistoryToString());
        editor.putInt(UNDO_COINS, view.game.undoCoins);
        editor.putString(MERGED_VALUES, view.game.mergedValuesToString());
        editor.apply();
    }

    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        //Stopping all animations
        view.game.aGrid.cancelAnimations();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        // A game saved on a different board size can't be restored onto this one.
        if (settings.getInt(WIDTH, view.game.numSquaresX) != view.game.numSquaresX
                || settings.getInt(HEIGHT, view.game.numSquaresY) != view.game.numSquaresY) {
            return;
        }
        for (int xx = 0; xx < view.game.grid.field.length; xx++) {
            for (int yy = 0; yy < view.game.grid.field[0].length; yy++) {
                int value = settings.getInt(xx + " " + yy, -1);
                if (value > 0) {
                    view.game.grid.field[xx][yy] = new Tile(xx, yy, value);
                } else if (value == 0) {
                    view.game.grid.field[xx][yy] = null;
                }
            }
        }

        view.game.score = settings.getLong(SCORE, view.game.score);
        view.game.highScore = settings.getLong(HIGH_SCORE, view.game.highScore);
        view.game.gameState = settings.getInt(GAME_STATE, view.game.gameState);
        view.game.undoHistoryFromString(settings.getString(UNDO_HISTORY, null));
        view.game.undoCoins = settings.getInt(UNDO_COINS, 0);
        view.game.mergedValuesFromString(settings.getString(MERGED_VALUES, null));
    }

    private void setStatusBarColor(Window window, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) { // Android 15+
            window.getDecorView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View view, @NonNull WindowInsets windowInsets) {
                    Insets statusBarInsets = windowInsets.getInsets(WindowInsets.Type.statusBars());
                    view.setBackgroundColor(color);

                    // Adjust padding to avoid overlap
                    view.setPadding(0, statusBarInsets.top, 0, 0);
                    return windowInsets;
                }
            });

        } else {
            // For Android 14 and below
            window.setStatusBarColor(color);
        }
    }
}
