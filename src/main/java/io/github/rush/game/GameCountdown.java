package io.github.rush.game;

import io.github.rush.entities.GameCombatant;
import io.github.rush.entities.GamePlayer;

import io.github.rush.Main;
import io.github.rush.utils.Sounds;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class GameCountdown {

    private final Game game;
    private final boolean force;
    @Setter
    private int counter = 60;
    private BukkitTask task;

    public GameCountdown(Game game, boolean force) {
        this.game = game;
        this.force = force;
    }

    /**
     * Attempts every second to run the game if they are enough "ready" players
     * Sends a countdown message of the game start event in the last 15 seconds
     */
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (counter > 0 && (counter == 15 || counter == 10 || counter <= 5))
                broadcastCountdown(counter);
            else if (counter == 0) {
                game.start();
                cancel();
                return;
            } else if (!(force || game.areEnoughTeamsFull())) {
                cancel();
                return;
            }

            counter--;
        }, 0L, 20L);
    }

    /**
     * Broadcasts the countdown message to all players.
     */
    private void broadcastCountdown(int seconds) {
        for (GameCombatant participant : game.getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                final Player player = gp.player();

                player.sendMessage(Component.translatable("rush.countdown_seconds", Component.text(seconds)));
                final float pitch = seconds <= 1 ? 2.0f : seconds <= 2 ? 1.5f : seconds <= 3 ? 1.2f : 1.0f;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);

                Sounds.COUNTDOWN.play(player);
            }
        }
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        counter = 60;
    }

}
