package com.tpcstld.twozerogame;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class MainView extends View {

    //Internal Constants
    static final int BASE_ANIMATION_TIME = 100000000;
    private static final String TAG = MainView.class.getSimpleName();
    private static final float MERGING_ACCELERATION = (float) -0.5;
    private static final float INITIAL_VELOCITY = (1 - MERGING_ACCELERATION) / 4;
    // Achievements start once the merging tiles have met.
    private static final long ACHIEVEMENT_DELAY_TIME = BASE_ANIMATION_TIME;
    private static final long COIN_PULSE_TIME = BASE_ANIMATION_TIME * 6;
    // Merges of values that were merged before get a small burst from this value on
    private static final int BURST_MIN_VALUE = 256;
    // First merges of 1024 and above also get a random cheer, a flash and a shake
    private static final int CHEER_MIN_VALUE = 1024;
    private static final long CHEER_ANIMATION_TIME = BASE_ANIMATION_TIME * 24L;
    private static final long FLASH_TIME = BASE_ANIMATION_TIME * 3L;
    private static final long CONFETTI_TIME = BASE_ANIMATION_TIME * 40L;
    private static final long FIREWORK_TIME = BASE_ANIMATION_TIME * 14L;
    private static final long WAVE_TIME = BASE_ANIMATION_TIME * 3L;
    public final int numCellTypes = 21;
    private final BitmapDrawable[] bitmapCell = new BitmapDrawable[numCellTypes];
    private final int[] cellColors = new int[numCellTypes];
    public final MainGame game;
    //Internal variables
    private final Paint paint = new Paint();
    private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF effectRect = new RectF();
    private final Path effectPath = new Path();
    private final ArrayList<Achievement> achievements = new ArrayList<>();
    private final Random random = new Random();
    private long coinEarnedTime = 0;
    private final Paint cheerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable
    private String cheerText;
    private int cheerIndex;
    private long cheerStartTime;
    private int lastCheer = -1;
    public boolean hasSaveState = false;
    public boolean continueButtonEnabled = false;
    public int startingX;
    public int startingY;
    public int endingX;
    public int endingY;
    //Icon variables
    public int sYIcons;
    public int sXNewGame;
    public int sXUndo;
    public int sXBoardSize;
    public int sXHighScores;
    public int sYHighScores;
    public int eXHighScores;
    public int eYHighScores;
    public int iconSize;
    //Misc
    boolean refreshLastTime = true;
    boolean showHelp;
    //Timing
    private long lastFPSTime = System.nanoTime();
    //Text
    private float titleTextSize;
    private float bodyTextSize;
    private float headerTextSize;
    private float instructionsTextSize;
    private float gameOverTextSize;
    private float buttonTextSize;
    private float footerTextSize;
    private float cheerTextSize;
    private int footerY;
    //Layout variables
    private int cellSize = 0;
    private float textSize = 0;
    private float cellTextSize = 0;
    private int gridWidth = 0;
    private int textPaddingSize;
    private int iconPaddingSize;
    //Assets
    private Drawable backgroundRectangle;
    private Drawable lightUpRectangle;
    private Drawable fadeRectangle;
    private Drawable undoIcon;
    private Bitmap background = null;
    private BitmapDrawable loseGameOverlay;
    private BitmapDrawable winGameContinueOverlay;
    private BitmapDrawable winGameFinalOverlay;
    //Text variables
    private int sYAll;
    private int titleStartYAll;
    private int bodyStartYAll;
    private int eYAll;
    private int titleWidthHighScore;
    private int titleWidthScore;

    public MainView(Context context) {
        super(context);

        Resources resources = context.getResources();
        //Loading resources
        game = new MainGame(context, this);
        try {
            //Getting assets
            backgroundRectangle = ResourcesCompat.getDrawable(resources, R.drawable.background_rectangle, null);
            lightUpRectangle = ResourcesCompat.getDrawable(resources, R.drawable.light_up_rectangle, null);
            fadeRectangle = ResourcesCompat.getDrawable(resources, R.drawable.fade_rectangle, null);
            undoIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_action_undo, null).mutate();
            this.setBackgroundColor(resources.getColor(R.color.background));
            Typeface font = Typeface.createFromAsset(resources.getAssets(), "ClearSans-Bold.ttf");
            paint.setTypeface(font);
            paint.setAntiAlias(true);
            cheerPaint.setTypeface(font);
        } catch (Exception e) {
            Log.e(TAG, "Error getting assets?", e);
        }
        setOnTouchListener(new InputListener(this));
        game.newGame();
    }

    private static int log2(int n) {
        if (n <= 0) throw new IllegalArgumentException();
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    @Override
    public void onDraw(Canvas canvas) {
        //Reset the transparency of the screen
        long currentTime = System.nanoTime();
        removeFinishedAchievements(currentTime);
        canvas.save();
        shakeScreen(canvas, currentTime);

        canvas.drawBitmap(background, 0, 0, paint);

        drawScoreText(canvas);

        drawUndoButton(canvas);

        if (!game.isActive() && !game.aGrid.isAnimationActive()) {
            drawNewGameButton(canvas, true);
        }

        drawEffectsBelowTiles(canvas, currentTime);

        drawRemovedTiles(canvas);

        drawCells(canvas, currentTime);

        drawEffectsAboveTiles(canvas, currentTime);

        if (!game.isActive()) {
            drawEndGameState(canvas);
        }

        if (!game.canContinue()) {
            drawEndlessText(canvas);
        }

        drawEffectsOnTop(canvas, currentTime);

        drawCheer(canvas);
        canvas.restore();

        //Refresh the screen if there is still an animation running
        if (game.aGrid.isAnimationActive()) {
            invalidate(startingX, startingY, endingX, endingY);
            tick();
            //Refresh one last time on game end.
        } else if (!game.isActive() && refreshLastTime) {
            invalidate();
            refreshLastTime = false;
        }
        if (!achievements.isEmpty() || isCoinPulseActive() || cheerText != null) {
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldW, int oldH) {
        super.onSizeChanged(width, height, oldW, oldH);
        createLayout(width, height);
    }

    void onBoardSizeChanged() {
        if (getWidth() > 0 && getHeight() > 0) {
            createLayout(getWidth(), getHeight());
        }
        invalidate();
    }

    private void createLayout(int width, int height) {
        getLayout(width, height);
        createBitmapCells();
        createBackgroundBitmap(width, height);
        createOverlays();
    }

    private void drawDrawable(Canvas canvas, @Nullable Drawable draw, int startingX, int startingY, int endingX, int endingY) {
        if (draw == null) return;

        draw.setBounds(startingX, startingY, endingX, endingY);
        draw.draw(canvas);
    }

    private void drawCellText(Canvas canvas, int value) {
        int textShiftY = centerText();
        if (value >= 8) {
            paint.setColor(getResources().getColor(R.color.text_white));
        } else {
            paint.setColor(getResources().getColor(R.color.text_black));
        }
        canvas.drawText("" + value, (float) cellSize / 2, (float) cellSize / 2 - textShiftY, paint);
    }

    private void drawScoreText(Canvas canvas) {
        //Drawing the score text: Ver 2
        paint.setTextSize(bodyTextSize);
        paint.setTextAlign(Paint.Align.CENTER);

        int bodyWidthHighScore = (int) (paint.measureText("" + game.highScore));
        int bodyWidthScore = (int) (paint.measureText("" + game.score));

        int textWidthHighScore = Math.max(titleWidthHighScore, bodyWidthHighScore) + textPaddingSize * 2;
        int textWidthScore = Math.max(titleWidthScore, bodyWidthScore) + textPaddingSize * 2;

        int textMiddleHighScore = textWidthHighScore / 2;
        int textMiddleScore = textWidthScore / 2;

        int eXHighScore = endingX;
        int sXHighScore = eXHighScore - textWidthHighScore;

        int eXScore = sXHighScore - textPaddingSize;
        int sXScore = eXScore - textWidthScore;

        //Outputting high-scores box
        backgroundRectangle.setBounds(sXHighScore, sYAll, eXHighScore, eYAll);
        backgroundRectangle.draw(canvas);
        paint.setTextSize(titleTextSize);
        paint.setColor(getResources().getColor(R.color.text_brown));
        canvas.drawText(getResources().getString(R.string.high_score), sXHighScore + textMiddleHighScore, titleStartYAll, paint);
        paint.setTextSize(bodyTextSize);
        paint.setColor(getResources().getColor(R.color.text_white));
        canvas.drawText(String.valueOf(game.highScore), sXHighScore + textMiddleHighScore, bodyStartYAll, paint);


        //Outputting scores box
        backgroundRectangle.setBounds(sXScore, sYAll, eXScore, eYAll);
        backgroundRectangle.draw(canvas);
        paint.setTextSize(titleTextSize);
        paint.setColor(getResources().getColor(R.color.text_brown));
        canvas.drawText(getResources().getString(R.string.score), sXScore + textMiddleScore, titleStartYAll, paint);
        paint.setTextSize(bodyTextSize);
        paint.setColor(getResources().getColor(R.color.text_white));
        canvas.drawText(String.valueOf(game.score), sXScore + textMiddleScore, bodyStartYAll, paint);
    }

    private void drawNewGameButton(Canvas canvas, boolean lightUp) {

        if (lightUp) {
            drawDrawable(canvas,
                    lightUpRectangle,
                    sXNewGame,
                    sYIcons,
                    sXNewGame + iconSize,
                    sYIcons + iconSize
            );
        } else {
            drawDrawable(canvas,
                    backgroundRectangle,
                    sXNewGame,
                    sYIcons, sXNewGame + iconSize,
                    sYIcons + iconSize
            );
        }

        drawDrawable(canvas,
                ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_refresh, null),
                sXNewGame + iconPaddingSize,
                sYIcons + iconPaddingSize,
                sXNewGame + iconSize - iconPaddingSize,
                sYIcons + iconSize - iconPaddingSize
        );
    }

    private void drawUndoButton(Canvas canvas) {
        // Faded out while there are no undo coins to spend
        int alpha = game.canUndo() ? 255 : 90;

        backgroundRectangle.setAlpha(alpha);
        drawDrawable(canvas,
                backgroundRectangle,
                sXUndo,
                sYIcons, sXUndo + iconSize,
                sYIcons + iconSize
        );
        backgroundRectangle.setAlpha(255);

        undoIcon.setAlpha(alpha);
        drawDrawable(canvas,
                undoIcon,
                sXUndo + iconPaddingSize,
                sYIcons + iconPaddingSize,
                sXUndo + iconSize - iconPaddingSize,
                sYIcons + iconSize - iconPaddingSize
        );

        if (game.undoCoins > 0) {
            drawUndoCoins(canvas);
        }
    }

    // A badge with the number of undo coins, which pops when a coin is earned.
    private void drawUndoCoins(Canvas canvas) {
        float scale = 1;
        long sinceEarned = System.nanoTime() - coinEarnedTime;
        if (sinceEarned >= 0 && sinceEarned < COIN_PULSE_TIME) {
            scale += 0.6f * (float) Math.sin(Math.PI * sinceEarned / COIN_PULSE_TIME);
        }
        float radius = iconSize * 0.27f * scale;
        float centerX = sXUndo + iconSize - iconSize * 0.08f;
        float centerY = sYIcons + iconSize * 0.08f;

        effectPaint.setStyle(Paint.Style.FILL);
        effectPaint.setColor(getResources().getColor(R.color.icon_background));
        canvas.drawCircle(centerX, centerY, radius, effectPaint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(getResources().getColor(R.color.text_white));
        paint.setTextSize(radius * 1.25f);
        canvas.drawText(String.valueOf(game.undoCoins), centerX, centerY - centerText(), paint);
    }

    private boolean isCoinPulseActive() {
        return System.nanoTime() - coinEarnedTime < COIN_PULSE_TIME;
    }

    private void drawHighScoresButton(Canvas canvas) {
        drawDrawable(canvas,
                backgroundRectangle,
                sXHighScores,
                sYHighScores, eXHighScores,
                eYHighScores
        );

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(getResources().getColor(R.color.text_white));
        paint.setTextSize(buttonTextSize);
        canvas.drawText(getResources().getString(R.string.high_scores),
                (sXHighScores + eXHighScores) / 2f, (sYHighScores + eYHighScores) / 2f - centerText(), paint);
    }

    private void drawFooter(Canvas canvas, int width) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(getResources().getColor(R.color.text_black));
        paint.setTextSize(footerTextSize);
        canvas.drawText(getResources().getString(R.string.footer), width / 2f, footerY, paint);
    }

    private void drawBoardSizeButton(Canvas canvas) {
        drawDrawable(canvas,
                backgroundRectangle,
                sXBoardSize,
                sYIcons, sXBoardSize + iconSize,
                sYIcons + iconSize
        );

        String label = game.numSquaresX + "×" + game.numSquaresY;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(getResources().getColor(R.color.text_white));
        paint.setTextSize(iconSize);
        paint.setTextSize(iconSize * (iconSize - iconPaddingSize * 2) / paint.measureText(label));
        canvas.drawText(label, sXBoardSize + iconSize / 2f, sYIcons + iconSize / 2f - centerText(), paint);
    }

    private void drawHeader(Canvas canvas) {
        paint.setTextSize(headerTextSize);
        paint.setColor(getResources().getColor(R.color.text_black));
        paint.setTextAlign(Paint.Align.LEFT);
        int textShiftY = centerText() * 2;
        int headerStartY = sYAll - textShiftY;
        canvas.drawText(getResources().getString(R.string.header), startingX, headerStartY, paint);
    }

    private void drawInstructions(Canvas canvas) {
        paint.setTextSize(instructionsTextSize);
        paint.setTextAlign(Paint.Align.LEFT);
        int textShiftY = centerText() * 2;
        canvas.drawText(getResources().getString(R.string.instructions),
                startingX, endingY - textShiftY + textPaddingSize, paint);
    }

    private void drawBackground(Canvas canvas) {
        drawDrawable(canvas, backgroundRectangle, startingX, startingY, endingX, endingY);
    }

    //Renders the set of 16 background squares.
    private void drawBackgroundGrid(Canvas canvas) {
        Resources resources = getResources();
        Drawable backgroundCell = ResourcesCompat.getDrawable(resources, R.drawable.cell_rectangle, null);
        // Outputting the game grid
        for (int xx = 0; xx < game.numSquaresX; xx++) {
            for (int yy = 0; yy < game.numSquaresY; yy++) {
                int sX = startingX + gridWidth + (cellSize + gridWidth) * xx;
                int eX = sX + cellSize;
                int sY = startingY + gridWidth + (cellSize + gridWidth) * yy;
                int eY = sY + cellSize;

                drawDrawable(canvas, backgroundCell, sX, sY, eX, eY);
            }
        }
    }

    private void drawCells(Canvas canvas, long currentTime) {
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.CENTER);
        // Outputting the individual cells
        for (int xx = 0; xx < game.numSquaresX; xx++) {
            for (int yy = 0; yy < game.numSquaresY; yy++) {
                int sX = startingX + gridWidth + (cellSize + gridWidth) * xx;
                int eX = sX + cellSize;
                int sY = startingY + gridWidth + (cellSize + gridWidth) * yy;
                int eY = sY + cellSize;

                Tile currentTile = game.grid.getCellContent(xx, yy);
                if (currentTile != null) {
                    //Get and represent the value of the tile
                    int value = currentTile.getValue();
                    int index = log2(value);

                    //Check for any active animations
                    ArrayList<AnimationCell> aArray = game.aGrid.getAnimationCell(xx, yy);
                    boolean animated = false;
                    for (int i = aArray.size() - 1; i >= 0; i--) {
                        AnimationCell aCell = aArray.get(i);
                        //If this animation is not active, skip it
                        if (aCell.getAnimationType() == MainGame.SPAWN_ANIMATION) {
                            animated = true;
                        }
                        if (!aCell.isActive()) {
                            continue;
                        }

                        if (aCell.getAnimationType() == MainGame.SPAWN_ANIMATION) { // Spawning animation
                            double percentDone = aCell.getPercentageDone();
                            float textScaleSize = (float) (percentDone);
                            paint.setTextSize(textSize * textScaleSize);

                            float cellScaleSize = (float) cellSize / 2 * (1 - textScaleSize);
                            bitmapCell[index].setBounds((int) (sX + cellScaleSize), (int) (sY + cellScaleSize), (int) (eX - cellScaleSize), (int) (eY - cellScaleSize));
                            bitmapCell[index].draw(canvas);
                        } else if (aCell.getAnimationType() == MainGame.MERGE_ANIMATION) { // Merging Animation
                            double percentDone = aCell.getPercentageDone();
                            // Bigger values pop harder, up to almost three times the classic bounce
                            float intensity = Math.min(3, 1 + Math.max(0, index - 6) * 0.3f);
                            float textScaleSize = (float) (1 + intensity * (INITIAL_VELOCITY * percentDone
                                    + MERGING_ACCELERATION * percentDone * percentDone / 2));
                            paint.setTextSize(textSize * textScaleSize);

                            float cellScaleSize = (float) cellSize / 2 * (1 - textScaleSize);
                            bitmapCell[index].setBounds((int) (sX + cellScaleSize), (int) (sY + cellScaleSize), (int) (eX - cellScaleSize), (int) (eY - cellScaleSize));
                            bitmapCell[index].draw(canvas);
                        } else if (aCell.getAnimationType() == MainGame.MOVE_ANIMATION) {  // Moving animation
                            double percentDone = aCell.getPercentageDone();
                            int tempIndex = index;
                            if (aArray.size() >= 2) {
                                tempIndex = tempIndex - 1;
                            }
                            int previousX = aCell.extras[0];
                            int previousY = aCell.extras[1];
                            int currentX = currentTile.getX();
                            int currentY = currentTile.getY();
                            int dX = (int) ((currentX - previousX) * (cellSize + gridWidth) * (percentDone - 1) * 1.0);
                            int dY = (int) ((currentY - previousY) * (cellSize + gridWidth) * (percentDone - 1) * 1.0);
                            bitmapCell[tempIndex].setBounds(sX + dX, sY + dY, eX + dX, eY + dY);
                            bitmapCell[tempIndex].draw(canvas);
                        }
                        animated = true;
                    }

                    //No active animations? Just draw the cell, lifted by any passing wave
                    if (!animated) {
                        int lift = (int) (cellSize * (waveScale(xx, yy, currentTime) - 1) / 2);
                        bitmapCell[index].setBounds(sX - lift, sY - lift, eX + lift, eY + lift);
                        bitmapCell[index].draw(canvas);
                    }
                }
            }
        }
    }

    // Called for every merge. First merges that earned a coin are celebrated, the more the bigger
    // the value; repeated merges of big values get a small burst.
    void showAchievement(int x, int y, int value, boolean earnedCoin) {
        if (!earnedCoin && value < BURST_MIN_VALUE) {
            return;
        }
        long startTime = System.nanoTime() + ACHIEVEMENT_DELAY_TIME;
        Achievement achievement = new Achievement(x, y, log2(value), earnedCoin, startTime, random);
        achievements.add(achievement);
        if (earnedCoin) {
            coinEarnedTime = startTime;
        }
        if (earnedCoin && value >= CHEER_MIN_VALUE) {
            String[] cheers = getResources().getStringArray(R.array.cheers);
            int cheer = random.nextInt(cheers.length);
            if (cheer == lastCheer) {
                // Never the same cheer twice in a row
                cheer = (cheer + 1) % cheers.length;
            }
            lastCheer = cheer;
            cheerText = cheers[cheer];
            cheerIndex = log2(value);
            cheerStartTime = startTime;

            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            for (long delay : achievement.fireworkDelays) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    }
                }, (ACHIEVEMENT_DELAY_TIME + delay) / 1000000);
            }
        }
        invalidate();
    }

    void clearAchievements() {
        achievements.clear();
        coinEarnedTime = 0;
        cheerText = null;
    }

    private void removeFinishedAchievements(long currentTime) {
        Iterator<Achievement> iterator = achievements.iterator();
        while (iterator.hasNext()) {
            Achievement achievement = iterator.next();
            if (currentTime - achievement.startTime > achievement.totalTime) {
                iterator.remove();
            }
        }
    }

    // Big first merges shake the whole screen for a moment.
    private void shakeScreen(Canvas canvas, long currentTime) {
        float shakeX = 0;
        float shakeY = 0;
        for (Achievement achievement : achievements) {
            float progress = (float) (currentTime - achievement.startTime) / achievement.shakeTime;
            if (achievement.shake == 0 || progress < 0 || progress > 1) {
                continue;
            }
            float amplitude = gridWidth * achievement.shake * (1 - progress) * (1 - progress);
            double seconds = (currentTime - achievement.startTime) / 1e9;
            shakeX += amplitude * (float) (Math.sin(seconds * 83) + Math.sin(seconds * 131) / 2) / 1.5f;
            shakeY += amplitude * (float) (Math.cos(seconds * 97) + Math.sin(seconds * 151) / 2) / 1.5f;
        }
        canvas.translate(shakeX, shakeY);
    }

    // How much bigger the tile at (x, y) is drawn while the waves of huge first merges pass it.
    private float waveScale(int x, int y, long currentTime) {
        float scale = 1;
        for (Achievement achievement : achievements) {
            float distance = (float) Math.hypot(x - achievement.getX(), y - achievement.getY());
            for (int wave = 0; wave < achievement.waves; wave++) {
                long delay = (long) (distance * BASE_ANIMATION_TIME * 0.7f) + wave * BASE_ANIMATION_TIME * 5L;
                float progress = (float) (currentTime - achievement.startTime - delay) / WAVE_TIME;
                if (progress > 0 && progress < 1) {
                    scale += 0.2f * (float) Math.sin(Math.PI * progress);
                }
            }
        }
        return scale;
    }

    private int achievementColor(Achievement achievement, int variant, float seconds) {
        if (achievement.rainbow) {
            return Color.HSVToColor(new float[]{(variant * 47 + seconds * 240) % 360, 0.65f, 1});
        }
        return cellColors[Math.min(achievement.index, numCellTypes - 1)];
    }

    // Palette for confetti and fireworks: the tile colours from 8 to 2048, and white
    private int paletteColor(int index) {
        return index < 0 ? getResources().getColor(R.color.text_white) : cellColors[index];
    }

    // Small celebrations stay on the board, from 2048 on they spill over the whole screen.
    private void clipToBoard(Canvas canvas, Achievement achievement) {
        if (!achievement.unclipped) {
            canvas.clipRect(startingX, startingY, endingX, endingY);
        }
    }

    // A soft glow of the tile colour, and for the biggest merges a turning sunburst behind it.
    private void drawEffectsBelowTiles(Canvas canvas, long currentTime) {
        for (Achievement achievement : achievements) {
            float progress = (float) (currentTime - achievement.startTime) / achievement.effectTime;
            if (progress < 0 || progress > 1) {
                continue;
            }
            float seconds = (currentTime - achievement.startTime) / 1e9f;
            int color = achievementColor(achievement, 0, seconds);
            float centerX = startingX + gridWidth + (cellSize + gridWidth) * achievement.getX() + cellSize / 2f;
            float centerY = startingY + gridWidth + (cellSize + gridWidth) * achievement.getY() + cellSize / 2f;
            canvas.save();
            clipToBoard(canvas, achievement);

            if (achievement.sunburst) {
                int rays = 16;
                float radius = cellSize * (2 + 5 * easeOut(Math.min(1, progress * 2)));
                int alpha = (int) (150 * Math.min(1, (1 - progress) * 4));
                effectPaint.setStyle(Paint.Style.FILL);
                effectPaint.setShader(new RadialGradient(centerX, centerY, radius,
                        new int[]{withAlpha(color, alpha), withAlpha(color, 0)}, null, Shader.TileMode.CLAMP));
                effectPath.reset();
                for (int ray = 0; ray < rays; ray++) {
                    double angle = Math.toRadians(seconds * 40 + ray * 360f / rays);
                    double halfWidth = Math.PI / rays / 2;
                    effectPath.moveTo(centerX, centerY);
                    effectPath.lineTo(centerX + (float) Math.cos(angle - halfWidth) * radius,
                            centerY + (float) Math.sin(angle - halfWidth) * radius);
                    effectPath.lineTo(centerX + (float) Math.cos(angle + halfWidth) * radius,
                            centerY + (float) Math.sin(angle + halfWidth) * radius);
                    effectPath.close();
                }
                canvas.drawPath(effectPath, effectPaint);
            }

            float radius = cellSize * (0.7f + achievement.glowSpread * easeOut(progress));
            int alpha = (int) (achievement.glowAlpha * (1 - progress) * (1 - progress));
            effectPaint.setStyle(Paint.Style.FILL);
            effectPaint.setShader(new RadialGradient(centerX, centerY, radius,
                    new int[]{withAlpha(color, alpha), withAlpha(color, alpha / 2), withAlpha(color, 0)},
                    new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
            canvas.drawCircle(centerX, centerY, radius, effectPaint);
            effectPaint.setShader(null);
            canvas.restore();
        }
    }

    // Rings expanding out of the tile and sparks flying away from it.
    private void drawEffectsAboveTiles(Canvas canvas, long currentTime) {
        float cornerRadius = getResources().getDisplayMetrics().density * 5;
        for (Achievement achievement : achievements) {
            float progress = (float) (currentTime - achievement.startTime) / achievement.effectTime;
            if (progress < 0 || progress > 1) {
                continue;
            }
            float seconds = (currentTime - achievement.startTime) / 1e9f;
            float sX = startingX + gridWidth + (cellSize + gridWidth) * achievement.getX();
            float sY = startingY + gridWidth + (cellSize + gridWidth) * achievement.getY();
            float centerX = sX + cellSize / 2f;
            float centerY = sY + cellSize / 2f;
            canvas.save();
            clipToBoard(canvas, achievement);

            effectPaint.setStyle(Paint.Style.STROKE);
            float ringStagger = 0.45f / achievement.rings;
            for (int ring = 0; ring < achievement.rings; ring++) {
                float ringProgress = (progress - ring * ringStagger) / 0.7f;
                if (ringProgress <= 0 || ringProgress >= 1) {
                    continue;
                }
                float spread = cellSize * achievement.ringSpread * easeOut(ringProgress);
                effectPaint.setStrokeWidth(Math.max(1, gridWidth * achievement.ringWidth * (1 - ringProgress)));
                effectPaint.setColor(withAlpha(achievementColor(achievement, ring, seconds), (int) (235 * (1 - ringProgress))));
                effectRect.set(sX - spread, sY - spread, sX + cellSize + spread, sY + cellSize + spread);
                canvas.drawRoundRect(effectRect, cornerRadius + spread * 0.6f, cornerRadius + spread * 0.6f, effectPaint);
            }

            effectPaint.setStyle(Paint.Style.FILL);
            int sparkAlpha = (int) (255 * (1 - progress));
            float sparkSize = gridWidth * achievement.sparkSize * (1 - progress * 0.6f);
            for (int i = 0; i < achievement.sparkAngles.length; i++) {
                float distance = cellSize * (0.45f + achievement.sparkSpeeds[i] * easeOut(progress));
                float angle = achievement.sparkAngles[i];
                float pX = centerX + (float) Math.cos(angle) * distance;
                float pY = centerY + (float) Math.sin(angle) * distance;
                int sparkColor = i % 2 == 0 ? achievementColor(achievement, i, seconds)
                        : getResources().getColor(R.color.text_white);
                effectPaint.setColor(withAlpha(sparkColor, sparkAlpha));
                canvas.save();
                canvas.rotate((float) Math.toDegrees(angle) + progress * 360, pX, pY);
                canvas.drawRect(pX - sparkSize, pY - sparkSize, pX + sparkSize, pY + sparkSize, effectPaint);
                canvas.restore();
            }
            canvas.restore();
        }
    }

    // Over everything else: a white flash, confetti raining down and fireworks.
    private void drawEffectsOnTop(Canvas canvas, long currentTime) {
        int width = getWidth();
        int height = getHeight();
        for (Achievement achievement : achievements) {
            long elapsed = currentTime - achievement.startTime;
            if (elapsed < 0) {
                continue;
            }
            float seconds = elapsed / 1e9f;

            if (achievement.flash > 0 && elapsed < FLASH_TIME) {
                float fade = 1 - (float) elapsed / FLASH_TIME;
                effectPaint.setStyle(Paint.Style.FILL);
                effectPaint.setColor(withAlpha(Color.WHITE, (int) (255 * achievement.flash * fade * fade)));
                if (achievement.unclipped) {
                    canvas.drawRect(-gridWidth * 4, -gridWidth * 4, width + gridWidth * 4, height + gridWidth * 4, effectPaint);
                } else {
                    canvas.drawRect(startingX, startingY, endingX, endingY, effectPaint);
                }
            }

            if (elapsed < CONFETTI_TIME) {
                float fade = Math.min(1, (1 - (float) elapsed / CONFETTI_TIME) * 4);
                effectPaint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < achievement.confettiX.length; i++) {
                    float y = (achievement.confettiY[i] + achievement.confettiSpeed[i] * seconds) * height;
                    if (y < -gridWidth * 2 || y > height + gridWidth * 2) {
                        continue;
                    }
                    float phase = achievement.confettiPhase[i];
                    float x = achievement.confettiX[i] * width + (float) Math.sin(phase + seconds * 3) * cellSize * 0.3f;
                    float size = gridWidth * achievement.confettiSize[i];
                    // Paper flutter: the piece turns its narrow side to us and back
                    float flutter = Math.abs((float) Math.cos(phase + seconds * 7));
                    effectPaint.setColor(withAlpha(paletteColor(achievement.confettiColor[i]), (int) (255 * fade)));
                    canvas.save();
                    canvas.rotate(phase * 57 + seconds * achievement.confettiSpin[i], x, y);
                    canvas.drawRect(x - size * flutter, y - size * 0.6f, x + size * flutter, y + size * 0.6f, effectPaint);
                    canvas.restore();
                }
            }

            for (int burst = 0; burst < achievement.fireworkDelays.length; burst++) {
                float progress = (float) (elapsed - achievement.fireworkDelays[burst]) / FIREWORK_TIME;
                if (progress < 0 || progress > 1) {
                    continue;
                }
                float centerX = achievement.fireworkX[burst] * width;
                float centerY = achievement.fireworkY[burst] * height;
                int color = paletteColor(achievement.fireworkColors[burst]);
                int alpha = (int) (255 * (1 - progress));
                effectPaint.setStyle(Paint.Style.FILL);
                if (progress < 0.12f) {
                    effectPaint.setColor(withAlpha(Color.WHITE, (int) (255 * (1 - progress / 0.12f))));
                    canvas.drawCircle(centerX, centerY, cellSize * 0.5f * progress / 0.12f, effectPaint);
                }
                int particles = 28;
                float radius = cellSize * 2.2f * easeOut(progress);
                float drop = cellSize * 0.9f * progress * progress;
                for (int i = 0; i < particles; i++) {
                    double angle = 2 * Math.PI * i / particles + burst;
                    float speed = i % 3 == 0 ? 0.7f : 1;
                    float pX = centerX + (float) Math.cos(angle) * radius * speed;
                    float pY = centerY + (float) Math.sin(angle) * radius * speed + drop;
                    effectPaint.setColor(withAlpha(i % 4 == 0 ? Color.WHITE : color, alpha));
                    canvas.drawCircle(pX, pY, gridWidth * 0.45f * (1 - progress * 0.5f), effectPaint);
                }
            }
        }
    }

    // The cheer pops in over the upper part of the board, tilted and wobbling a little, then
    // floats up and fades out.
    private void drawCheer(Canvas canvas) {
        if (cheerText == null) {
            return;
        }
        float progress = (float) (System.nanoTime() - cheerStartTime) / CHEER_ANIMATION_TIME;
        if (progress > 1) {
            cheerText = null;
            return;
        } else if (progress < 0) {
            return;
        }

        float scale = 1;
        if (progress < 0.12f) {
            scale = 0.3f + 0.9f * easeOut(progress / 0.12f);
        } else if (progress < 0.22f) {
            scale = 1.2f - 0.2f * (progress - 0.12f) / 0.1f;
        }
        int alpha = 255;
        if (progress < 0.08f) {
            alpha = (int) (255 * progress / 0.08f);
        } else if (progress > 0.7f) {
            alpha = (int) (255 * (1 - progress) / 0.3f);
        }

        float centerX = (startingX + endingX) / 2f;
        float centerY = startingY + (endingY - startingY) * (0.3f - 0.08f * progress);
        cheerPaint.setTextAlign(Paint.Align.CENTER);
        float size = cheerTextSize * (1 + 0.12f * Math.max(0, cheerIndex - 10));
        cheerPaint.setTextSize(size);
        float maxWidth = (endingX - startingX) * 0.9f;
        float width = cheerPaint.measureText(cheerText);
        if (width > maxWidth) {
            cheerPaint.setTextSize(size * maxWidth / width);
        }
        float baseline = centerY - (cheerPaint.descent() + cheerPaint.ascent()) / 2;

        int color = cellColors[Math.min(cheerIndex, numCellTypes - 1)];
        int outline = Color.rgb(Color.red(color) * 7 / 10, Color.green(color) * 7 / 10, Color.blue(color) * 7 / 10);
        if (cheerIndex >= 12) {
            outline = Color.HSVToColor(new float[]{(progress * 720) % 360, 0.8f, 0.75f});
        }

        canvas.save();
        canvas.rotate(-6 + 2 * (float) Math.sin(progress * Math.PI * 4), centerX, centerY);
        canvas.scale(scale, scale, centerX, centerY);
        cheerPaint.setStyle(Paint.Style.STROKE);
        cheerPaint.setStrokeJoin(Paint.Join.ROUND);
        cheerPaint.setStrokeWidth(cheerPaint.getTextSize() * 0.14f);
        cheerPaint.setColor(withAlpha(outline, alpha));
        cheerPaint.setShadowLayer(gridWidth, 0, gridWidth / 2f, withAlpha(Color.BLACK, alpha / 4));
        canvas.drawText(cheerText, centerX, baseline, cheerPaint);
        cheerPaint.setStyle(Paint.Style.FILL);
        cheerPaint.clearShadowLayer();
        cheerPaint.setColor(withAlpha(getResources().getColor(R.color.text_white), alpha));
        canvas.drawText(cheerText, centerX, baseline, cheerPaint);
        canvas.restore();
    }

    private static float easeOut(float progress) {
        float remaining = 1 - progress;
        return 1 - remaining * remaining * remaining;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    // An undo takes back the tile that spawned after the move: it shrinks away where it appeared.
    private void drawRemovedTiles(Canvas canvas) {
        for (AnimationCell animation : game.aGrid.globalAnimation) {
            if (animation.getAnimationType() != MainGame.UNSPAWN_GLOBAL_ANIMATION) {
                continue;
            }
            float scale = 1 - (float) Math.min(1, animation.getPercentageDone());
            int index = log2(animation.extras[2]);
            int sX = startingX + gridWidth + (cellSize + gridWidth) * animation.extras[0];
            int sY = startingY + gridWidth + (cellSize + gridWidth) * animation.extras[1];
            float inset = cellSize / 2f * (1 - scale);
            bitmapCell[index].setBounds((int) (sX + inset), (int) (sY + inset),
                    (int) (sX + cellSize - inset), (int) (sY + cellSize - inset));
            bitmapCell[index].draw(canvas);
        }
    }

    private void drawEndGameState(Canvas canvas) {
        double alphaChange = 1;
        continueButtonEnabled = false;
        for (AnimationCell animation : game.aGrid.globalAnimation) {
            if (animation.getAnimationType() == MainGame.FADE_GLOBAL_ANIMATION) {
                alphaChange = animation.getPercentageDone();
            }
        }
        BitmapDrawable displayOverlay = null;
        if (game.gameWon()) {
            if (game.canContinue()) {
                continueButtonEnabled = true;
                displayOverlay = winGameContinueOverlay;
            } else {
                displayOverlay = winGameFinalOverlay;
            }
        } else if (game.gameLost()) {
            displayOverlay = loseGameOverlay;
        }

        if (displayOverlay != null) {
            displayOverlay.setBounds(startingX, startingY, endingX, endingY);
            displayOverlay.setAlpha((int) (255 * alphaChange));
            displayOverlay.draw(canvas);
        }
    }

    private void drawEndlessText(Canvas canvas) {
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(bodyTextSize);
        paint.setColor(getResources().getColor(R.color.text_black));
        canvas.drawText(getResources().getString(R.string.endless), startingX, sYIcons - centerText() * 2, paint);
    }

    private void createEndGameStates(Canvas canvas, boolean win, boolean showButton) {
        int width = endingX - startingX;
        int length = endingY - startingY;
        int middleX = width / 2;
        int middleY = length / 2;
        if (win) {
            lightUpRectangle.setAlpha(127);
            drawDrawable(canvas, lightUpRectangle, 0, 0, width, length);
            lightUpRectangle.setAlpha(255);
            paint.setColor(getResources().getColor(R.color.text_white));
            paint.setAlpha(255);
            paint.setTextSize(gameOverTextSize);
            paint.setTextAlign(Paint.Align.CENTER);
            int textBottom = middleY - centerText();
            canvas.drawText(getResources().getString(R.string.you_win), middleX, textBottom, paint);
            paint.setTextSize(bodyTextSize);
            String text = showButton ? getResources().getString(R.string.go_on) :
                    getResources().getString(R.string.for_now);
            canvas.drawText(text, middleX, textBottom + textPaddingSize * 2 - centerText() * 2, paint);
        } else {
            fadeRectangle.setAlpha(127);
            drawDrawable(canvas, fadeRectangle, 0, 0, width, length);
            fadeRectangle.setAlpha(255);
            paint.setColor(getResources().getColor(R.color.text_black));
            paint.setAlpha(255);
            paint.setTextSize(gameOverTextSize);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(getResources().getString(R.string.game_over), middleX, middleY - centerText(), paint);
        }
    }

    private void createBackgroundBitmap(int width, int height) {
        background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(background);
        drawHeader(canvas);
        drawNewGameButton(canvas, false);
        drawBoardSizeButton(canvas);
        drawBackground(canvas);
        drawBackgroundGrid(canvas);
        drawHighScoresButton(canvas);
        drawFooter(canvas, width);
        if (showHelp) drawInstructions(canvas);

    }

    private void createBitmapCells() {
        Resources resources = getResources();
        paint.setTextAlign(Paint.Align.CENTER);
        for (int xx = 1; xx < bitmapCell.length; xx++) {
            int value = (int) Math.pow(2, xx);
            paint.setTextSize(cellTextSize);
            float tempTextSize = cellTextSize * cellSize * 0.9f / Math.max(cellSize * 0.9f, paint.measureText(String.valueOf(value)));
            paint.setTextSize(tempTextSize);
            Bitmap bitmap = Bitmap.createBitmap(cellSize, cellSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawDrawable(canvas, ResourcesCompat.getDrawable(resources, getCellRectangleId(xx), null), 0, 0, cellSize, cellSize);
            drawCellText(canvas, value);
            bitmapCell[xx] = new BitmapDrawable(resources, bitmap);
            // Sample the tile colour from the top edge, clear of the rounded corners and the text
            cellColors[xx] = bitmap.getPixel(cellSize / 2, Math.max(1, cellSize / 12));
        }
    }

    // The background of the tile with the value 2^index.
    static int getCellRectangleId(int index) {
        int[] cellRectangleIds = {
                R.drawable.cell_rectangle,
                R.drawable.cell_rectangle_2,
                R.drawable.cell_rectangle_4,
                R.drawable.cell_rectangle_8,
                R.drawable.cell_rectangle_16,
                R.drawable.cell_rectangle_32,
                R.drawable.cell_rectangle_64,
                R.drawable.cell_rectangle_128,
                R.drawable.cell_rectangle_256,
                R.drawable.cell_rectangle_512,
                R.drawable.cell_rectangle_1024,
                R.drawable.cell_rectangle_2048,
        };
        return index < cellRectangleIds.length ? cellRectangleIds[index] : R.drawable.cell_rectangle_4096;
    }

    private void createOverlays() {
        Resources resources = getResources();
        //Initialize overlays
        Bitmap bitmap = Bitmap.createBitmap(endingX - startingX, endingY - startingY, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        createEndGameStates(canvas, true, true);
        winGameContinueOverlay = new BitmapDrawable(resources, bitmap);
        bitmap = Bitmap.createBitmap(endingX - startingX, endingY - startingY, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        createEndGameStates(canvas, true, false);
        winGameFinalOverlay = new BitmapDrawable(resources, bitmap);
        bitmap = Bitmap.createBitmap(endingX - startingX, endingY - startingY, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        createEndGameStates(canvas, false, false);
        loseGameOverlay = new BitmapDrawable(resources, bitmap);
    }

    private void tick() {
        long currentTime = System.nanoTime();
        game.aGrid.tickAll(currentTime - lastFPSTime);
        lastFPSTime = currentTime;
    }

    public void resyncTime() {
        lastFPSTime = System.nanoTime();
    }

    private void getLayout(int width, int height) {
        // The header and the board keep the size of the classic 4x4 layout. Bigger boards fit more,
        // smaller cells into the same space: n cells plus n + 1 gaps of a seventh of a cell.
        int classicSize = MainGame.DEFAULT_BOARD_SIZE;
        int unitSize = Math.min(width / (classicSize + 1), height / (classicSize + 3));
        int boardSize = classicSize * unitSize + (classicSize + 1) * (unitSize / 7);
        cellSize = boardSize * 7 / (8 * game.numSquaresX + 1);
        gridWidth = cellSize / 7;
        int screenMiddleX = width / 2;
        int screenMiddleY = height / 2;
        int boardMiddleY = screenMiddleY + unitSize / 2;
        iconSize = unitSize / 2;

        //Grid Dimensions
        double halfNumSquaresX = game.numSquaresX / 2d;
        double halfNumSquaresY = game.numSquaresY / 2d;
        startingX = (int) (screenMiddleX - (cellSize + gridWidth) * halfNumSquaresX - gridWidth / 2);
        endingX = (int) (screenMiddleX + (cellSize + gridWidth) * halfNumSquaresX + gridWidth / 2);
        startingY = (int) (boardMiddleY - (cellSize + gridWidth) * halfNumSquaresY - gridWidth / 2);
        endingY = (int) (boardMiddleY + (cellSize + gridWidth) * halfNumSquaresY + gridWidth / 2);

        float widthWithPadding = endingX - startingX;

        // Text Dimensions
        paint.setTextSize(cellSize);
        cellTextSize = cellSize * cellSize / Math.max(cellSize, paint.measureText("0000"));
        paint.setTextSize(unitSize);
        textSize = unitSize * unitSize / Math.max(unitSize, paint.measureText("0000"));

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(1000);
        instructionsTextSize = Math.min(
            1000f * (widthWithPadding / (paint.measureText(getResources().getString(R.string.instructions)))),
            textSize / 1.5f
        );
        gameOverTextSize = Math.min(
            Math.min(
                1000f * ((widthWithPadding - gridWidth * 2) / (paint.measureText(getResources().getString(R.string.game_over)))),
                textSize * 2
            ),
            1000f * ((widthWithPadding - gridWidth * 2) / (paint.measureText(getResources().getString(R.string.you_win))))
        );

        titleTextSize = textSize / 3;
        bodyTextSize = (int) (textSize / 1.5);
        headerTextSize = textSize * 2;
        textPaddingSize = (int) (textSize / 3);
        iconPaddingSize = (int) (textSize / 5);

        paint.setTextSize(titleTextSize);

        int textShiftYAll = centerText();
        //static variables
        sYAll = (int) (startingY - unitSize * 1.5);
        titleStartYAll = (int) (sYAll + textPaddingSize + titleTextSize / 2 - textShiftYAll);
        bodyStartYAll = (int) (titleStartYAll + textPaddingSize + titleTextSize / 2 + bodyTextSize / 2);

        titleWidthHighScore = (int) (paint.measureText(getResources().getString(R.string.high_score)));
        titleWidthScore = (int) (paint.measureText(getResources().getString(R.string.score)));
        paint.setTextSize(bodyTextSize);
        textShiftYAll = centerText();
        eYAll = (int) (bodyStartYAll + textShiftYAll + bodyTextSize / 2 + textPaddingSize);

        sYIcons = (startingY + eYAll) / 2 - iconSize / 2;
        sXNewGame = (endingX - iconSize);
        sXUndo = sXNewGame - iconSize * 3 / 2 - iconPaddingSize;
        sXBoardSize = sXUndo - iconSize * 3 / 2 - iconPaddingSize;

        cheerTextSize = textSize * 1.3f;

        // High scores button under the board
        buttonTextSize = bodyTextSize * 0.75f;
        paint.setTextSize(buttonTextSize);
        int highScoresWidth = (int) paint.measureText(getResources().getString(R.string.high_scores)) + textPaddingSize * 4;
        sXHighScores = screenMiddleX - highScoresWidth / 2;
        eXHighScores = sXHighScores + highScoresWidth;
        sYHighScores = endingY + iconSize;
        eYHighScores = sYHighScores + iconSize;

        // Footer at the bottom of the screen, clear of the navigation bar the app draws behind
        footerTextSize = titleTextSize * 1.4f;
        int bottomInset = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowInsets windowInsets = getRootWindowInsets();
            if (windowInsets != null) {
                bottomInset = windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            }
        }
        footerY = Math.max(height - bottomInset - textPaddingSize * 2,
                (int) (eYHighScores + footerTextSize * 2));
        resyncTime();
    }

    private int centerText() {
        return (int) ((paint.descent() + paint.ascent()) / 2);
    }

    // One celebrated merge. Its level decides how big the celebration gets: level 0 is a first 32,
    // every doubling adds one. Repeated merges get a much smaller burst.
    private static class Achievement extends Cell {
        final int index;
        final long startTime;
        // Glow, rings and sparks
        final long effectTime;
        // Until the last confetti and fireworks are gone
        final long totalTime;
        final float glowSpread;
        final int glowAlpha;
        final int rings;
        final float ringSpread;
        final float ringWidth;
        final float sparkSize;
        final float[] sparkAngles;
        final float[] sparkSpeeds;
        // Big first merges
        final float flash;
        final float shake;
        final long shakeTime;
        final boolean unclipped;
        final boolean rainbow;
        final boolean sunburst;
        final int waves;
        // Confetti: position and start height as fractions of the screen, fall speed in screens
        // per second, flutter phase, spin in degrees per second, size and palette colour
        final float[] confettiX;
        final float[] confettiY;
        final float[] confettiSpeed;
        final float[] confettiPhase;
        final float[] confettiSpin;
        final float[] confettiSize;
        final int[] confettiColor;
        // Fireworks: position as fractions of the screen, start and palette colour
        final float[] fireworkX;
        final float[] fireworkY;
        final long[] fireworkDelays;
        final int[] fireworkColors;

        Achievement(int x, int y, int index, boolean firstMerge, long startTime, Random random) {
            super(x, y);
            this.index = index;
            this.startTime = startTime;
            int level = Math.max(0, firstMerge ? index - 5 : index - 8);
            int sparks;
            if (firstMerge) {
                effectTime = BASE_ANIMATION_TIME * (12L + 2 * level);
                glowSpread = 2.6f + 0.4f * level;
                glowAlpha = Math.min(255, 190 + 8 * level);
                rings = Math.min(3 + level / 2, 7);
                ringSpread = 1.7f + 0.3f * level;
                ringWidth = 1.4f + 0.15f * level;
                sparkSize = 0.75f + 0.05f * level;
                sparks = Math.min(14 + 5 * level, 70);
            } else {
                effectTime = BASE_ANIMATION_TIME * (7L + level);
                glowSpread = 1 + 0.2f * level;
                glowAlpha = 90;
                rings = Math.min(1 + level / 2, 3);
                ringSpread = 0.9f + 0.2f * level;
                ringWidth = 1;
                sparkSize = 0.6f;
                sparks = 8 + 3 * level;
            }
            boolean big = firstMerge && level >= 5;
            flash = big ? Math.min(0.3f + 0.1f * (level - 5), 0.7f) : 0;
            shake = big ? 0.6f + 0.6f * (level - 5) : 0;
            shakeTime = BASE_ANIMATION_TIME * (5L + 2 * Math.max(0, level - 5));
            unclipped = firstMerge && level >= 6;
            rainbow = firstMerge && level >= 7;
            sunburst = firstMerge && level >= 8;
            waves = firstMerge ? Math.max(0, level - 6) : 0;

            sparkAngles = new float[sparks];
            sparkSpeeds = new float[sparks];
            for (int i = 0; i < sparks; i++) {
                sparkAngles[i] = (float) (2 * Math.PI * (i + random.nextFloat() * 0.6f) / sparks);
                sparkSpeeds[i] = (1.4f + random.nextFloat() * 1.4f) * (1 + 0.15f * level);
            }

            int confetti = firstMerge && level >= 6 ? Math.min(70 * (level - 5), 280) : 0;
            confettiX = new float[confetti];
            confettiY = new float[confetti];
            confettiSpeed = new float[confetti];
            confettiPhase = new float[confetti];
            confettiSpin = new float[confetti];
            confettiSize = new float[confetti];
            confettiColor = new int[confetti];
            for (int i = 0; i < confetti; i++) {
                confettiX[i] = random.nextFloat();
                confettiY[i] = -0.05f - random.nextFloat() * 0.7f;
                confettiSpeed[i] = 0.3f + random.nextFloat() * 0.35f;
                confettiPhase[i] = random.nextFloat() * 6.3f;
                confettiSpin[i] = (random.nextBoolean() ? 1 : -1) * (120 + random.nextFloat() * 400);
                confettiSize[i] = 0.5f + random.nextFloat() * 0.5f;
                confettiColor[i] = randomPaletteColor(random);
            }

            int fireworks = firstMerge && level >= 7 ? Math.min(3 * (level - 6), 12) : 0;
            fireworkX = new float[fireworks];
            fireworkY = new float[fireworks];
            fireworkDelays = new long[fireworks];
            fireworkColors = new int[fireworks];
            long lastFirework = 0;
            for (int i = 0; i < fireworks; i++) {
                fireworkX[i] = 0.15f + random.nextFloat() * 0.7f;
                fireworkY[i] = 0.12f + random.nextFloat() * 0.5f;
                fireworkDelays[i] = BASE_ANIMATION_TIME * (2L + 4 * i) + (long) (random.nextFloat() * BASE_ANIMATION_TIME * 2);
                fireworkColors[i] = randomPaletteColor(random);
                lastFirework = fireworkDelays[i] + FIREWORK_TIME;
            }

            long longest = Math.max(effectTime, Math.max(shakeTime, lastFirework));
            if (confetti > 0) {
                longest = Math.max(longest, CONFETTI_TIME);
            }
            if (waves > 0) {
                longest = Math.max(longest, BASE_ANIMATION_TIME * (5L * waves + 10));
            }
            totalTime = longest;
        }

        // A tile colour from 8 to 2048, or -1 for white
        private static int randomPaletteColor(Random random) {
            int color = 2 + random.nextInt(11);
            return color == 2 ? -1 : color;
        }
    }
}
