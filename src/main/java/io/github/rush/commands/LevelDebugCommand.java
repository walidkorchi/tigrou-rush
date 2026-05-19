package io.github.rush.commands;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.github.rush.Main;
import io.github.rush.statistics.PlayerLevel;
import io.github.rush.statistics.PlayerStatistic;
import io.github.rush.statistics.PlayerStatisticManager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> runAddXp(ctx)))))
                .then(Commands.literal("removexp")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> runRemoveXp(ctx)))))
                .then(Commands.literal("recalculate")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(ctx -> runRecalculate(ctx))))
                .then(Commands.literal("setstat")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                .executes(ctx -> runSetStat(ctx))))))
                .then(Commands.literal("info")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(ctx -> runInfo(ctx))));
    }

    private int runAddXp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        int amount = ctx.getArgument("amount", Integer.class);
        CommandSender sender = ctx.getSource().getSender();

        plugin.getPlayerLevelManager().addXP(target.getUniqueId(), amount);
        PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());

        var lb = plugin.getCommandManager().getLeaderboardCommand();
        if (lb != null) lb.updateAllHolograms();

        sender.sendMessage(text("Added " + amount + " XP to " + target.getName() +
                " (Level: " + pl.getLevel() + ", XP: " + pl.getCurrentXP() + "/" + pl.getXPForNextLevel() + ")",
                NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }

    private int runRemoveXp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        int amount = ctx.getArgument("amount", Integer.class);
        CommandSender sender = ctx.getSource().getSender();

        plugin.getPlayerLevelManager().removeXP(target.getUniqueId(), amount);
        PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());

        sender.sendMessage(text("Removed " + amount + " XP from " + target.getName() +
                " (Level: " + pl.getLevel() + ", XP: " + pl.getCurrentXP() + "/" + pl.getXPForNextLevel() + ")",
                NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }

    private int runRecalculate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        CommandSender sender = ctx.getSource().getSender();

        plugin.getPlayerLevelManager().recalculateLevelFromStats(target.getUniqueId());
        PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());
        PlayerStatistic stat = plugin.getPlayerStatisticManager().loadStatistic(target.getUniqueId());

        sender.sendMessage(text("Recalculated level for " + target.getName(), NamedTextColor.GREEN));
        sender.sendMessage(text("Stats - Wins: " + stat.getWins() + ", Loses: " + stat.getLoses() +
                ", Kills: " + stat.getKills() + ", Assists: " + stat.getAssists() + ", Deaths: " + stat.getDeaths(),
                NamedTextColor.YELLOW));
        sender.sendMessage(text("Total XP: " + pl.getTotalXP() + " -> Level: " + pl.getLevel() +
                ", Current XP: " + pl.getCurrentXP() + "/" + pl.getXPForNextLevel(), NamedTextColor.YELLOW));

        return Command.SINGLE_SUCCESS;
    }

    private int runSetStat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        String statName = ctx.getArgument("stat", String.class);
        int value = ctx.getArgument("value", Integer.class);
        CommandSender sender = ctx.getSource().getSender();

        PlayerStatisticManager statManager = plugin.getPlayerStatisticManager();
        PlayerStatistic stat = statManager.loadStatistic(target.getUniqueId());

        switch (statName.toLowerCase()) {
            case "wins" -> stat.setWins(value);
            case "loses" -> stat.setLoses(value);
            case "kills" -> stat.setKills(value);
            case "assists" -> stat.setAssists(value);
            case "deaths" -> stat.setDeaths(value);
            default -> {
                sender.sendMessage(text("Unknown stat: " + statName, NamedTextColor.RED));
                sender.sendMessage(text("Valid stats: wins, loses, kills, assists, deaths", NamedTextColor.RED));
                return 0;
            }
        }

        statManager.saveStatistic(stat);
        plugin.getPlayerLevelManager().recalculateLevelFromStats(target.getUniqueId());
        PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());

        sender.sendMessage(text("Set " + statName + " = " + value + " for " + target.getName(), NamedTextColor.GREEN));
        sender.sendMessage(text("Level: " + pl.getLevel() + ", XP: " + pl.getCurrentXP() + "/" + pl.getXPForNextLevel(),
                NamedTextColor.YELLOW));

        return Command.SINGLE_SUCCESS;
    }

    private int runInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        CommandSender sender = ctx.getSource().getSender();

        PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());
        PlayerStatistic stat = plugin.getPlayerStatisticManager().loadStatistic(target.getUniqueId());

        sender.sendMessage(text("=== " + target.getName() + " ===", NamedTextColor.GOLD));
        sender.sendMessage(text("Level: " + pl.getLevel() + " " + pl.getFormattedLevel(), NamedTextColor.YELLOW));
        sender.sendMessage(text("XP: " + pl.getCurrentXP() + "/" + pl.getXPForNextLevel() + " (Total: " + pl.getTotalXP() + ")",
                NamedTextColor.YELLOW));
        sender.sendMessage(text("Stats - Wins: " + stat.getWins() + ", Loses: " + stat.getLoses(), NamedTextColor.YELLOW));
        sender.sendMessage(text("Kills: " + stat.getKills() + ", Assists: " + stat.getAssists() + ", Deaths: " + stat.getDeaths(),
                NamedTextColor.YELLOW));
        sender.sendMessage(text("XP Calculation: (" + stat.getWins() + "×100) + (" + stat.getLoses() + "×20) + (" +
                stat.getKills() + "×15) + (" + stat.getAssists() + "×5) - (" + stat.getDeaths() + "×10) = " + pl.getTotalXP(),
                NamedTextColor.YELLOW));

        return Command.SINGLE_SUCCESS;
    }
}
