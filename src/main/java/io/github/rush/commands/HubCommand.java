package io.github.rush.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.rush.Main;
import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.jspecify.annotations.NullMarked;

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

            gameManager.resetPlayerHubState(player);

            return Command.SINGLE_SUCCESS;
        });
    }
}
