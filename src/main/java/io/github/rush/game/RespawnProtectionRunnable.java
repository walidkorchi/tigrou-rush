
package io.github.rush.game;

import io.github.rush.Main;
import io.github.rush.utils.ChatWriter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class RespawnProtectionRunnable extends BukkitRunnable {

    private Game game = null;
    private int length = 0;
    private Player player = null;

    public RespawnProtectionRunnable(Game game, Player player, int seconds) {
        this.game = game;
        this.player = player;
        this.length = seconds;
    }

    @Override
    public void run() {
        if (this.length > 0) {
            this.player
                    .sendMessage(ChatWriter.pluginMessage("Protection: " + this.length + " seconds remaining"));
        }

        if (this.length <= 0) {
            this.player
                    .sendMessage(
                            ChatWriter.pluginMessage("Protection ended!"));
            this.game.removeProtection(this.player);
        }

        this.length--;
    }

    public void runProtection() {
        this.runTaskTimerAsynchronously(Main.getInstance(), 5L, 20L);
    }

}
