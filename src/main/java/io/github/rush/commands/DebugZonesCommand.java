package io.github.rush.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.objects.Island;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
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
import java.util.List;
import java.util.Map;

import static net.kyori.adventure.text.Component.text;

/**
 * Visualizes block-placement restrictions for the current running game.
 *
 * Color legend:
 *   REDSTONE_BLOCK — always forbidden (outside the ring path)
 *   ORANGE_WOOL    — forbidden until overtime (corridor between 2 playing teams)
 */
@NullMarked
public class DebugZonesCommand {

    private static final int SCAN_RADIUS = 100;
    private static final int ISLAND_FOOTPRINT_HALF = 16;

    private final Main plugin;
    private final Map<String, OvertimeWatcher> overtimeWatchers = new HashMap<>();

    public DebugZonesCommand(Main plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("debugzones")
                .requires(ctx -> ctx.getSender().isOp())
                .then(Commands.literal("place").executes(this::placeZones))
                .then(Commands.literal("clear").executes(this::clearZones));
    }

    private int placeZones(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("Must be run by a player.", NamedTextColor.RED));
            return 0;
        }

        Game game = getRunningGame(player);
        if (game == null) {
            sender.sendMessage(text("No running game found in your world.", NamedTextColor.RED));
            return 0;
        }

        World world = player.getWorld();
        int islandY = resolveIslandY(game, world);
        List<Island> islands = game.getAllIslandPositions();

        OvertimeWatcher prev = overtimeWatchers.remove(world.getName());
        if (prev != null) prev.cancel();

        int redPlaced = 0;
        int orangePlaced = 0;
        List<Location> orangePositions = new ArrayList<>();

        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                if (isInsideIslandFootprint(x, z, islands)) continue;

                Block b = world.getBlockAt(x, islandY, z);
                if (b.getType() != Material.AIR) continue;

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

        sender.sendMessage(text(
                "Placed " + redPlaced + " redstone (ring forbidden) and "
                        + orangePlaced + " orange wool (forbidden until overtime).",
                NamedTextColor.GREEN));

        if (!orangePositions.isEmpty() && !game.isOvertime()) {
            OvertimeWatcher watcher = new OvertimeWatcher(game, orangePositions, world.getName());
            overtimeWatchers.put(world.getName(), watcher);
            watcher.start();
        }

        return Command.SINGLE_SUCCESS;
    }

    private int clearZones(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("Must be run by a player.", NamedTextColor.RED));
            return 0;
        }

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
            }
        }

        OvertimeWatcher prev = overtimeWatchers.remove(world.getName());
        if (prev != null) prev.cancel();

        sender.sendMessage(text("Removed " + removed + " debug blocks.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
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

    private boolean isInsideIslandFootprint(int x, int z, List<Island> islands) {
        for (Island island : islands) {
            if (Math.abs(x - island.getX()) < ISLAND_FOOTPRINT_HALF
                    && Math.abs(z - island.getZ()) < ISLAND_FOOTPRINT_HALF) {
                return true;
            }
        }
        return false;
    }

    private final class OvertimeWatcher {
        private final Game game;
        private final List<Location> orangePositions;
        private final String worldName;
        private BukkitTask task;

        OvertimeWatcher(Game game, List<Location> orangePositions, String worldName) {
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
