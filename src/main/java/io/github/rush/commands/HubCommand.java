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
import org.jspecify.annotations.NullMarked;

import net.kyori.adventure.text.Component;

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
        return CommandManager.requirePlayer(ctx, player -> {
            if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
                plugin.getReplayManager().leaveReplay(player);
            }

            final GameManager gameManager = plugin.getGameManager();

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

            final Location lobby = plugin.getMainLobby();

            if (lobby == null || lobby.getWorld() == null) {
                player.sendMessage(Component.translatable("rush.no_lobby_spawn"));
                return Command.SINGLE_SUCCESS;
            }

            player.setGameMode(GameMode.SURVIVAL);

            final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);

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
        });
    }
}
