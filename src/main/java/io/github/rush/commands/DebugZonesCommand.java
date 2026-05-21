package io.github.rush.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.game.RingPath;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.kyori.adventure.text.Component.text;

/**
 * Visualises the block-placement restrictions of the running game.
 *
 * <p>
 * Color legend:
 * <ul>
 * <li><b>Red redstone block</b> — always forbidden (outside the ring).
 * Stays put across the overtime transition.</li>
 * <li><b>Orange wool</b> — forbidden until overtime (the patched corridor
 * between the two playing teams). Automatically removed when the game
 * enters overtime.</li>
 * </ul>
 *
 * <p>
 * Existing non-air blocks (island schematic foundations, beds, anything
 * players placed) are never overwritten — that is all the island protection
 * the visualiser needs. The triangular gaps between the two bridges leaving
 * each island are air at island Y and will correctly show up red.
 */
@NullMarked
public class DebugZonesCommand {

    private static final int SCAN_RADIUS = 100;

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

        OvertimeWatcher prev = overtimeWatchers.remove(world.getName());
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
                Block plate = world.getBlockAt(x, islandY + 1, z);
                if (plate.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
                    plate.setType(Material.AIR);
                    removed++;
                }
            }
        }

        OvertimeWatcher prev = overtimeWatchers.remove(world.getName());
        if (prev != null)
            prev.cancel();

        sender.sendMessage(text("Removed " + removed + " debug blocks.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Places a LIGHT_WEIGHTED_PRESSURE_PLATE at the bridge endpoint of every cyclic
     * island pair. Each island contributes exactly 2 endpoints (one per
     * adjacent bridge). Returns the number of plates placed.
     */
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
