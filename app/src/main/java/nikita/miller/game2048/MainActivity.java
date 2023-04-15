package nikita.miller.game2048;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private final Random random = new Random();
    private final String BEST_SCORE_FILE_NAME = "best_score.txt";
    private final String SAVED_GAME_FILE_NAME = "saved_game.txt";
    private final TextView[][] tvTiles = new TextView[4][4];
    private TextView tvScore;
    private TextView tvBestScore;
    private Animation spawnTileAnimation;
    private GameState gameState = new GameState();
    private int savedBestScore;
    private final FixedCapacityStack<GameState> history = new FixedCapacityStack<>(5);
    private GameState prevState = new GameState();

    @SuppressLint("DiscouragedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Загрузка сохранённой игры (при наличии)
        boolean gameWasLoaded = loadGame();
        savedBestScore = loadBestScore();
        // Инициализация TextView элементов
        tvScore = findViewById(R.id.tv_score);
        tvBestScore = findViewById(R.id.tv_best_score);
        tvBestScore.setText(
                getString(
                        R.string.game_best_score_pattern,
                        gameState.getBestScore()
                )
        );
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < 4; ++j) {
                tvTiles[i][j] = findViewById(
                        getResources().getIdentifier(
                                "game_tile_" + i + j,
                                "id",
                                getPackageName()
                        ));
            }

        // Загрузка анимаций
        spawnTileAnimation = AnimationUtils.loadAnimation(
                MainActivity.this,
                R.anim.spawn_tile
        );
        spawnTileAnimation.reset();

        // Инициализиация кнопок
        findViewById(R.id.game_layout)
                .setOnTouchListener(new OnSwipeListener(MainActivity.this) {
                    @Override
                    public void onSwipeLeft() {
                        if (moveLeft()) {
                            history.push(prevState);
                            spawnTile();
                            return;
                        }

                        showToast(R.string.cant_move_left);
                    }

                    @Override
                    public void onSwipeRight() {
                        if (moveRight()) {
                            history.push(prevState);
                            spawnTile();
                            return;
                        }

                        showToast(R.string.cant_move_right);
                    }

                    @Override
                    public void onSwipeTop() {
                        if (moveUp()) {
                            history.push(prevState);
                            spawnTile();
                            return;
                        }

                        showToast(R.string.cant_move_up);
                    }

                    @Override
                    public void onSwipeBottom() {
                        if (moveDown()) {
                            history.push(prevState);
                            spawnTile();
                            return;
                        }

                        showToast(R.string.cant_move_down);
                    }
                });
        findViewById(R.id.btn_new_game)
                .setOnClickListener(view -> showNewGameDialog());
        findViewById(R.id.btn_undo_move)
                .setOnClickListener(view -> undo());

        // Если игра была загружена - продолжение,
        // иначе - запуск новой игры
        if (gameWasLoaded) {
            showField();
            return;
        }

        startNewGame();
    }

    // region Save/Load

    private void saveBestScore() {
        try (FileOutputStream fos = openFileOutput(BEST_SCORE_FILE_NAME, Context.MODE_PRIVATE)) {
            DataOutputStream writer = new DataOutputStream(fos);
            writer.writeInt(gameState.getBestScore());
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            Log.d("saveBestScore", ex.getMessage());
        }
    }

    private int loadBestScore() {
        int bestScore = 0;

        try (FileInputStream fis = openFileInput(BEST_SCORE_FILE_NAME)) {
            DataInputStream reader = new DataInputStream(fis);
            bestScore = reader.readInt();
            reader.close();
            return bestScore;
        } catch (IOException ex) {
            Log.d("loadBestScore", ex.getMessage());
        }

        return bestScore;
    }

    private void saveGame() {
        try (FileOutputStream fos = openFileOutput(SAVED_GAME_FILE_NAME, Context.MODE_PRIVATE)) {
            DataOutputStream writer = new DataOutputStream(fos);

            // Сохранение состояния игрового поля в формате 32;2;0;8;...
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; ++i) {
                for (int j = 0; j < 4; ++j) {
                    sb.append(gameState.getTiles()[i][j]);
                    if (i == 3 && j == 3) {
                        break;
                    }
                    sb.append(';');
                }
            }
            writer.writeUTF(sb.toString());
            // Сохранение текущего количества очков
            writer.writeInt(gameState.getScore());
            // Сохранение текущего рекорда
            writer.writeInt(gameState.getBestScore());
            // Сохранение текущего режима игры
            writer.writeBoolean(gameState.isFreeMode());

            writer.flush();
            writer.close();
        } catch (IOException ex) {
            Log.d("saveGame", ex.getMessage());
        }
    }

    private boolean loadGame() {
        File savedGame = new File(getFilesDir(), SAVED_GAME_FILE_NAME);
        if (!savedGame.exists()) {
            return false;
        }

        try (FileInputStream fis = openFileInput(SAVED_GAME_FILE_NAME)) {
            DataInputStream reader = new DataInputStream(fis);

            gameState.setTiles(parseTiles(reader.readUTF()));
            gameState.setScore(reader.readInt());
            gameState.setBestScore(reader.readInt());
            gameState.setFreeMode(reader.readBoolean());

            reader.close();
        } catch (IOException ex) {
            Log.d("loadGame", ex.getMessage());
        }

        return true;
    }

    private int[][] parseTiles(String tiles) {
        int[][] result = new int[4][4];
        String[] split = tiles.replace("\n", "").split(";");

        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                result[i][j] = Integer.parseInt(split[4 * i + j]);
            }
        }

        return result;
    }

    // endregion

    private boolean isWin() {
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < 4; ++j) {
                if (gameState.getTiles()[i][j] == 2048) {
                    return true;
                }
            }

        return false;
    }

    private boolean isGameOver() {
        int current;
        int leftNeighbor;
        int rightNeighbor;
        int topNeighbor;
        int bottomNeighbor;

        // пробег по всему полю в поисках пустых ячеек или возможных слияний
        // каждая ячейка проверяется на возможность слияния с одной из соседних
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < 4; ++j) {
                if (gameState.getTiles()[i][j] == 0) {
                    return false;
                }

                try {
                    leftNeighbor = gameState.getTiles()[i][j - 1];
                } catch (Exception ignored) {
                    leftNeighbor = -1;
                }

                try {
                    rightNeighbor = gameState.getTiles()[i][j + 1];
                } catch (Exception ignored) {
                    rightNeighbor = -1;
                }

                try {
                    topNeighbor = gameState.getTiles()[i - 1][j];
                } catch (Exception ignored) {
                    topNeighbor = -1;
                }

                try {
                    bottomNeighbor = gameState.getTiles()[i + 1][j];
                } catch (Exception ignored) {
                    bottomNeighbor = -1;
                }

                current = gameState.getTiles()[i][j];

                if (current == leftNeighbor
                        || current == rightNeighbor
                        || current == topNeighbor
                        || current == bottomNeighbor) {
                    return false;
                }
            }

        return true;
    }

    // region Dialogs

    private void showWinDialog() {
        new AlertDialog.Builder(
                MainActivity.this,
                androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle(R.string.game_victory_title)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setMessage(R.string.game_victory_message)
                .setCancelable(false)
                .setPositiveButton(R.string.btn_continue, (dialog, which) -> gameState.setFreeMode(true))
                .setNegativeButton(R.string.btn_exit, (dialog, which) -> finish())
                .setNeutralButton(R.string.btn_new_game, (dialog, which) -> startNewGame())
                .show();
    }

    private void showGameOverDialog() {
        new AlertDialog.Builder(
                MainActivity.this,
                androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle(R.string.game_over_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(R.string.game_over_message)
                .setCancelable(false)
                .setPositiveButton(R.string.btn_new_game, (dialog, which) -> startNewGame())
                .setNegativeButton(R.string.btn_exit, (dialog, which) -> finish())
                .show();
    }

    private void showNewGameDialog() {
        new AlertDialog.Builder(
                MainActivity.this,
                androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle(R.string.new_game_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(R.string.new_game_confirm)
                .setPositiveButton(R.string.yes_button_text, (dialog, which) -> startNewGame())
                .setNegativeButton(R.string.no_button_text, (dialog, which) -> dialog.dismiss())
                .show();
    }

    // endregion

    private void showToast(int message) {
        Toast
                .makeText(
                        MainActivity.this,
                        message,
                        Toast.LENGTH_SHORT
                )
                .show();
    }

    private void emptyField() {
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < 4; ++j) {
                gameState.getTiles()[i][j] = 0;
            }
    }

    private void startNewGame() {
        if (gameState.getBestScore() > savedBestScore) {
            saveBestScore();
        }

        gameState.setScore(0);
        gameState.setFreeMode(false);

        history.clear();
        emptyField();
        spawnTile();
        spawnTile();
        saveGame();
    }

    @SuppressLint("DiscouragedApi")
    private void showField() {
        if (isGameOver()) {
            showGameOverDialog();
            return;
        }

        // region Обновление значений TextView элементов

        Resources resources = getResources();
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < 4; ++j) {
                tvTiles[i][j].setText(String.valueOf(gameState.getTiles()[i][j]));
                tvTiles[i][j].setTextAppearance(
                        resources.getIdentifier(
                                "GameTile" + (gameState.getTiles()[i][j] == 0
                                        ? "Empty"
                                        : gameState.getTiles()[i][j]),
                                "style",
                                getPackageName()
                        ));
                tvTiles[i][j].setBackgroundColor(
                        resources.getColor(
                                resources.getIdentifier(
                                        "game_tile_" +
                                                (gameState.getTiles()[i][j] == 0
                                                        ? "empty"
                                                        : gameState.getTiles()[i][j] > 2048
                                                        ? "other"
                                                        : gameState.getTiles()[i][j]),
                                        "color",
                                        getPackageName()
                                ),
                                getTheme())
                );
            }
        tvScore.setText(
                getString(
                        R.string.game_score_pattern,
                        gameState.getScore()
                )
        );

        // endregion

        if (gameState.getScore() >= gameState.getBestScore()) {
            gameState.setBestScore(gameState.getScore());
            tvBestScore.setText(
                    getString(
                            R.string.game_best_score_pattern,
                            gameState.getBestScore()
                    )
            );
        }

        if (!gameState.isFreeMode() && isWin()) {
            showWinDialog();
            return;
        }

        saveGame();
    }

    private void spawnTile() {
        List<Integer> emptyTileIndexes = new ArrayList<>();
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < 4; ++j) {
                if (gameState.getTiles()[i][j] == 0) {
                    emptyTileIndexes.add(i * 10 + j);
                }
            }

        int count = emptyTileIndexes.size();
        if (count == 0) {
            return;
        }

        int randIndex = random.nextInt(count);
        int x = emptyTileIndexes.get(randIndex) / 10;
        int y = emptyTileIndexes.get(randIndex) % 10;
        gameState.getTiles()[x][y] = random.nextInt(10) < 9 ? 2 : 4;
        tvTiles[x][y].startAnimation(spawnTileAnimation);

        showField();
    }

    private void undo() {
        if (history.isEmpty()) {
            return;
        }

        gameState = history.pop();
        Log.i("best", String.valueOf(gameState.getBestScore()));

        showField();
    }

    // region Moves

    private boolean moveLeft() {
        prevState = (GameState) gameState.clone();
        boolean result = false;
        boolean needRepeat;
        boolean isTileEmpty;
        boolean tilesEqual;

        for (int i = 0; i < 4; ++i) {
            do {
                needRepeat = false;
                for (int j = 0; j < 3; ++j) {
                    isTileEmpty = gameState.getTiles()[i][j] == 0;
                    if (isTileEmpty) {
                        for (int k = j + 1; k < 4; ++k) {
                            if (gameState.getTiles()[i][k] > 0) {
                                gameState.getTiles()[i][j] = gameState.getTiles()[i][k];
                                gameState.getTiles()[i][k] = 0;
                                needRepeat = true;
                                result = true;
                                break;
                            }
                        }
                    }
                }
            } while (needRepeat);

            for (int j = 0; j < 3; ++j) {
                isTileEmpty = gameState.getTiles()[i][j] == 0;
                tilesEqual = gameState.getTiles()[i][j] == gameState.getTiles()[i][j + 1];
                if (!isTileEmpty && tilesEqual) {
                    gameState.getTiles()[i][j] *= 2;
                    for (int k = j + 1; k < 3; ++k) {
                        gameState.getTiles()[i][k] = gameState.getTiles()[i][k + 1];
                    }
                    gameState.getTiles()[i][3] = 0;
                    result = true;
                    gameState.setScore(
                            gameState.getScore() + gameState.getTiles()[i][j]
                    );
                }
            }
        }

        return result;
    }

    private boolean moveRight() {
        prevState = (GameState) gameState.clone();
        boolean result = false;
        boolean needRepeat;
        boolean isTileEmpty;
        boolean tilesEqual;

        for (int i = 0; i < 4; ++i) {
            do {
                needRepeat = false;
                for (int j = 3; j > 0; --j) {
                    isTileEmpty = gameState.getTiles()[i][j] == 0;
                    if (isTileEmpty) {
                        for (int k = j - 1; k > -1; --k) {
                            if (gameState.getTiles()[i][k] > 0) {
                                gameState.getTiles()[i][j] = gameState.getTiles()[i][k];
                                gameState.getTiles()[i][k] = 0;
                                needRepeat = true;
                                result = true;
                                break;
                            }
                        }
                    }
                }
            } while (needRepeat);

            for (int j = 3; j > 0; --j) {
                isTileEmpty = gameState.getTiles()[i][j] == 0;
                tilesEqual = gameState.getTiles()[i][j] == gameState.getTiles()[i][j - 1];
                if (!isTileEmpty && tilesEqual) {
                    gameState.getTiles()[i][j] *= 2;
                    for (int k = j - 1; k > 0; --k) {
                        gameState.getTiles()[i][k] = gameState.getTiles()[i][k - 1];
                    }
                    gameState.getTiles()[i][0] = 0;
                    result = true;
                    gameState.setScore(
                            gameState.getScore() + gameState.getTiles()[i][j]
                    );
                }
            }
        }

        return result;
    }

    private boolean moveUp() {
        prevState = (GameState) gameState.clone();
        boolean result = false;
        boolean needRepeat;
        boolean isTileEmpty;
        boolean tilesEqual;

        for (int i = 0; i < 4; ++i) {
            do {
                needRepeat = false;
                for (int j = 0; j < 3; ++j) {
                    isTileEmpty = gameState.getTiles()[j][i] == 0;
                    if (isTileEmpty) {
                        for (int k = j + 1; k < 4; ++k) {
                            if (gameState.getTiles()[k][i] > 0) {
                                gameState.getTiles()[j][i] = gameState.getTiles()[k][i];
                                gameState.getTiles()[k][i] = 0;
                                needRepeat = true;
                                result = true;
                                break;
                            }
                        }
                    }
                }
            } while (needRepeat);

            for (int j = 0; j < 3; ++j) {
                isTileEmpty = gameState.getTiles()[j][i] == 0;
                tilesEqual = gameState.getTiles()[j][i] == gameState.getTiles()[j + 1][i];
                if (!isTileEmpty && tilesEqual) {
                    gameState.getTiles()[j][i] *= 2;
                    for (int k = j + 1; k < 3; ++k) {
                        gameState.getTiles()[k][i] = gameState.getTiles()[k + 1][i];
                    }
                    gameState.getTiles()[3][i] = 0;
                    result = true;
                    gameState.setScore(
                            gameState.getScore() + gameState.getTiles()[j][i]
                    );
                }
            }
        }

        return result;
    }

    private boolean moveDown() {
        prevState = (GameState) gameState.clone();
        boolean result = false;
        boolean needRepeat;
        boolean isTileEmpty;
        boolean tilesEqual;

        for (int i = 0; i < 4; ++i) {
            do {
                needRepeat = false;
                for (int j = 3; j > 0; --j) {
                    isTileEmpty = gameState.getTiles()[j][i] == 0;
                    if (isTileEmpty) {
                        for (int k = j - 1; k >= 0; --k) {
                            if (gameState.getTiles()[k][i] > 0) {
                                gameState.getTiles()[j][i] = gameState.getTiles()[k][i];
                                gameState.getTiles()[k][i] = 0;
                                needRepeat = true;
                                result = true;
                                break;
                            }
                        }
                    }
                }
            } while (needRepeat);

            for (int j = 3; j > 0; --j) {
                isTileEmpty = gameState.getTiles()[j][i] == 0;
                tilesEqual = gameState.getTiles()[j][i] == gameState.getTiles()[j - 1][i];
                if (!isTileEmpty && tilesEqual) {
                    gameState.getTiles()[j][i] *= 2;
                    for (int k = j - 1; k > 0; --k) {
                        gameState.getTiles()[k][i] = gameState.getTiles()[k - 1][i];
                    }
                    gameState.getTiles()[0][i] = 0;
                    result = true;
                    gameState.setScore(
                            gameState.getScore() + gameState.getTiles()[j][i]
                    );
                }
            }
        }

        return result;
    }

    //endregion
}