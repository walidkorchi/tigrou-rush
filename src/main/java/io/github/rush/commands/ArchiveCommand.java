package io.github.rush.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.rush.Main;
import io.github.rush.utils.ReplayUtils.ReplayFile;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
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
            final ReplayFile replayFile = plugin.getGameManager().consumePendingArchive(player.getUniqueId());

            if (replayFile == null) {
                player.sendMessage(Component.translatable("rush.archive_expired"));
                return Command.SINGLE_SUCCESS;
            }

            if (archive) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                        () -> plugin.getReplayStorage().save(replayFile));
                player.sendMessage(Component.translatable("rush.archive_saved"));
            } else {
                player.sendMessage(Component.translatable("rush.archive_skipped"));
            }

            return Command.SINGLE_SUCCESS;
        });
    }
}
