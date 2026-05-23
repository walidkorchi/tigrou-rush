package io.github.rush.commands;

import io.github.rush.menus.PlayerSettingsGUI;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SettingsCommand {

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("settings")
                .executes(ctx -> openSettings(ctx));
    }

    private int openSettings(CommandContext<CommandSourceStack> ctx) {
        return CommandManager.requirePlayer(ctx, player -> {
            PlayerSettingsGUI.openPlayerSettings(player);
            return Command.SINGLE_SUCCESS;
        });
    }
}
