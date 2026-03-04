package io.github.rush.commands;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameManager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import net.kyori.adventure.text.format.NamedTextColor;

import static net.kyori.adventure.text.Component.text;

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

        game.forceStart();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(text("§c⚠ Un administrateur a forcé le démarrage de la partie!"));
        }

        sender.sendMessage(text("Game force started with 5 second countdown.", NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }
}
