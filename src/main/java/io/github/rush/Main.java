package io.github.rush;

import com.sk89q.worldedit.EditSession;
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

import io.github.rush.entities.Merchant;
import io.github.rush.entities.MerchantType;
import io.github.rush.events.Rules;
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

        int islandOffset = getConfig().getInt("islandOffset");
        schematics = List.of(
                new Island(-islandOffset, ISLAND_Y, 0, 0),
                new Island(islandOffset, ISLAND_Y, 0, 180),
                new Island(0, ISLAND_Y, -islandOffset, 270),
                new Island(0, ISLAND_Y, islandOffset, 90));

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

        final int speedOffset = getConfig().getInt("villagerSpeedOffset", 13);
        final int regularOffset = getConfig().getInt("villagerRegularOffset", 12);
        List<Integer> spread = getConfig().getIntegerList("villagerSpreadDistance");

        if (spread == null || spread.isEmpty()) {
            spread = List.of(5, 7);
        }

        // direction vectors pointing toward center (0,0): {dirX, dirZ}
        final int[][] directions = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        final float[] yawValues = { -90f, 90f, 0f, 180f };

        final int[] dir = directions[islandIndex];
        final int perpX = dir[1];
        final int perpZ = -dir[0];
        final float facingYaw = yawValues[islandIndex];

        final MerchantType[] regularTypes = { MerchantType.WEAPONSMITH, MerchantType.BUILDER,
                MerchantType.ALCHEMIST, MerchantType.ARMORSMITH };

        // spawn speed villagers (2) at direction * speedOffset ± spread[0]
        for (int i = 0; i < 2; i++) {
            final int sign = (i == 0) ? 1 : -1;
            final int speedX = schematic.x + (dir[0] * speedOffset) + (perpX * sign);
            final int speedZ = schematic.z + (dir[1] * speedOffset) + (perpZ * sign);

            final Location speedLoc = new Location(world, speedX + 0.5, schematic.y + 0.5, speedZ + 0.5, facingYaw, 0);
            final Villager speedVillager = world.spawn(speedLoc, Villager.class);

            speedVillager.setAI(false);
            speedVillager.setInvulnerable(true);
            speedVillager.setBaby();

            Merchant.apply(speedVillager, MerchantType.SPEED);
        }

        // spawn regular villagers (4) at direction * regularOffset ± spread
        for (int i = 0; i < 4; i++) {
            final int sign = (i < 2) ? 1 : -1;
            final int spreadIdx = (i % 2 == 0) ? 0 : 1;
            final int regX = schematic.x + (dir[0] * regularOffset) + (perpX * spread.get(spreadIdx) * sign);
            final int regZ = schematic.z + (dir[1] * regularOffset) + (perpZ * spread.get(spreadIdx) * sign);

            final Location villagerLoc = new Location(world, regX + 0.5, schematic.y + 1, regZ + 0.5, facingYaw, 0);
            final Villager villager = world.spawn(villagerLoc, Villager.class);

            villager.setAI(false);
            villager.setInvulnerable(true);

            Merchant.apply(villager, regularTypes[i]);
        }
    }

    private void pasteSchematic(Island info) {
        final String schematic = getConfig().getString("schematicFilename");
        final File schematicFile = new File(getDataFolder().getParentFile(), "WorldEdit/schematics/" + schematic);

        if (!schematicFile.exists()) {
            getLogger().warning("Schematic not found: " + schematicFile.getPath());
            return;
        }

        final ClipboardFormat format = ClipboardFormats.findByPath(schematicFile.toPath());

        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            final Clipboard clipboard = reader.read();
            final BlockVector3 dimensions = clipboard.getDimensions();

            World bukkitWorld = Bukkit.getWorld(getGameWorld());

            if (bukkitWorld == null) {
                getLogger().warning("World not found: " + getGameWorld());
                return;
            }

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

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(worldEditWorld)) {
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
