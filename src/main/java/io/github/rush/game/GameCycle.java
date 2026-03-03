package io.github.rush.game;

import io.github.rush.Main;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class GameCycle {

    private final Game game;
    private BukkitTask gameTask;

    public GameCycle(Game game) {
        this.game = game;
    }

    public void onGameStart() {
        gameTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (game.getState() != GameState.RUNNING) {
                return;
            }
            game.incrementGameTime();
        }, 0L, 20L);
    }

    public void onGameEnd() {
        if (gameTask != null) {
            gameTask.cancel();
        }

        game.saveStats();
    }
}
