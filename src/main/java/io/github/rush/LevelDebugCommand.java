package io.github.rush;

import io.github.rush.statistics.PlayerLevel;
import io.github.rush.statistics.PlayerStatistic;
import io.github.rush.statistics.PlayerStatisticManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class LevelDebugCommand implements CommandExecutor {

    private final Main plugin;

    public LevelDebugCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cYou must be OP to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§6=== Level Test Commands ===");
            sender.sendMessage("§e/rush:levels addxp <player> <amount> - Add XP to player");
            sender.sendMessage("§e/rush:levels removexp <player> <amount> - Remove XP from player");
            sender.sendMessage("§e/rush:levels recalculate <player> - Recalculate level from stats");
            sender.sendMessage(
                    "§e/rush:levels setstat <player> <stat> <value> - Set stat (wins/loses/kills/assists/deaths)");
            sender.sendMessage("§e/rush:levels info <player> - Show player level info");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "addxp" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rush:levels addxp <player> <amount>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found: " + args[1]);
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid amount: " + args[2]);
                    return true;
                }
                plugin.getPlayerLevelManager().addXP(target.getUniqueId(), amount);
                PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());
                sender.sendMessage("§aAdded " + amount + " XP to " + target.getName() +
                        " (Level: " + pl.getLevel() + ", XP: " + pl.getCurrentXP() + "/" + pl.getXPForNextLevel()
                        + ")");
            }

            case "removexp" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rush:levels removexp <player> <amount>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found: " + args[1]);
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid amount: " + args[2]);
                    return true;
                }
                plugin.getPlayerLevelManager().removeXP(target.getUniqueId(), amount);
                PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());
                sender.sendMessage("§aRemoved " + amount + " XP from " + target.getName() +
                        " (Level: " + pl.getLevel() + ", XP: " + pl.getCurrentXP() + "/" + pl.getXPForNextLevel()
                        + ")");
            }

            case "recalculate" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rush:levels recalculate <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found: " + args[1]);
                    return true;
                }
                plugin.getPlayerLevelManager().recalculateLevelFromStats(target.getUniqueId());
                PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());
                PlayerStatistic stat = plugin.getPlayerStatisticManager().loadStatistic(target.getUniqueId());
                sender.sendMessage("§aRecalculated level for " + target.getName());
                sender.sendMessage("§eStats - Wins: " + stat.getWins() + ", Loses: " + stat.getLoses() +
                        ", Kills: " + stat.getKills() + ", Assists: " + stat.getAssists() + ", Deaths: "
                        + stat.getDeaths());
                sender.sendMessage("§eTotal XP: " + pl.getTotalXP() + " -> Level: " + pl.getLevel() +
                        ", Current XP: " + pl.getCurrentXP() + "/" + pl.getXPForNextLevel());
            }

            case "setstat" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /rush:levels setstat <player> <stat> <value>");
                    sender.sendMessage("§cStats: wins, loses, kills, assists, deaths");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found: " + args[1]);
                    return true;
                }
                String statName = args[2].toLowerCase();
                int value;
                try {
                    value = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid value: " + args[3]);
                    return true;
                }
                PlayerStatisticManager statManager = plugin.getPlayerStatisticManager();
                PlayerStatistic stat = statManager.loadStatistic(target.getUniqueId());

                switch (statName) {
                    case "wins" -> stat.setWins(value);
                    case "loses" -> stat.setLoses(value);
                    case "kills" -> stat.setKills(value);
                    case "assists" -> stat.setAssists(value);
                    case "deaths" -> stat.setDeaths(value);
                    default -> {
                        sender.sendMessage("§cUnknown stat: " + statName);
                        sender.sendMessage("§cValid stats: wins, loses, kills, assists, deaths");
                        return true;
                    }
                }

                statManager.saveStatistic(stat);
                plugin.getPlayerLevelManager().recalculateLevelFromStats(target.getUniqueId());
                PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());
                sender.sendMessage("§aSet " + statName + " = " + value + " for " + target.getName());
                sender.sendMessage(
                        "§eLevel: " + pl.getLevel() + ", XP: " + pl.getCurrentXP() + "/" + pl.getXPForNextLevel());
            }

            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rush:levels info <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found: " + args[1]);
                    return true;
                }
                PlayerLevel pl = plugin.getPlayerLevelManager().loadPlayerLevel(target.getUniqueId());
                PlayerStatistic stat = plugin.getPlayerStatisticManager().loadStatistic(target.getUniqueId());

                sender.sendMessage("§6=== " + target.getName() + " ===");
                sender.sendMessage("§eLevel: " + pl.getLevel() + " " + pl.getFormattedLevel());
                sender.sendMessage("§eXP: " + pl.getCurrentXP() + "/" + pl.getXPForNextLevel() + " (Total: "
                        + pl.getTotalXP() + ")");
                sender.sendMessage("§eStats - Wins: " + stat.getWins() + ", Loses: " + stat.getLoses());
                sender.sendMessage("§eKills: " + stat.getKills() + ", Assists: " + stat.getAssists() + ", Deaths: "
                        + stat.getDeaths());
                sender.sendMessage("§eXP Calculation: (" + stat.getWins() + "×100) + (" + stat.getLoses() + "×20) + (" +
                        stat.getKills() + "×15) + (" + stat.getAssists() + "×5) - (" + stat.getDeaths() + "×10) = "
                        + pl.getTotalXP());
            }

            default -> {
                sender.sendMessage("§cUnknown subcommand: " + subCommand);
                sender.sendMessage("§eUse /rush:levels for help");
            }
        }

        return true;
    }
}
