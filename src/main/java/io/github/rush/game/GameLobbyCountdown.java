package io.github.rush.game;

import io.github.rush.Main;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class GameLobbyCountdown {

    private final Game game;
    private int counter = 60;
    private BukkitTask task;

    public GameLobbyCountdown(Game game) {
        this.game = game;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (counter == 15 || counter == 10 || counter <= 5) {
                broadcastCountdown(counter);
            }

            if (counter <= 0) {
                game.start();
                cancel();
                return;
            }

            if (!canStart()) {
                cancel();
                return;
            }

            counter--;
        }, 0L, 20L);
    }

    private void broadcastCountdown(int seconds) {
        String message = "§eLa partie commence dans §c" + seconds + " §esecondes!";
        for (Player player : game.getPlayers()) {
            player.sendMessage(Component.text(message));
        }
    }

    private boolean canStart() {
        long readyCount = game.getFreePlayers().stream()
                .filter(p -> true)
                .count();
        return readyCount >= 2 && game.getTeamCount() >= 2;
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        counter = 60;
    }
}
