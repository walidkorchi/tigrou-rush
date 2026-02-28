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
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import io.github.rush.entities.*;
import io.github.rush.events.*;
import io.github.rush.objects.*;
import io.github.rush.game.*;
import io.github.rush.statistics.*;
import io.github.rush.scoreboard.ScoreboardManager;
import fr.mrmicky.fastboard.FastBoard;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import lombok.Getter;

public class Main extends JavaPlugin {

    private PlayerStatisticManager playerStatisticManager = null;
    @Getter
    private GameManager gameManager = null;
    @Getter
    private ScoreboardManager scoreboardManager = null;
    private boolean gameStarted = false;
    private static Main instance = null;

    private static final int ISLAND_Y = 64;

    private List<Island> schematics;

    private final List<Villager> spawnedVillagers = new ArrayList<>();
    private final List<Island> pastedRegions = new ArrayList<>();

    private final Map<MerchantType, Villager> merchantVillagers = new HashMap<>();
    private final Map<Player, FastBoard> playerBoards = new HashMap<>();

    public FastBoard getFastBoard(Player player) {
        return playerBoards.get(player);
    }

    public void setFastBoard(Player player, FastBoard board) {
        playerBoards.put(player, board);
    }

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

    @Override
    public void onEnable() {
        instance = this;

        scoreboardManager = new ScoreboardManager(this);
        playerStatisticManager = new PlayerStatisticManager(this);
        gameManager = new GameManager(this);
        gameManager.createGame("rush");

        Bukkit.getPluginManager().registerEvents(new GameRules(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerActivity(this), this);
        Bukkit.getPluginManager().registerEvents(new TNT(this), this);
        Bukkit.getPluginManager().registerEvents(new VillagerInteraction(), this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            scoreboardManager.updateAll();
        }, 0L, 40L);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (gameManager != null && gameManager.getCurrentGame() != null) {
                gameManager.getCurrentGame().autoStart();
            }
        }, 0L, 600L);

        int islandOffset = getConfig().getInt("islandOffset");
        schematics = List.of(
                new Island(-islandOffset, ISLAND_Y, 0, 0),
                new Island(islandOffset, ISLAND_Y, 0, 180),
                new Island(0, ISLAND_Y, -islandOffset, 270),
                new Island(0, ISLAND_Y, islandOffset, 90));
    }

    public void loadSchematics(CommandSender sender) {
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

            spawnedVillagers.add(speedVillager);

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
            spawnedVillagers.add(villager);

            villager.setAI(false);
            villager.setInvulnerable(true);

            Merchant.apply(villager, regularTypes[i]);
            merchantVillagers.put(regularTypes[i], villager);

            // Spawn item frame 2 blocks in front of villager with corresponding item
            final int frameX = schematic.x + (dir[0] * (regularOffset + 2)) + (perpX * spread.get(spreadIdx) * sign);
            final int frameZ = schematic.z + (dir[1] * (regularOffset + 2)) + (perpZ * spread.get(spreadIdx) * sign);
            final Location frameLoc = new Location(world, frameX + 0.5, schematic.y + 1, frameZ + 0.5);

            final ItemFrame itemFrame = world.spawn(frameLoc, ItemFrame.class);

            itemFrame.setRotation(Rotation.NONE);
            itemFrame.setInvulnerable(true);
            itemFrame.setVisibleByDefault(false);
            itemFrame.setFixed(true);

            ItemStack displayItem = switch (regularTypes[i]) {
                case WEAPONSMITH -> new ItemStack(org.bukkit.Material.IRON_SWORD);
                case ARMORSMITH -> new ItemStack(org.bukkit.Material.IRON_CHESTPLATE);
                case ALCHEMIST -> new ItemStack(org.bukkit.Material.GOLDEN_APPLE);
                case BUILDER -> new ItemStack(org.bukkit.Material.SANDSTONE);
                default -> null;
            };

            if (displayItem != null) {
                itemFrame.setItem(displayItem);
            }
        }

        pastedRegions.add(schematic);
    }

    public Integer getRespawnProtectionTime() {
        final FileConfiguration config = this.getConfig();

        if (config.contains("respawn-protection") && config.isInt("respawn-protection")) {
            return config.getInt("respawn-protection");
        } else {
            return 0;
        }
    }

    public void clearGame() {
        for (Villager villager : spawnedVillagers) {
            if (villager != null && !villager.isDead()) {
                villager.remove();
            }
        }

        spawnedVillagers.clear();
        merchantVillagers.clear();

        World world = Bukkit.getWorld(getGameWorld());

        if (world != null) {
            for (Island region : pastedRegions) {
                clearRegion(world, region);
            }
        }

        pastedRegions.clear();
    }

    public PlayerStatisticManager getPlayerStatisticManager() {
        return this.playerStatisticManager;
    }

    private void clearRegion(World world, Island region) {
        final int radius = getConfig().getInt("islandOffset", 40) + 20;
        final int centerX = region.x;
        final int centerZ = region.z;
        final int centerY = region.y;

        final com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(world);

        final BlockVector3 min = BlockVector3.at(centerX - radius, centerY - 5, centerZ - radius);
        final BlockVector3 max = BlockVector3.at(centerX + radius, centerY + 20, centerZ + radius);
        final CuboidRegion cuboidRegion = new CuboidRegion(worldEditWorld, min, max);

        try (final EditSession editSession = WorldEdit.getInstance().newEditSession(worldEditWorld)) {
            editSession.setBlocks(cuboidRegion, BlockTypes.AIR.getDefaultState());
        } catch (WorldEditException e) {
            getLogger().severe("Failed to clear region: " + e.getMessage());
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

    public void setGameStarted(boolean started) {
        this.gameStarted = started;
    }

    public Villager getMerchantVillager(MerchantType type) {
        return merchantVillagers.get(type);
    }

    public boolean isBlockOnIsland(org.bukkit.block.Block block) {
        if (!gameStarted)
            return false;

        int radius = getConfig().getInt("islandOffset", 40) + 10;
        int blockX = block.getX();
        int blockZ = block.getZ();

        for (Island island : pastedRegions) {
            int dx = blockX - island.x;
            int dz = blockZ - island.z;
            if (dx * dx + dz * dz <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the max length of a game in seconds, fallbacks to 60 minutes if not
     * defined in plugin config
     *
     * @return The length of the game in seconds
     */
    public int getMaxLength() {
        if (this.getConfig().contains("gamelength") && this.getConfig().isInt("gamelength")) {
            return this.getConfig().getInt("gamelength") * 60;
        } else
            return 60 * 60;
    }

    public static Main getInstance() {
        return Main.instance;
    }

    public Object getBugsnag() {
        return null;
    }

    public String getCurrentVersion() {
        return "1.21.11";
    }

    public boolean getBooleanConfig(String path, boolean defaultValue) {
        return getConfig().getBoolean(path, defaultValue);
    }

    public String getStringConfig(String path, String defaultValue) {
        return getConfig().getString(path, defaultValue);
    }

    public Class<?> getVersionRelatedClass(String className) {
        return null;
    }

    public boolean statisticsEnabled() {
        return false;
    }

    public Location getSpectatorSpawn() {
        return new Location(
            Bukkit.getWorld(getConfig().getString("lobbyWorld", "world")),
            0, 100, 0
        );
    }

    public boolean isHologramsEnabled() {
        return false;
    }

    public Object getHolographicInteractor() {
        return null;
    }

    public void toMainLobby(org.bukkit.entity.Player player) {
    }

    public org.bukkit.Location getMainLobby() {
        return null;
    }

    public static String _l(org.bukkit.command.CommandSender sender, String key) {
        return key;
    }

    public static String _l(org.bukkit.command.CommandSender sender, String key, java.util.Map<String, String> params) {
        return key;
    }

    public int getIntConfig(String path, int defaultValue) {
        return getConfig().getInt(path, defaultValue);
    }

    public io.github.rush.game.GameManager getGameManager() {
        return this.gameManager;
    }

    public io.github.rush.scoreboard.ScoreboardManager getScoreboardManager() {
        return this.scoreboardManager;
    }

    @Override
    public void onDisable() {
        if (playerStatisticManager != null) {
            playerStatisticManager.close();
        }
    }
}
