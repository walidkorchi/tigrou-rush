package io.github.rush.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.rush.Main;
import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public class HubCommand {

    private final Main plugin;

    public HubCommand(Main plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("hub").executes(this::run);
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("§cCette commande doit être exécutée par un joueur."));
            return Command.SINGLE_SUCCESS;
        }

        if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
            plugin.getReplayManager().leaveReplay(player);
        }

        GameManager gameManager = plugin.getGameManager();
        if (gameManager != null) {
            GameRoom room = gameManager.getGameRoomOfPlayer(player);
            if (room != null) {
                room.removePlayer(player);
                gameManager.removePlayerFromGameRoom(player);
                if (room.getPlayerCount() == 0 && room.isWaiting()) {
                    gameManager.removeGameRoom(room.getId());
                }
            }
        }

        Location lobby = plugin.getMainLobby();
        if (lobby == null || lobby.getWorld() == null) {
            player.sendMessage(text("§cAucun spawn du hub n'est configuré. Contactez un administrateur."));
            return Command.SINGLE_SUCCESS;
        }

        player.setGameMode(GameMode.SURVIVAL);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFallDistance(0);
        player.teleport(lobby);
        if (gameManager != null) {
            gameManager.restoreHubInventory(player);
        }

        return Command.SINGLE_SUCCESS;
    }
}
