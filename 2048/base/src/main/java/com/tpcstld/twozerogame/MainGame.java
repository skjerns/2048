package com.tpcstld.twozerogame;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tpcstld.twozerogame.snapshot.SnapshotData;
import com.tpcstld.twozerogame.snapshot.SnapshotManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainGame {

    static final int SPAWN_ANIMATION = -1;
    static final int MOVE_ANIMATION = 0;
    static final int MERGE_ANIMATION = 1;

    static final int FADE_GLOBAL_ANIMATION = 0;
    static final int UNSPAWN_GLOBAL_ANIMATION = 1;
    private static final long MOVE_ANIMATION_TIME = MainView.BASE_ANIMATION_TIME;
    private static final long SPAWN_ANIMATION_TIME = MainView.BASE_ANIMATION_TIME;
    private static final long NOTIFICATION_DELAY_TIME = MOVE_ANIMATION_TIME + SPAWN_ANIMATION_TIME;
    private static final long NOTIFICATION_ANIMATION_TIME = MainView.BASE_ANIMATION_TIME * 5;
    private static final int startingMaxValue = 2048;
    //Odd state = game is not active
    //Even state = game is active
    //Win state = active state + 1
    private static final int GAME_WIN = 1;
    private static final int GAME_LOST = -1;
    private static final int GAME_NORMAL = 0;
    int gameState = GAME_NORMAL;
    private static final int GAME_ENDLESS = 2;
    private static final int GAME_ENDLESS_WON = 3;
    private static final String HIGH_SCORE = "high score";
    private static final String BOARD_SIZE = "board size";
    static final int DEFAULT_BOARD_SIZE = 4;
    static final int[] BOARD_SIZES = {4, 5, 6};
    // Merging a value of at least this size for the first time in a game earns an undo coin.
    private static final int MIN_COIN_VALUE = 32;
    private static final int MAX_UNDO_HISTORY = 64;
    private static int endingMaxValue;
    int numSquaresX;
    int numSquaresY;
    private final Context mContext;
    private final MainView mView;
    Grid grid = null;
    AnimationGrid aGrid;
    public long score = 0;
    long highScore = 0;
    // Board states before each move, most recent last.
    private final List<UndoState> undoHistory = new ArrayList<>();
    // Values that have been created by a merge in this game.
    private final Set<Integer> mergedValues = new HashSet<>();
    int undoCoins = 0;
    // Identifies this game in the high score list.
    long gameId;

    MainGame(Context context, MainView view) {
        mContext = context;
        mView = view;
        endingMaxValue = (int) Math.pow(2, view.numCellTypes - 1);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        numSquaresX = settings.getInt(BOARD_SIZE, DEFAULT_BOARD_SIZE);
        numSquaresY = numSquaresX;
    }

    void newGame() {
        if (grid == null) {
            grid = new Grid(numSquaresX, numSquaresY);
        } else {
            recordGame();
            grid.clearGrid();
        }
        gameId = System.currentTimeMillis();
        undoHistory.clear();
        mergedValues.clear();
        undoCoins = 0;
        mView.clearAchievements();
        aGrid = new AnimationGrid(numSquaresX, numSquaresY);
        highScore = getHighScore();
        if (score >= highScore) {
            highScore = score;
            recordHighScore();
        }
        score = DebugTools.getStartingScore();
        gameState = GAME_NORMAL;
        addStartTiles();
        mView.refreshLastTime = true;
        mView.resyncTime();
        mView.invalidate();
    }

    private void addStartTiles() {
        List<Tile> debugTiles = DebugTools.generatePremadeMap();
        if (debugTiles != null) {
            for (Tile tile : debugTiles) {
                this.spawnTile(tile);
            }
            return;
        }

        int startTiles = 2;
        for (int xx = 0; xx < startTiles; xx++) {
            this.addRandomTile();
        }
    }

    @Nullable
    private Tile addRandomTile() {
        if (grid.isCellsAvailable()) {
            int value = Math.random() < 0.9 ? 2 : 4;
            Tile tile = new Tile(grid.randomAvailableCell(), value);
            spawnTile(tile);
            return tile;
        }
        return null;
    }

    private void spawnTile(Tile tile) {
        grid.insertTile(tile);
        aGrid.startAnimation(tile.getX(), tile.getY(), SPAWN_ANIMATION,
                SPAWN_ANIMATION_TIME, MOVE_ANIMATION_TIME, null); //Direction: -1 = EXPANDING
    }

    void setBoardSize(int size) {
        if (size == numSquaresX) {
            return;
        }
        recordGame();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
        settings.edit().putInt(BOARD_SIZE, size).apply();
        numSquaresX = size;
        numSquaresY = size;
        score = 0;
        grid = null;
        newGame();
        mView.onBoardSizeChanged();
    }

    // Every board size keeps its own high score. The 4x4 one uses the original key.
    static String highScoreKey(int size) {
        if (size == DEFAULT_BOARD_SIZE) {
            return HIGH_SCORE;
        }
        return HIGH_SCORE + " " + size + "x" + size;
    }

    private String highScoreKey() {
        return highScoreKey(numSquaresX);
    }

    // Forgets all high scores. The game in progress counts again from its current score.
    void resetHighScores() {
        HighScores.reset(mContext);
        highScore = score;
        recordHighScore();
        mView.invalidate();
    }

    // Adds this game to the high score list of its board size, or updates its entry.
    void recordGame() {
        if (grid == null || score <= 0) {
            return;
        }
        int bestTile = 0;
        for (int[] column : grid.getValues()) {
            for (int value : column) {
                bestTile = Math.max(bestTile, value);
            }
        }
        HighScores.record(mContext, numSquaresX, gameId, score, bestTile);
    }

    private void recordHighScore() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(highScoreKey(), highScore);
        editor.apply();

        if (numSquaresX == DEFAULT_BOARD_SIZE) {
            SnapshotData data = new SnapshotData(highScore);
            SnapshotManager.saveSnapshot(mContext, data);
        }
    }

    void handleSnapshot(SnapshotData data) {
        highScore = Math.max(data.getHighScore(), highScore);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(HIGH_SCORE, highScore);
        editor.apply();

        mView.invalidate();

        System.out.println("Successfully loaded snapshot from Cloud Save: " + highScore);
    }

    private long getHighScore() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
        return settings.getLong(highScoreKey(), -1);
    }

    private void prepareTiles() {
        for (Tile[] array : grid.field) {
            for (Tile tile : array) {
                if (grid.isCellOccupied(tile)) {
                    tile.setMergedFrom(null);
                }
            }
        }
    }

    private void moveTile(Tile tile, Cell cell) {
        grid.field[tile.getX()][tile.getY()] = null;
        grid.field[cell.getX()][cell.getY()] = tile;
        tile.updatePosition(cell);
    }

    private void saveUndoState(UndoState state) {
        undoHistory.add(state);
        if (undoHistory.size() > MAX_UNDO_HISTORY) {
            undoHistory.remove(0);
        }
    }

    boolean canUndo() {
        return undoCoins > 0 && !undoHistory.isEmpty();
    }

    // Each undo costs one coin and steps back one move, so undos can be chained.
    void revertUndoState() {
        if (canUndo()) {
            undoCoins--;
            UndoState state = undoHistory.remove(undoHistory.size() - 1);
            aGrid.cancelAnimations();
            grid.setValues(state.values);
            score = state.score;
            gameState = state.gameState;
            animateUndo(state);
            mView.refreshLastTime = true;
            mView.resyncTime();
            mView.invalidate();
        }
    }

    // Plays the move backwards: the new tile shrinks away and every tile slides back to where it
    // came from, so merged tiles split up again.
    private void animateUndo(UndoState state) {
        if (state.moves != null) {
            for (int i = 0; i + 3 < state.moves.length; i += 4) {
                int[] extras = {state.moves[i + 2], state.moves[i + 3]};
                aGrid.startAnimation(state.moves[i], state.moves[i + 1], MOVE_ANIMATION,
                        MOVE_ANIMATION_TIME, 0, extras);
            }
        }
        if (state.spawn != null) {
            aGrid.startAnimation(-1, -1, UNSPAWN_GLOBAL_ANIMATION, SPAWN_ANIMATION_TIME, 0,
                    state.spawn);
        }
    }

    // Format: "score:gameState:values:moves:spawn;score:..." with the values column by column as
    // "v,v,...", the moves as "fromX,fromY,toX,toY/..." and the spawn as "x,y,value" or "-".
    String undoHistoryToString() {
        StringBuilder builder = new StringBuilder();
        for (UndoState state : undoHistory) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(state.score).append(':').append(state.gameState).append(':');
            for (int xx = 0; xx < numSquaresX; xx++) {
                for (int yy = 0; yy < numSquaresY; yy++) {
                    if (xx != 0 || yy != 0) {
                        builder.append(',');
                    }
                    builder.append(state.values[xx][yy]);
                }
            }
            builder.append(':');
            if (state.moves != null) {
                for (int i = 0; i + 3 < state.moves.length; i += 4) {
                    if (i != 0) {
                        builder.append('/');
                    }
                    builder.append(state.moves[i]).append(',').append(state.moves[i + 1]).append(',')
                            .append(state.moves[i + 2]).append(',').append(state.moves[i + 3]);
                }
            }
            builder.append(':');
            if (state.spawn != null) {
                builder.append(state.spawn[0]).append(',').append(state.spawn[1]).append(',')
                        .append(state.spawn[2]);
            } else {
                builder.append('-');
            }
        }
        return builder.toString();
    }

    private static int[] parseInts(String data, String separator) {
        String[] parts = data.split(separator);
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i]);
        }
        return result;
    }

    void undoHistoryFromString(@Nullable String data) {
        undoHistory.clear();
        if (TextUtils.isEmpty(data)) {
            return;
        }
        try {
            for (String entry : data.split(";")) {
                String[] parts = entry.split(":");
                String[] cells = parts[2].split(",");
                if (cells.length != numSquaresX * numSquaresY) {
                    continue;
                }
                int[][] values = new int[numSquaresX][numSquaresY];
                for (int xx = 0; xx < numSquaresX; xx++) {
                    for (int yy = 0; yy < numSquaresY; yy++) {
                        values[xx][yy] = Integer.parseInt(cells[xx * numSquaresY + yy]);
                    }
                }
                int[] moves = null;
                int[] spawn = null;
                if (parts.length >= 5) {
                    moves = parts[3].isEmpty() ? null : parseInts(parts[3].replace('/', ','), ",");
                    spawn = parts[4].equals("-") ? null : parseInts(parts[4], ",");
                }
                undoHistory.add(new UndoState(values, Long.parseLong(parts[0]),
                        Integer.parseInt(parts[1]), moves, spawn));
            }
        } catch (RuntimeException e) {
            undoHistory.clear();
        }
    }

    String mergedValuesToString() {
        return TextUtils.join(",", mergedValues);
    }

    void mergedValuesFromString(@Nullable String data) {
        mergedValues.clear();
        if (data == null) {
            return;
        }
        for (String value : TextUtils.split(data, ",")) {
            try {
                mergedValues.add(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    boolean gameWon() {
        return (gameState > 0 && gameState % 2 != 0);
    }

    boolean gameLost() {
        return (gameState == GAME_LOST);
    }

    boolean isActive() {
        return !(gameWon() || gameLost());
    }

    void move(int direction) {
        aGrid.cancelAnimations();
        // 0: up, 1: right, 2: down, 3: left
        if (!isActive()) {
            return;
        }
        int[][] previousValues = grid.getValues();
        long previousScore = score;
        int previousGameState = gameState;
        Cell vector = getVector(direction);
        List<Integer> traversalsX = buildTraversalsX(vector);
        List<Integer> traversalsY = buildTraversalsY(vector);
        boolean moved = false;

        prepareTiles();

        // Where every tile starts, so that an undo can slide it back
        List<Tile> tiles = new ArrayList<>();
        List<Cell> startingCells = new ArrayList<>();
        for (Tile[] array : grid.field) {
            for (Tile tile : array) {
                if (tile != null) {
                    tiles.add(tile);
                    startingCells.add(new Cell(tile.getX(), tile.getY()));
                }
            }
        }

        for (int xx : traversalsX) {
            for (int yy : traversalsY) {
                Cell cell = new Cell(xx, yy);
                Tile tile = grid.getCellContent(cell);

                if (tile != null) {
                    Cell[] positions = findFarthestPosition(cell, vector);
                    Tile next = grid.getCellContent(positions[1]);

                    if (next != null && next.getValue() == tile.getValue() && next.getMergedFrom() == null) {
                        Tile merged = new Tile(positions[1], tile.getValue() * 2);
                        Tile[] temp = {tile, next};
                        merged.setMergedFrom(temp);

                        grid.insertTile(merged);
                        grid.removeTile(tile);

                        // Converge the two tiles' positions
                        tile.updatePosition(positions[1]);

                        int[] extras = {xx, yy};
                        aGrid.startAnimation(merged.getX(), merged.getY(), MOVE_ANIMATION,
                                MOVE_ANIMATION_TIME, 0, extras); //Direction: 0 = MOVING MERGED
                        aGrid.startAnimation(merged.getX(), merged.getY(), MERGE_ANIMATION,
                                SPAWN_ANIMATION_TIME, MOVE_ANIMATION_TIME, null);

                        // Update the score
                        score = score + merged.getValue();
                        highScore = Math.max(score, highScore);
                        if (score >= highScore) {
                            highScore = score;
                            recordHighScore();
                        }

                        // A value merged for the first time in this game earns an undo coin
                        boolean earnedCoin = merged.getValue() >= MIN_COIN_VALUE
                                && mergedValues.add(merged.getValue());
                        if (earnedCoin) {
                            undoCoins++;
                        }
                        mView.showAchievement(merged.getX(), merged.getY(), merged.getValue(), earnedCoin);

                        // The mighty 2048 tile
                        if (merged.getValue() >= winValue() && !gameWon()) {
                            gameState = gameState + GAME_WIN; // Set win state
                            endGame();
                        }
                    } else {
                        moveTile(tile, positions[0]);
                        int[] extras = {xx, yy, 0};
                        aGrid.startAnimation(positions[0].getX(), positions[0].getY(), MOVE_ANIMATION, MOVE_ANIMATION_TIME, 0, extras); //Direction: 1 = MOVING NO MERGE
                    }

                    if (!positionsEqual(cell, tile)) {
                        moved = true;
                    }
                }
            }
        }

        if (moved) {
            int[] moves = new int[tiles.size() * 4];
            for (int i = 0; i < tiles.size(); i++) {
                moves[i * 4] = startingCells.get(i).getX();
                moves[i * 4 + 1] = startingCells.get(i).getY();
                moves[i * 4 + 2] = tiles.get(i).getX();
                moves[i * 4 + 3] = tiles.get(i).getY();
            }
            Tile spawned = addRandomTile();
            int[] spawn = spawned == null ? null
                    : new int[]{spawned.getX(), spawned.getY(), spawned.getValue()};
            saveUndoState(new UndoState(previousValues, previousScore, previousGameState, moves, spawn));
            checkLose();
        }
        mView.resyncTime();
        mView.invalidate();
    }

    private void checkLose() {
        if (!movesAvailable() && !gameWon()) {
            gameState = GAME_LOST;
            endGame();
        }
    }

    private void endGame() {
        recordGame();
        aGrid.startAnimation(-1, -1, FADE_GLOBAL_ANIMATION, NOTIFICATION_ANIMATION_TIME, NOTIFICATION_DELAY_TIME, null);
        if (score >= highScore) {
            highScore = score;
            recordHighScore();
        }
    }

    private Cell getVector(int direction) {
        Cell[] map = {
                new Cell(0, -1), // up
                new Cell(1, 0),  // right
                new Cell(0, 1),  // down
                new Cell(-1, 0)  // left
        };
        return map[direction];
    }

    private List<Integer> buildTraversalsX(Cell vector) {
        List<Integer> traversals = new ArrayList<>();

        for (int xx = 0; xx < numSquaresX; xx++) {
            traversals.add(xx);
        }
        if (vector.getX() == 1) {
            Collections.reverse(traversals);
        }

        return traversals;
    }

    private List<Integer> buildTraversalsY(Cell vector) {
        List<Integer> traversals = new ArrayList<>();

        for (int xx = 0; xx < numSquaresY; xx++) {
            traversals.add(xx);
        }
        if (vector.getY() == 1) {
            Collections.reverse(traversals);
        }

        return traversals;
    }

    private Cell[] findFarthestPosition(Cell cell, Cell vector) {
        Cell previous;
        Cell nextCell = new Cell(cell.getX(), cell.getY());
        do {
            previous = nextCell;
            nextCell = new Cell(previous.getX() + vector.getX(),
                    previous.getY() + vector.getY());
        } while (grid.isCellWithinBounds(nextCell) && grid.isCellAvailable(nextCell));

        return new Cell[]{previous, nextCell};
    }

    private boolean movesAvailable() {
        return grid.isCellsAvailable() || tileMatchesAvailable();
    }

    private boolean tileMatchesAvailable() {
        Tile tile;

        for (int xx = 0; xx < numSquaresX; xx++) {
            for (int yy = 0; yy < numSquaresY; yy++) {
                tile = grid.getCellContent(new Cell(xx, yy));

                if (tile != null) {
                    for (int direction = 0; direction < 4; direction++) {
                        Cell vector = getVector(direction);
                        Cell cell = new Cell(xx + vector.getX(), yy + vector.getY());

                        Tile other = grid.getCellContent(cell);

                        if (other != null && other.getValue() == tile.getValue()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean positionsEqual(Cell first, Cell second) {
        return first.getX() == second.getX() && first.getY() == second.getY();
    }

    private int winValue() {
        if (!canContinue()) {
            return endingMaxValue;
        } else {
            return startingMaxValue;
        }
    }

    void setEndlessMode() {
        gameState = GAME_ENDLESS;
        mView.invalidate();
        mView.refreshLastTime = true;
    }

    boolean canContinue() {
        return !(gameState == GAME_ENDLESS || gameState == GAME_ENDLESS_WON);
    }

    private static class UndoState {
        final int[][] values;
        final long score;
        final int gameState;
        // fromX, fromY, toX, toY for every tile of the move, null if unknown
        @Nullable final int[] moves;
        // x, y, value of the tile spawned after the move, null if none
        @Nullable final int[] spawn;

        UndoState(int[][] values, long score, int gameState, @Nullable int[] moves, @Nullable int[] spawn) {
            this.values = values;
            this.score = score;
            this.gameState = gameState;
            this.moves = moves;
            this.spawn = spawn;
        }
    }
}
