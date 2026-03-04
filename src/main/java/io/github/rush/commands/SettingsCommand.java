package io.github.rush.commands;

import io.github.rush.menus.PlayerSettingsGUI;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import net.kyori.adventure.text.format.NamedTextColor;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public class SettingsCommand {

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("settings")
                .executes(ctx -> openSettings(ctx));
    }

    private int openSettings(CommandContext<CommandSourceStack> ctx) {
        final CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("Cette commande ne peut être exécutée que par un joueur.", NamedTextColor.RED));
            return 0;
        }

        PlayerSettingsGUI.openPlayerSettings(player);

        return Command.SINGLE_SUCCESS;
    }
}
