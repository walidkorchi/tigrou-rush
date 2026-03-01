package io.github.rush.commands;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.github.rush.Main;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import net.kyori.adventure.text.format.NamedTextColor;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public class ResetIslandsCommand {

    private final Main plugin;

    public ResetIslandsCommand(Main plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("resetislands")
                .requires(ctx -> ctx.getSender().isOp())
                .executes(ctx -> runResetIslands(ctx));
    }

    private int runResetIslands(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        plugin.pasteAllSchematics();

        sender.sendMessage(text("All island schematics have been reset.", NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }
}
