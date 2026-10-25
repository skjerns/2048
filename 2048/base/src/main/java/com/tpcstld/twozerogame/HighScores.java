package com.tpcstld.twozerogame;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The best games of every board size, stored as a JSON list in the shared preferences.
 */
class HighScores {

    static final int MAX_ENTRIES = 10;
    private static final String HIGH_SCORES = "high scores ";

    static class Entry {
        final long gameId;
        long score;
        int bestTile;
        // When the entry was last improved, 0 if unknown
        long time;

        Entry(long gameId, long score, int bestTile, long time) {
            this.gameId = gameId;
            this.score = score;
            this.bestTile = bestTile;
            this.time = time;
        }
    }

    static List<Entry> load(Context context, int size) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        List<Entry> entries = new ArrayList<>();
        String data = settings.getString(key(size), null);
        if (data == null) {
            // No list yet: start it with the single high score kept before there were lists.
            long highScore = settings.getLong(MainGame.highScoreKey(size), 0);
            if (highScore > 0) {
                entries.add(new Entry(0, highScore, 0, 0));
            }
            return entries;
        }
        try {
            JSONArray array = new JSONArray(data);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                entries.add(new Entry(entry.getLong("id"), entry.getLong("score"),
                        entry.getInt("tile"), entry.getLong("time")));
            }
        } catch (JSONException e) {
            entries.clear();
        }
        return entries;
    }

    static void record(Context context, int size, long gameId, long score, int bestTile) {
        List<Entry> entries = load(context, size);
        Entry entry = null;
        for (Entry existing : entries) {
            if (existing.gameId == gameId) {
                entry = existing;
            }
        }
        if (entry == null) {
            entries.add(new Entry(gameId, score, bestTile, System.currentTimeMillis()));
        } else if (score > entry.score || bestTile > entry.bestTile) {
            entry.score = Math.max(entry.score, score);
            entry.bestTile = Math.max(entry.bestTile, bestTile);
            entry.time = System.currentTimeMillis();
        } else {
            return;
        }

        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry first, Entry second) {
                return Long.compare(second.score, first.score);
            }
        });
        JSONArray array = new JSONArray();
        try {
            for (int i = 0; i < Math.min(entries.size(), MAX_ENTRIES); i++) {
                Entry current = entries.get(i);
                array.put(new JSONObject()
                        .put("id", current.gameId)
                        .put("score", current.score)
                        .put("tile", current.bestTile)
                        .put("time", current.time));
            }
        } catch (JSONException e) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(key(size), array.toString())
                .apply();
    }

    private static String key(int size) {
        return HIGH_SCORES + size + "x" + size;
    }
}
