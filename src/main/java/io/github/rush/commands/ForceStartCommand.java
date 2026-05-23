package io.github.rush.commands;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import net.kyori.adventure.text.Component;

@NullMarked
public class ForceStartCommand {

    private final Main plugin;

    public ForceStartCommand(Main plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("forcestart")
                .requires(ctx -> ctx.getSender().isOp())
                .executes(ctx -> runForceStart(ctx));
    }

    private int runForceStart(CommandContext<CommandSourceStack> ctx) {
        return CommandManager.requirePlayer(ctx, player -> {
            GameManager gameManager = plugin.getGameManager();

            if (gameManager == null) {
                ctx.getSource().getSender().sendMessage(Component.translatable("rush.game_manager_unavailable"));
                return Command.SINGLE_SUCCESS;
            }

            GameRoom room = gameManager.getGameRoomByWorld(player.getWorld().getName());
            if (room != null && room.isWaiting()) {
                room.getGame().start();
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (online.getWorld().equals(room.getWorld())) {
                        online.sendMessage(Component.translatable("rush.force_start_broadcast"));
                    }
                }
                ctx.getSource().getSender().sendMessage(Component.translatable("rush.game_force_started"));
                return Command.SINGLE_SUCCESS;
            }

            Game game = gameManager.getCurrentGame();

            if (game == null) {
                ctx.getSource().getSender().sendMessage(Component.translatable("rush.no_active_game"));
                return Command.SINGLE_SUCCESS;
            }

            game.start();

            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.getWorld().getName().equals(plugin.getGameWorld())) {
                    online.sendMessage(Component.translatable("rush.force_start_broadcast"));
                }
            }

            ctx.getSource().getSender().sendMessage(Component.translatable("rush.game_force_started"));

            return Command.SINGLE_SUCCESS;
        });
    }
}
