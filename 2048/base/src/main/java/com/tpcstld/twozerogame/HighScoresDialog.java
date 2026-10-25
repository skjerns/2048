package com.tpcstld.twozerogame;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The best games of every board size, one tab per size.
 */
class HighScoresDialog extends GameDialog {

    private final MainGame game;
    private final List<TextView> tabs = new ArrayList<>();
    private int selectedSize;
    private LinearLayout list;
    private TextView empty;

    HighScoresDialog(Context context, MainGame game) {
        super(context);
        this.game = game;
        selectedSize = game.numSquaresX;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setUpContent(R.layout.dialog_high_scores);
        Window window = getWindow();
        if (window != null) {
            // Pinned to the top, so the tabs stay in place when the lists differ in length
            window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.y = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.12);
            window.setAttributes(attributes);
        }

        list = findViewById(R.id.high_scores_list);
        empty = findViewById(R.id.high_scores_empty);
        TextView close = findViewById(R.id.high_scores_close);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        setFont((TextView) findViewById(R.id.high_scores_title), empty, close);

        LinearLayout tabContainer = findViewById(R.id.high_scores_tabs);
        int margin = dp(4);
        for (final int size : MainGame.BOARD_SIZES) {
            TextView tab = new TextView(getContext());
            tab.setText(size + "×" + size);
            tab.setGravity(Gravity.CENTER);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            tab.setPadding(0, dp(10), 0, dp(10));
            setFont(tab);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectedSize = size;
                    showScores();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(margin, 0, margin, 0);
            tabContainer.addView(tab, params);
            tabs.add(tab);
        }

        showScores();
    }

    private void showScores() {
        Resources resources = getContext().getResources();
        for (int i = 0; i < tabs.size(); i++) {
            boolean selected = MainGame.BOARD_SIZES[i] == selectedSize;
            tabs.get(i).setBackgroundResource(selected ? R.drawable.button_rectangle : R.drawable.cell_rectangle);
            tabs.get(i).setTextColor(resources.getColor(selected ? R.color.text_white : R.color.text_black));
        }

        list.removeAllViews();
        List<HighScores.Entry> entries = HighScores.load(getContext(), selectedSize);
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
        for (int i = 0; i < entries.size(); i++) {
            HighScores.Entry entry = entries.get(i);
            View row = inflater.inflate(R.layout.item_high_score, list, false);
            TextView rank = row.findViewById(R.id.high_score_rank);
            TextView score = row.findViewById(R.id.high_score_score);
            TextView date = row.findViewById(R.id.high_score_date);
            TextView tile = row.findViewById(R.id.high_score_tile);
            setFont(rank, score, date, tile);

            rank.setText((i + 1) + ".");
            score.setText(String.valueOf(entry.score));
            if (selectedSize == game.numSquaresX && entry.gameId == game.gameId) {
                row.setBackgroundResource(R.drawable.current_game_rectangle);
                date.setText(R.string.current_game);
            } else if (entry.time > 0) {
                date.setText(dateFormat.format(new Date(entry.time)));
            }

            if (entry.bestTile > 0) {
                // A small copy of the best tile of that game
                String value = String.valueOf(entry.bestTile);
                tile.setText(value);
                tile.setBackgroundResource(MainView.getCellRectangleId(31 - Integer.numberOfLeadingZeros(entry.bestTile)));
                tile.setTextColor(resources.getColor(entry.bestTile >= 8 ? R.color.text_white : R.color.text_black));
                tile.setTextSize(TypedValue.COMPLEX_UNIT_SP, value.length() <= 2 ? 18 : value.length() == 3 ? 15 : value.length() == 4 ? 12 : 10);
            } else {
                tile.setVisibility(View.INVISIBLE);
            }
            list.addView(row);
        }
    }
}
