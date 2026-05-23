package io.github.rush.commands;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameManager;
import io.github.rush.game.GameState;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.jspecify.annotations.NullMarked;

import net.kyori.adventure.text.Component;

@NullMarked
public class ForceStopCommand {

    private final Main plugin;

    public ForceStopCommand(Main plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("forcestop")
                .requires(ctx -> ctx.getSender().isOp())
                .executes(ctx -> runForceStop(ctx));
    }

    private int runForceStop(CommandContext<CommandSourceStack> ctx) {
        return CommandManager.requirePlayer(ctx, player -> {
            GameManager gameManager = plugin.getGameManager();
            if (gameManager == null) {
                ctx.getSource().getSender().sendMessage(Component.translatable("rush.game_manager_unavailable"));
                return Command.SINGLE_SUCCESS;
            }

            Game game = gameManager.getGameForPlayer(player);
            if (game != null && game.getState() == GameState.RUNNING) {
                if (game.isGameRoomMode()) {
                    gameManager.removeGameRoom(game.getGameRoom().getId());
                } else {
                    game.forceStop();
                    plugin.clearGame();
                    for (var p : plugin.getServer().getOnlinePlayers()) {
                        p.sendMessage(Component.translatable("rush.force_stop_broadcast"));
                    }
                }
                ctx.getSource().getSender().sendMessage(Component.translatable("rush.game_force_stopped"));
                return Command.SINGLE_SUCCESS;
            }

            ctx.getSource().getSender().sendMessage(Component.translatable("rush.no_running_game"));
            return Command.SINGLE_SUCCESS;
        });
    }
}
