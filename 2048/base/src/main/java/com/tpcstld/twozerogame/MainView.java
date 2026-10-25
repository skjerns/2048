package com.tpcstld.twozerogame;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
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
    private static final long ACHIEVEMENT_ANIMATION_TIME = BASE_ANIMATION_TIME * 12;
    private static final long COIN_PULSE_TIME = BASE_ANIMATION_TIME * 6;
    private static final int ACHIEVEMENT_RINGS = 3;
    private static final int ACHIEVEMENT_PARTICLES = 14;
    public final int numCellTypes = 21;
    private final BitmapDrawable[] bitmapCell = new BitmapDrawable[numCellTypes];
    private final int[] cellColors = new int[numCellTypes];
    public final MainGame game;
    //Internal variables
    private final Paint paint = new Paint();
    private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF effectRect = new RectF();
    private final ArrayList<Achievement> achievements = new ArrayList<>();
    private final Random random = new Random();
    private long coinEarnedTime = 0;
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

        canvas.drawBitmap(background, 0, 0, paint);

        drawScoreText(canvas);

        drawUndoButton(canvas);

        if (!game.isActive() && !game.aGrid.isAnimationActive()) {
            drawNewGameButton(canvas, true);
        }

        drawAchievements(canvas, true);

        drawRemovedTiles(canvas);

        drawCells(canvas);

        drawAchievements(canvas, false);

        if (!game.isActive()) {
            drawEndGameState(canvas);
        }

        if (!game.canContinue()) {
            drawEndlessText(canvas);
        }

        //Refresh the screen if there is still an animation running
        if (game.aGrid.isAnimationActive()) {
            invalidate(startingX, startingY, endingX, endingY);
            tick();
            //Refresh one last time on game end.
        } else if (!game.isActive() && refreshLastTime) {
            invalidate();
            refreshLastTime = false;
        }
        if (!achievements.isEmpty() || isCoinPulseActive()) {
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

    private void drawCells(Canvas canvas) {
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
                            float textScaleSize = (float) (1 + INITIAL_VELOCITY * percentDone
                                    + MERGING_ACCELERATION * percentDone * percentDone / 2);
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

                    //No active animations? Just draw the cell
                    if (!animated) {
                        bitmapCell[index].setBounds(sX, sY, eX, eY);
                        bitmapCell[index].draw(canvas);
                    }
                }
            }
        }
    }

    void showAchievement(int x, int y, int value) {
        long startTime = System.nanoTime() + ACHIEVEMENT_DELAY_TIME;
        achievements.add(new Achievement(x, y, log2(value), startTime, random));
        coinEarnedTime = startTime;
        invalidate();
    }

    void clearAchievements() {
        achievements.clear();
        coinEarnedTime = 0;
    }

    // The colour of a new tile radiates out of it: a soft glow below the tiles (glow == true),
    // then expanding rings and sparks above them.
    private void drawAchievements(Canvas canvas, boolean glow) {
        if (achievements.isEmpty()) {
            return;
        }
        long currentTime = System.nanoTime();
        canvas.save();
        canvas.clipRect(startingX, startingY, endingX, endingY);
        Iterator<Achievement> iterator = achievements.iterator();
        while (iterator.hasNext()) {
            Achievement achievement = iterator.next();
            float progress = (float) (currentTime - achievement.startTime) / ACHIEVEMENT_ANIMATION_TIME;
            if (progress > 1) {
                if (!glow) {
                    iterator.remove();
                }
                continue;
            } else if (progress < 0) {
                continue;
            }

            int color = cellColors[Math.min(achievement.index, numCellTypes - 1)];
            float sX = startingX + gridWidth + (cellSize + gridWidth) * achievement.getX();
            float sY = startingY + gridWidth + (cellSize + gridWidth) * achievement.getY();
            float centerX = sX + cellSize / 2f;
            float centerY = sY + cellSize / 2f;

            if (glow) {
                float radius = cellSize * (0.7f + 2.6f * easeOut(progress));
                int alpha = (int) (190 * (1 - progress) * (1 - progress));
                effectPaint.setStyle(Paint.Style.FILL);
                effectPaint.setShader(new RadialGradient(centerX, centerY, radius,
                        new int[]{withAlpha(color, alpha), withAlpha(color, alpha / 2), withAlpha(color, 0)},
                        new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
                canvas.drawCircle(centerX, centerY, radius, effectPaint);
                effectPaint.setShader(null);
                continue;
            }

            float cornerRadius = getResources().getDisplayMetrics().density * 5;
            effectPaint.setStyle(Paint.Style.STROKE);
            for (int ring = 0; ring < ACHIEVEMENT_RINGS; ring++) {
                float ringProgress = (progress - ring * 0.15f) / 0.7f;
                if (ringProgress <= 0 || ringProgress >= 1) {
                    continue;
                }
                float spread = cellSize * 1.7f * easeOut(ringProgress);
                effectPaint.setStrokeWidth(Math.max(1, gridWidth * 1.4f * (1 - ringProgress)));
                effectPaint.setColor(withAlpha(color, (int) (235 * (1 - ringProgress))));
                effectRect.set(sX - spread, sY - spread, sX + cellSize + spread, sY + cellSize + spread);
                canvas.drawRoundRect(effectRect, cornerRadius + spread * 0.6f, cornerRadius + spread * 0.6f, effectPaint);
            }

            effectPaint.setStyle(Paint.Style.FILL);
            int sparkAlpha = (int) (255 * (1 - progress));
            float sparkSize = gridWidth * 0.75f * (1 - progress * 0.6f);
            for (int i = 0; i < ACHIEVEMENT_PARTICLES; i++) {
                float distance = cellSize * (0.45f + achievement.particleSpeeds[i] * easeOut(progress));
                float angle = achievement.particleAngles[i];
                float pX = centerX + (float) Math.cos(angle) * distance;
                float pY = centerY + (float) Math.sin(angle) * distance;
                int sparkColor = i % 2 == 0 ? color : getResources().getColor(R.color.text_white);
                effectPaint.setColor(withAlpha(sparkColor, sparkAlpha));
                canvas.save();
                canvas.rotate((float) Math.toDegrees(angle) + progress * 360, pX, pY);
                canvas.drawRect(pX - sparkSize, pY - sparkSize, pX + sparkSize, pY + sparkSize, effectPaint);
                canvas.restore();
            }
        }
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

    private static class Achievement extends Cell {
        final int index;
        final long startTime;
        final float[] particleAngles = new float[ACHIEVEMENT_PARTICLES];
        final float[] particleSpeeds = new float[ACHIEVEMENT_PARTICLES];

        Achievement(int x, int y, int index, long startTime, Random random) {
            super(x, y);
            this.index = index;
            this.startTime = startTime;
            for (int i = 0; i < ACHIEVEMENT_PARTICLES; i++) {
                particleAngles[i] = (float) (2 * Math.PI * (i + random.nextFloat() * 0.6f) / ACHIEVEMENT_PARTICLES);
                particleSpeeds[i] = 1.4f + random.nextFloat() * 1.4f;
            }
        }
    }
}
