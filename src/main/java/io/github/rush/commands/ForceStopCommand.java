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

import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import net.kyori.adventure.text.format.NamedTextColor;

import static net.kyori.adventure.text.Component.text;

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
        CommandSender sender = ctx.getSource().getSender();

        GameManager gameManager = plugin.getGameManager();
        if (gameManager == null) {
            sender.sendMessage(text("Game manager not available.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Game game = gameManager.getCurrentGame();
        if (game == null) {
            sender.sendMessage(text("No active game found.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        if (game.getState() != GameState.RUNNING) {
            sender.sendMessage(text("Game is not currently running.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        game.forceStop();

        for (var player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(text("§c⚠ Un administrateur a forcé l'arrêt de la partie!"));
        }

        sender.sendMessage(text("Game force stopped.", NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }
}
