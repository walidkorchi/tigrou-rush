package io.github.rush.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.game.RingPath;
import io.github.rush.objects.Island;
import io.github.rush.statistics.PlayerLevel;
import io.github.rush.statistics.PlayerStatistic;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public class DebugCommand {

    private static final int SCAN_RADIUS = 100;

    private final Main plugin;
    private final Map<String, ZonesOvertimeWatcher> overtimeWatchers = new HashMap<>();

    public DebugCommand(Main plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("debug:islands")
                .requires(ctx -> ctx.getSender().isOp())
                .then(zonesBranch())
                .then(levelsBranch());
    }

    private LiteralArgumentBuilder<CommandSourceStack> zonesBranch() {
        return Commands.literal("zones")
                .then(Commands.literal("place").executes(this::placeZones))
                .then(Commands.literal("clear").executes(this::clearZones));
    }

    private LiteralArgumentBuilder<CommandSourceStack> levelsBranch() {
        return Commands.literal("levels")
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
        sender.sendMessage(text("Added " + amount + " XP to " + target.getName()
                + " (Rank: " + pl.getRankIndex() + " [" + rankName + "], TotalXP: " + pl.getTotalXP() + ")",
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

        if (plugin.getTablistManager() != null) {
            plugin.getTablistManager().updatePlayerListName(target);
        }

        LeaderboardCommand lb = plugin.getCommandManager().getLeaderboardCommand();
        if (lb != null)
            lb.updateAllHolograms();

        String rankName = rankIndex >= 0
                ? PlayerLevel.getPrestigeName(rankIndex) + " " + PlayerLevel.getGemName(rankIndex) + " "
                        + PlayerLevel.getLevelInRank(rankIndex)
                : "Non classé";
        sender.sendMessage(text("Set rank for " + target.getName() + " to index " + rankIndex
                + " [" + rankName + "], totalXP set to " + threshold + ".", NamedTextColor.GREEN));

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

    private int placeZones(CommandContext<CommandSourceStack> ctx) {
        return CommandManager.requirePlayer(ctx, player -> {
            CommandSender sender = ctx.getSource().getSender();
            Game game = getRunningGame(player);
            if (game == null) {
                sender.sendMessage(text("No running game found in your world.", NamedTextColor.RED));
                return 0;
            }

            World world = player.getWorld();
            int islandY = resolveIslandY(game, world);

            ZonesOvertimeWatcher prev = overtimeWatchers.remove(world.getName());
            if (prev != null)
                prev.cancel();

            int redPlaced = 0;
            int orangePlaced = 0;
            List<Location> orangePositions = new ArrayList<>();

            for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    Block b = world.getBlockAt(x, islandY, z);
                    if (b.getType() != Material.AIR)
                        continue;

                    Location loc = new Location(world, x, islandY, z);

                    if (!game.isBlockInRingPath(loc)) {
                        b.setType(Material.REDSTONE_BLOCK);
                        redPlaced++;
                        continue;
                    }

                    if (game.isBlockInForbiddenZone(loc)) {
                        b.setType(Material.ORANGE_WOOL);
                        orangePositions.add(loc);
                        orangePlaced++;
                    }
                }
            }

            int platePlaced = placeBridgeEndpointPlates(game, world, islandY);

            sender.sendMessage(text(
                    "Placed " + redPlaced + " redstone (ring forbidden), "
                            + orangePlaced + " orange wool (forbidden until overtime), "
                            + platePlaced + " pressure plates (bridge endpoints).",
                    NamedTextColor.GREEN));

            if (!orangePositions.isEmpty() && !game.isOvertime()) {
                ZonesOvertimeWatcher watcher = new ZonesOvertimeWatcher(game, orangePositions, world.getName());
                overtimeWatchers.put(world.getName(), watcher);
                watcher.start();
            }

            return Command.SINGLE_SUCCESS;
        });
    }

    private int clearZones(CommandContext<CommandSourceStack> ctx) {
        return CommandManager.requirePlayer(ctx, player -> {
            CommandSender sender = ctx.getSource().getSender();
            Game game = getRunningGame(player);
            if (game == null) {
                sender.sendMessage(text("No running game found in your world.", NamedTextColor.RED));
                return 0;
            }

            World world = player.getWorld();
            int islandY = resolveIslandY(game, world);
            int removed = 0;

            for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    Block b = world.getBlockAt(x, islandY, z);
                    Material m = b.getType();
                    if (m == Material.REDSTONE_BLOCK || m == Material.ORANGE_WOOL) {
                        b.setType(Material.AIR);
                        removed++;
                    }
                    Block plate = world.getBlockAt(x, islandY + 1, z);
                    if (plate.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
                        plate.setType(Material.AIR);
                        removed++;
                    }
                }
            }

            ZonesOvertimeWatcher prev = overtimeWatchers.remove(world.getName());
            if (prev != null)
                prev.cancel();

            sender.sendMessage(text("Removed " + removed + " debug blocks.", NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        });
    }

    private int placeBridgeEndpointPlates(Game game, World world, int islandY) {
        List<Island> islands = game.getAllIslandPositions();
        int n = islands.size();
        int placed = 0;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Island a = islands.get(i);
            Island b = islands.get(j);
            double[] ea = RingPath.bridgeEndpoint(a.getX(), a.getZ(), b.getX(), b.getZ());
            double[] eb = RingPath.bridgeEndpoint(b.getX(), b.getZ(), a.getX(), a.getZ());
            for (double[] ep : new double[][] { ea, eb }) {
                int bx = (int) Math.round(ep[0]);
                int bz = (int) Math.round(ep[1]);
                long key = ((long) bx << 32) | (bz & 0xFFFFFFFFL);
                if (seen.add(key)) {
                    world.getBlockAt(bx, islandY + 1, bz).setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
                    placed++;
                }
            }
        }
        return placed;
    }

    private Game getRunningGame(Player player) {
        Game game = plugin.getGameManager().getGameForPlayer(player);
        if (game != null && game.getState() == GameState.RUNNING) {
            return game;
        }
        return null;
    }

    private int resolveIslandY(Game game, World world) {
        if (game.isGameRoomMode() && game.getGameRoom() != null) {
            return game.getGameRoom().getIslandY();
        }
        int y = Main.getISLAND_Y();
        return y > 0 ? y : world.getMaxHeight() - 12;
    }

    private final class ZonesOvertimeWatcher {
        private final Game game;
        private final List<Location> orangePositions;
        private final String worldName;
        private BukkitTask task;

        ZonesOvertimeWatcher(Game game, List<Location> orangePositions, String worldName) {
            this.game = game;
            this.orangePositions = orangePositions;
            this.worldName = worldName;
        }

        void start() {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }

        private void tick() {
            if (game.getState() != GameState.RUNNING) {
                cleanupAndCancel();
                return;
            }
            if (game.isOvertime()) {
                removeOrangeBlocks();
                cleanupAndCancel();
            }
        }

        private void removeOrangeBlocks() {
            for (Location loc : orangePositions) {
                Block b = loc.getBlock();
                if (b.getType() == Material.ORANGE_WOOL) {
                    b.setType(Material.AIR);
                }
            }
        }

        private void cleanupAndCancel() {
            cancel();
            overtimeWatchers.remove(worldName, this);
        }

        void cancel() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }
}
