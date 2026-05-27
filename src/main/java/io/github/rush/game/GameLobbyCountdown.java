package io.github.rush.game;

import io.github.rush.entities.GameCombatant;
import io.github.rush.entities.GamePlayer;

import io.github.rush.Main;
import io.github.rush.sound.RushSounds;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class GameLobbyCountdown {

    private final Game game;
    private final boolean force;
    @Setter
    private int counter = 60;
    private BukkitTask task;

    public GameLobbyCountdown(Game game, boolean force) {
        this.game = game;
        this.force = force;
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
        float pitch = seconds <= 1 ? 2.0f : 1.0f;
        Component message = Component.translatable("rush.countdown_seconds", Component.text(seconds));

        for (GameCombatant participant : game.getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                Player player = gp.player();
                player.sendMessage(message);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
                RushSounds.COUNTDOWN.play(player);
            }
        }
    }

    private boolean canStart() {
        return force || game.areEnoughTeamsFull();
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        counter = 60;
    }

    public void broadcastCountdownMessage(int seconds) {
        Component message = Component.translatable("rush.countdown_seconds", Component.text(seconds));

        for (GameCombatant participant : game.getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                gp.player().sendMessage(message);
            }
        }
    }
}
