package com.tpcstld.twozerogame;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * A dialog in the style of the game: a rounded card in the background colour, set in Clear Sans.
 */
abstract class GameDialog extends Dialog {

    final Typeface font;

    GameDialog(Context context) {
        super(context);
        font = Typeface.createFromAsset(context.getAssets(), "ClearSans-Bold.ttf");
    }

    // Call from onCreate() to show the layout on a transparent, slightly inset window.
    void setUpContent(int layout) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(layout);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout((int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.9),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    void setFont(TextView... views) {
        for (TextView view : views) {
            view.setTypeface(font);
        }
    }

    int dp(int value) {
        return (int) (value * getContext().getResources().getDisplayMetrics().density);
    }
}
