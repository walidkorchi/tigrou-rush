package io.github.rush.game;

import io.github.rush.Main;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class GameCycle {

    private final Game game;
    private BukkitTask gameTask;
    private int timeLeft;

    public GameCycle(Game game) {
        this.game = game;
        this.timeLeft = Main.getInstance().getMaxLength();
    }

    public void onGameStart() {
        timeLeft = Main.getInstance().getMaxLength();

        gameTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (game.getState() != GameState.RUNNING) {
                return;
            }

            timeLeft--;

            if (timeLeft <= 0) {
                endGame();
            }
        }, 0L, 20L);
    }

    private void endGame() {
        if (gameTask != null) {
            gameTask.cancel();
        }
        game.setState(GameState.STOPPED);
    }

    public void onGameEnd() {
        if (gameTask != null) {
            gameTask.cancel();
        }

        game.saveStats();
    }
}
