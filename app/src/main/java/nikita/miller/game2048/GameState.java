package nikita.miller.game2048;

import androidx.annotation.NonNull;

public class GameState implements Cloneable {
    private int[][] tiles;
    private int score;
    private int bestScore;
    private boolean isFreeMode;

    public GameState() {
        tiles = new int[4][4];
        score = 0;
        bestScore = 0;
        isFreeMode = false;
    }

    public int[][] getTiles() {
        return tiles;
    }

    public void setTiles(int[][] tiles) {
        this.tiles = tiles;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getBestScore() {
        return bestScore;
    }

    public void setBestScore(int bestScore) {
        this.bestScore = bestScore;
    }

    public boolean isFreeMode() {
        return isFreeMode;
    }

    public void setFreeMode(boolean freeMode) {
        isFreeMode = freeMode;
    }

    @NonNull
    @Override
    protected Object clone() {
        GameState clone = new GameState();

        int[][] tiles = this.getTiles();
        int[][] clonedTiles = new int[4][4];
        for (int i = 0; i < 4; ++i) {
            System.arraycopy(tiles[i], 0, clonedTiles[i], 0, 4);
        }

        clone.setTiles(clonedTiles);
        clone.setScore(this.getScore());
        clone.setBestScore(this.getBestScore());
        clone.setFreeMode(this.isFreeMode());

        return clone;
    }
}
