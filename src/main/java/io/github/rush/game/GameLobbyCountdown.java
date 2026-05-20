package io.github.rush.game;

import io.github.rush.Main;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
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
        float pitch = seconds <= 1 ? 2.0f : 1.0f;

        for (Entity entity : game.getPlayers()) {
            if (entity instanceof Player player) {
                player.sendMessage(Component.text(message));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
            }
        }
    }

    private boolean canStart() {
        return game.areEnoughTeamsFull();
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        counter = 60;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public void broadcastCountdownMessage(int seconds) {
        String message = "§eLa partie commence dans §c" + seconds + " §esecondes!";

        for (Entity entity : game.getPlayers()) {
            if (entity instanceof Player player) {
                player.sendMessage(Component.text(message));
            }
        }
    }
}
