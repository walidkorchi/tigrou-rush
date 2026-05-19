package io.github.rush.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.game.Team;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public class DebugZonesCommand {

    private final Main plugin;

    public DebugZonesCommand(Main plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("debugzones")
                .requires(ctx -> ctx.getSender().isOp())
                .then(Commands.literal("place")
                        .executes(this::placeZones))
                .then(Commands.literal("clear")
                        .executes(this::clearZones));
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
        int bedRadius = plugin.getConfig().getInt("bedProtectionRadius", 3);
        int placed = 0;

        // Bed protection zones: cube around each living bed
        for (Team team : game.getTeams().values()) {
            Location bedLoc = team.getBedLocation();
            if (bedLoc == null || team.isBedDestroyed()) continue;
            int bx = (int) bedLoc.getX();
            int by = (int) bedLoc.getY();
            int bz = (int) bedLoc.getZ();
            for (int dx = -bedRadius; dx <= bedRadius; dx++) {
                for (int dy = -bedRadius; dy <= bedRadius; dy++) {
                    for (int dz = -bedRadius; dz <= bedRadius; dz++) {
                        Block b = world.getBlockAt(bx + dx, by + dy, bz + dz);
                        if (b.getType() == Material.AIR) {
                            b.setType(Material.BARRIER);
                            placed++;
                        }
                    }
                }
            }
        }

        // Forbidden zone: flat scan at island Y, ±55 blocks from center
        if (!game.isOvertime()) {
            int islandY = resolveIslandY(game, world);
            int scanRadius = 55;
            for (int x = -scanRadius; x <= scanRadius; x++) {
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    Location loc = new Location(world, x, islandY, z);
                    if (game.isBlockInForbiddenZone(loc)) {
                        Block b = world.getBlockAt(x, islandY, z);
                        if (b.getType() == Material.AIR) {
                            b.setType(Material.BARRIER);
                            placed++;
                        }
                    }
                }
            }
        }

        sender.sendMessage(text("Placed " + placed + " barrier blocks.", NamedTextColor.GREEN));
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
        int removed = 0;
        int islandY = resolveIslandY(game, world);
        int bedRadius = plugin.getConfig().getInt("bedProtectionRadius", 3);

        // Clear forbidden zone scan area
        int scanRadius = 55;
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int z = -scanRadius; z <= scanRadius; z++) {
                Block b = world.getBlockAt(x, islandY, z);
                if (b.getType() == Material.BARRIER) {
                    b.setType(Material.AIR);
                    removed++;
                }
            }
        }

        // Clear bed protection zones
        for (Team team : game.getTeams().values()) {
            Location bedLoc = team.getBedLocation();
            if (bedLoc == null) continue;
            int bx = (int) bedLoc.getX();
            int by = (int) bedLoc.getY();
            int bz = (int) bedLoc.getZ();
            for (int dx = -bedRadius; dx <= bedRadius; dx++) {
                for (int dy = -bedRadius; dy <= bedRadius; dy++) {
                    for (int dz = -bedRadius; dz <= bedRadius; dz++) {
                        Block b = world.getBlockAt(bx + dx, by + dy, bz + dz);
                        if (b.getType() == Material.BARRIER) {
                            b.setType(Material.AIR);
                            removed++;
                        }
                    }
                }
            }
        }

        sender.sendMessage(text("Removed " + removed + " barrier blocks.", NamedTextColor.GREEN));
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
}
