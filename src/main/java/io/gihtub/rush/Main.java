package io.gihtub.rush;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;

import io.gihtub.rush.entities.Merchant;
import io.gihtub.rush.entities.MerchantType;
import io.gihtub.rush.events.Rules;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Villager;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class Main extends JavaPlugin implements Listener {

    private boolean gameStarted = false;

    private static final int ISLAND_OFFSET = 100;
    private static final int ISLAND_Y = 64;

    private static final class Island {
        final int x, y, z;
        final int rotation;

        Island(int x, int y, int z, int rotation) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotation = rotation;
        }
    }

    private List<Island> schematics;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(new Rules(this), this);

        schematics = List.of(
                new Island(-ISLAND_OFFSET, ISLAND_Y, 0, 0),
                new Island(ISLAND_OFFSET, ISLAND_Y, 0, 90),
                new Island(0, ISLAND_Y, -ISLAND_OFFSET, 180),
                new Island(0, ISLAND_Y, ISLAND_OFFSET, 270));

        // temporary for development purposes
        registerCommands();
    }

    private void loadSchematics(CommandSender sender) {
        final Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");

        if (worldEdit == null || !worldEdit.isEnabled()) {
            sender.sendMessage(Component.text("Error: WorldEdit is not loaded!"));
            getLogger().severe("WorldEdit is not loaded!");
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
            int index = 0;
            for (Island schematic : schematics) {
                pasteSchematic(schematic);
                spawnMerchantsInIsland(index);
                index++;
            }
            sender.sendMessage(Component.text("Schematics loaded!"));
        });
    }

    private void spawnMerchantsInIsland(int islandIndex) {
        final World world = Bukkit.getWorld(getGameWorld());
        final Island schematic = schematics.get(islandIndex);

        final int offset = getConfig().getInt("villagerSpawnOffset");
        final int direction = (islandIndex % 2 == 0) ? 1 : -1;

        // Spawn 4 regular merchants around the island
        MerchantType[] regularTypes = {MerchantType.WEAPONSMITH, MerchantType.BUILDER, MerchantType.ALCHEMIST, MerchantType.ARMORSMITH};
        for (int i = 0; i < 4; i++) {
            int x, z;
            if (islandIndex % 2 == 0) {
                // Even: normal pattern
                x = schematic.x + (i < 2 ? -offset : offset);
                z = schematic.z + (i % 2 == 0 ? offset * direction : -offset * direction);
            } else {
                // Odd: swap x and z
                x = schematic.z + (i % 2 == 0 ? offset * direction : -offset * direction);
                z = schematic.x + (i < 2 ? -offset : offset);
            }

            final Location villagerLoc = new Location(world, x + 0.5, schematic.y, z + 0.5);
            final Villager villager = world.spawn(villagerLoc, Villager.class);

            Merchant.apply(villager, regularTypes[i]);
        }

        // Spawn 2 Speed merchants between regular merchants (at half offset)
        int speedOffset = offset / 2;
        for (int i = 0; i < 2; i++) {
            int x, z;
            if (islandIndex % 2 == 0) {
                x = schematic.x + (i == 0 ? -speedOffset : speedOffset);
                z = schematic.z + speedOffset * direction;
            } else {
                x = schematic.z + speedOffset * direction;
                z = schematic.x + (i == 0 ? -speedOffset : speedOffset);
            }

            final Location speedLoc = new Location(world, x + 0.5, schematic.y, z + 0.5);
            final Villager speedVillager = world.spawn(speedLoc, Villager.class);
            Merchant.apply(speedVillager, MerchantType.SPEED);
        }

        getLogger().info("Spawned 4 regular + 2 Speed merchants for island " + islandIndex);
    }

    private void pasteSchematic(Island info) {
        final String schematic = getConfig().getString("schematicFilename");
        final File schematicFile = new File(getDataFolder().getParentFile(), "WorldEdit/schematics/" + schematic);

        getLogger().info("Looking for schematic at: " + schematicFile.getPath());

        if (!schematicFile.exists()) {
            getLogger().warning("Schematic not found: " + schematicFile.getPath());
            return;
        }

        final ClipboardFormat format = ClipboardFormats.findByPath(schematicFile.toPath());

        if (format == null) {
            getLogger().warning("Unknown schematic format: " + schematic);
            return;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            final Clipboard clipboard = reader.read();
            final BlockVector3 dimensions = clipboard.getDimensions();

            getLogger().info(
                    "Schematic dimensions: " + dimensions.x() + "x" + dimensions.y() + "x" + dimensions.z());

            var bukkitWorld = Bukkit.getWorld(getGameWorld());

            if (bukkitWorld == null) {
                getLogger().warning("World not found: " + getGameWorld());
                return;
            }

            getLogger().info("Pasting to world: " + bukkitWorld.getName());

            final com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(bukkitWorld);
            final int minX = info.x;
            final int maxX = info.x + dimensions.x();
            final int minZ = info.z;
            final int maxZ = info.z + dimensions.z();

            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                    bukkitWorld.getChunkAt(cx, cz).load(true);
                }
            }

            try (var editSession = WorldEdit.getInstance().newEditSession(worldEditWorld)) {
                final ClipboardHolder holder = new ClipboardHolder(clipboard);

                if (info.rotation != 0) {
                    AffineTransform transform = new AffineTransform().rotateY(info.rotation);
                    holder.setTransform(holder.getTransform().combine(transform));
                }

                final Operation operation = holder
                        .createPaste(editSession)
                        .to(com.sk89q.worldedit.math.BlockVector3.at(info.x, info.y, info.z))
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
            }

            getLogger().info(
                    "Pasted schematic: " + schematic + " at (" + info.x + ", " + info.y + ", " + info.z + ")");

        } catch (IOException | WorldEditException event) {
            getLogger().severe("Failed to paste schematic " + schematic + ": " + event.getMessage());
        }
    }

    public String getGameWorld() {
        return getConfig().getString("gameWorld");
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public void registerCommands() {
        getCommand("startgame").setExecutor((sender, command, label, args) -> {
            if (gameStarted) {
                sender.sendMessage(Component.text("Game already started!"));
                return true;
            }

            gameStarted = true;
            sender.sendMessage(Component.text("Game started! Loading schematics..."));

            loadSchematics(sender);
            return true;
        });

        getCommand("stopgame").setExecutor((sender, command, label, args) -> {
            if (!gameStarted) {
                sender.sendMessage(Component.text("Game not started!"));
                return true;
            }

            gameStarted = false;
            sender.sendMessage(Component.text("Game stopped!"));
            return true;
        });
    }
}
