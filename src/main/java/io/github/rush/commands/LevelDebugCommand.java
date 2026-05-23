package io.github.rush.commands;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.github.rush.Main;
import io.github.rush.statistics.PlayerLevel;
import io.github.rush.statistics.PlayerStatistic;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import net.kyori.adventure.text.format.NamedTextColor;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public class LevelDebugCommand {

    private final Main plugin;

    public LevelDebugCommand(Main plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("levels")
                .requires(ctx -> ctx.getSender().isOp())
                .then(Commands.literal("addxp")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg())
                                        .executes(this::runAddXp))))
                .then(Commands.literal("resetxp")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(this::runResetXp)))
                .then(Commands.literal("setrank")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .then(Commands.argument("rankIndex", IntegerArgumentType.integer(-1, 35))
                                        .executes(this::runSetRank))))
                .then(Commands.literal("info")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(this::runInfo)));
    }

    private int runAddXp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        long amount = ctx.getArgument("amount", Long.class);
        CommandSender sender = ctx.getSource().getSender();

        plugin.getPlayerLevelManager().addXP(target.getUniqueId(), amount);
        PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());

        LeaderboardCommand lb = plugin.getCommandManager().getLeaderboardCommand();
        if (lb != null)
            lb.updateAllHolograms();

        String rankName = pl.getRankIndex() >= 0
                ? PlayerLevel.getPrestigeName(pl.getRankIndex()) + " " + PlayerLevel.getGemName(pl.getRankIndex()) + " "
                        + PlayerLevel.getLevelInRank(pl.getRankIndex())
                : "Non classé";
        sender.sendMessage(text("Added " + amount + " XP to " + target.getName() +
                " (Rank: " + pl.getRankIndex() + " [" + rankName + "], TotalXP: " + pl.getTotalXP() + ")",
                NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }

    private int runResetXp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        CommandSender sender = ctx.getSource().getSender();

        plugin.getPlayerLevelManager().resetXP(target.getUniqueId());

        sender.sendMessage(text("Reset XP for " + target.getName() + " (totalXP=0, rankIndex=-1).",
                NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }

    private int runSetRank(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        int rankIndex = ctx.getArgument("rankIndex", Integer.class);
        CommandSender sender = ctx.getSource().getSender();

        PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());
        long threshold = rankIndex < 0 ? 0 : PlayerLevel.getRankThreshold(rankIndex);
        pl.setTotalXP(threshold);
        plugin.getPlayerLevelManager().savePlayerLevel(pl);

        LeaderboardCommand lb = plugin.getCommandManager().getLeaderboardCommand();
        if (lb != null)
            lb.updateAllHolograms();

        String rankName = rankIndex >= 0
                ? PlayerLevel.getPrestigeName(rankIndex) + " " + PlayerLevel.getGemName(rankIndex) + " "
                        + PlayerLevel.getLevelInRank(rankIndex)
                : "Non classé";
        sender.sendMessage(text("Set rank for " + target.getName() + " to index " + rankIndex +
                " [" + rankName + "], totalXP set to " + threshold + ".", NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }

    private int runInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        CommandSender sender = ctx.getSource().getSender();

        PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());
        PlayerStatistic stat = plugin.getPlayerStatisticManager().loadStatistic(target.getUniqueId());

        int rankIndex = pl.getRankIndex();
        String rankName = rankIndex >= 0
                ? PlayerLevel.getPrestigeName(rankIndex) + " " + PlayerLevel.getGemName(rankIndex) + " "
                        + PlayerLevel.getLevelInRank(rankIndex)
                : "Non classé";

        sender.sendMessage(text("=== " + target.getName() + " ===", NamedTextColor.GOLD));
        sender.sendMessage(text("Rank: #" + rankIndex + " [" + rankName + "]", NamedTextColor.YELLOW));
        sender.sendMessage(text("TotalXP: " + pl.getTotalXP() + "  Progress: " + pl.getProgressInRank() + "/"
                + pl.getXPForCurrentRange(), NamedTextColor.YELLOW));
        sender.sendMessage(text("To next rank: " + pl.getXPToNextRank() + " XP", NamedTextColor.YELLOW));
        sender.sendMessage(
                text("Stats - Wins: " + stat.getWins() + ", Loses: " + stat.getLoses(), NamedTextColor.YELLOW));
        sender.sendMessage(
                text("Kills: " + stat.getKills() + ", Assists: " + stat.getAssists() + ", Deaths: " + stat.getDeaths(),
                        NamedTextColor.YELLOW));

        return Command.SINGLE_SUCCESS;
    }
}
