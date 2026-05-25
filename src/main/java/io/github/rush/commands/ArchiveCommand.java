package io.github.rush.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.rush.Main;
import io.github.rush.replay.ReplayFile;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ArchiveCommand {

    private final Main plugin;

    public ArchiveCommand(Main plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("rusharchive")
                .then(Commands.literal("yes").executes(ctx -> handleChoice(ctx, true)))
                .then(Commands.literal("no").executes(ctx -> handleChoice(ctx, false)));
    }

    private int handleChoice(CommandContext<CommandSourceStack> ctx, boolean archive) {
        return CommandManager.requirePlayer(ctx, player -> {
            ReplayFile replayFile = plugin.getGameManager().consumePendingArchive(player.getUniqueId());
            if (replayFile == null) {
                player.sendMessage(Component.text("Il n'est plus possible de rechoisir l'archivage de la game.")
                        .color(NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }
            if (archive) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                        () -> plugin.getReplayStorage().save(replayFile));
                player.sendMessage(Component.text("La partie a été archivée et pourra être revisitée ultérieurement.")
                        .color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(
                        Component.text("Cette partie ne sera pas archivée, et donc ne pourra pas être revisitée.")
                                .color(NamedTextColor.GRAY));
            }
            return Command.SINGLE_SUCCESS;
        });
    }
}
