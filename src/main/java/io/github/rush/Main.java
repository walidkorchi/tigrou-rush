package io.github.rush;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;

import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import io.github.rush.commands.CommandManager;
import io.github.rush.config.ConfigManager;
import io.github.rush.entities.*;
import io.github.rush.events.*;
import io.github.rush.objects.*;
import io.github.rush.game.*;
import io.github.rush.replay.ReplayManager;
import io.github.rush.replay.ReplayStorage;
import io.github.rush.settings.PlayerSettingsManager;
import io.github.rush.statistics.*;
import io.github.rush.scoreboard.ScoreboardManager;
import io.github.rush.utils.CustomSoundRegistrar;
import io.github.rush.utils.MusicManager;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.hologram.HologramManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import fr.mrmicky.fastboard.FastBoard;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;
import java.util.ResourceBundle;
import lombok.Getter;
import lombok.Setter;

public class Main extends JavaPlugin {

    @Getter
    private PlayerStatisticManager playerStatisticManager = null;

    @Getter
    private PlayerLevelManager playerLevelManager = null;

    @Getter
    private GameManager gameManager = null;

    @Getter
    private ScoreboardManager scoreboardManager = null;

    @Getter
    private PlayerSettingsManager playerSettingsManager = null;

    @Getter
    private MusicManager musicManager = null;

    @Getter
    private HologramManager hologramManager = null;

    @Getter
    private CommandManager commandManager = null;

    @Getter
    private ConfigManager configManager = null;

    @Getter
    private ReplayStorage replayStorage = null;

    @Getter
    private ReplayManager replayManager = null;

    @Getter
    private net.momirealms.craftengine.core.item.ItemManager craftEngineItemManager;
    private BukkitCraftEngine craftEngine;
    private File craftEngineDataFolder;

    public String getCraftEngineDataFolder() {
        if (craftEngineDataFolder == null) {
            return getDataFolder().getParent() + "/CraftEngine";
        }
        return craftEngineDataFolder.getAbsolutePath();
    }

    public net.momirealms.craftengine.core.entity.player.Player adaptCraftPlayer(org.bukkit.entity.Player player) {
        return net.momirealms.craftengine.bukkit.api.BukkitAdaptor.adapt(player);
    }

    @Getter
    @Setter
    private boolean gameStarted = false;

    @Getter
    private static Main instance = null;

    @Getter
    private static int ISLAND_Y = 0;
    private static int DISTANCE_HEIGHT_LIMIT = 12;

    @Getter
    private List<Island> islands;

    @Getter
    private boolean islandsLoaded = false;

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

    @Override
    public void onEnable() {
        instance = this;

        // Initialize config manager first (other managers may depend on it)
        configManager = new ConfigManager(this);

        scoreboardManager = new ScoreboardManager(this);
        playerSettingsManager = new PlayerSettingsManager(this);
        playerStatisticManager = new PlayerStatisticManager(this);
        playerLevelManager = new PlayerLevelManager(this);
        gameManager = new GameManager(this);
        gameManager.createGame("rush");
        musicManager = new MusicManager(this);
        replayStorage = new ReplayStorage(getDataFolder().toPath().resolve("replays"));
        replayManager = new ReplayManager(this);

        Plugin craftEnginePlugin = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEnginePlugin != null) {
            craftEngineDataFolder = craftEnginePlugin.getDataFolder();
            try {
                BukkitCraftEngine ce = BukkitCraftEngine.instance();
                craftEngine = ce;
                craftEngineItemManager = ce.itemManager();
                CustomSoundRegistrar.register(this);
            } catch (Exception ignored) {
            }
        }

        HologramLib.getManager().ifPresentOrElse(
                manager -> hologramManager = manager,
                () -> getLogger().severe("Failed to initialize HologramLib manager."));

        Bukkit.getPluginManager().registerEvents(new GameRules(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerActivity(this), this);
        Bukkit.getPluginManager().registerEvents(new TNT(this), this);

        commandManager = new CommandManager();
        commandManager.registerAll(this);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commandManager::onCommands);

        // Restore author displays with particles after server restart
        if (commandManager.getAuthorCommand() != null) {
            commandManager.getAuthorCommand().restoreExistingAuthors();
        }

        // Restore leaderboard holograms after server restart
        if (commandManager.getLeaderboardCommand() != null) {
            commandManager.getLeaderboardCommand().restoreLeaderboards();
        }

        final TranslationStore.StringBased<MessageFormat> i18n = TranslationStore
                .messageFormat(Key.key("rush", "main"));

        try {
            final ResourceBundle bundle = ResourceBundle.getBundle("io.github.rush.i18n.Bundle", Locale.FRANCE);

            i18n.registerAll(Locale.FRANCE, bundle, true);
            GlobalTranslator.translator().addSource(i18n);
            getLogger().info("Registered French translations");
        } catch (Exception e) {
            getLogger().warning("Failed to register translations: " + e.getMessage());
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            scoreboardManager.updateAll();
        }, 0L, 1L);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (gameManager != null && gameManager.getCurrentGame() != null) {
                gameManager.getCurrentGame().autoStart();
            }
        }, 0L, 100L);

        PlayerLevel.loadRankImages();
        if (!PlayerLevel.isRanksLoaded()) {
            Bukkit.getScheduler().runTaskLater(this, PlayerLevel::loadRankImages, 1L);
        }

        int islandOffset = getConfig().getInt("islandOffset");

        islands = List.of(
                new Island(-islandOffset, 0, -90),
                new Island(islandOffset, 0, 90),
                new Island(0, -islandOffset, 180),
                new Island(0, islandOffset, 0));
    }

    public void loadSchematics(CommandSender sender) {
        final Plugin fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");

        if (fawe == null || !fawe.isEnabled()) {
            sender.sendMessage(Component.translatable("rush.error_fawe_not_loaded"));
            getLogger().severe("FastAsyncWorldEdit is not loaded!");
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
            loadSchematicsInternal();
            sender.sendMessage(Component.translatable("rush.schematics_loaded"));
        });
    }

    public void loadSchematicsSync() {
        final Plugin fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");

        if (fawe == null || !fawe.isEnabled()) {
            getLogger().severe("FastAsyncWorldEdit is not loaded!");
            return;
        }

        loadSchematicsInternal();
    }

    private void loadSchematicsInternal() {
        if (islandsLoaded) {
            return;
        }

        int index = 0;

        for (Island island : islands) {
            pasteSchematic(island);
            spawnMerchantsInIsland(index);
            index++;
        }

        islandsLoaded = true;
    }

    public void pasteAllSchematics() {
        for (Island island : islands) {
            pasteSchematic(island);
        }
    }

    private Villager spawnMerchant(World world, Location location, MerchantType type) {
        final Villager villager = world.spawn(location, Villager.class);

        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setSilent(true);

        if (type == MerchantType.SPEED) {
            villager.setBaby();
            villager.setAgeLock(true);
        }

        Merchant.apply(villager, type);
        spawnedVillagers.add(villager);

        return villager;
    }

    private void spawnMerchantsInIsland(int islandIndex) {
        final World world = Bukkit.getWorld(getGameWorld());
        final Island island = islands.get(islandIndex);

        final int speedOffset = getConfig().getInt("villagerSpeedOffset");
        final int regularOffset = getConfig().getInt("villagerRegularOffset", speedOffset - 1);

        // direction vectors pointing toward center (0,0): {dirX, dirZ}
        final int[][] directions = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        final float[] yawValues = { -90f, 90f, 0f, 180f };
        final List<Integer> spread = List.of(5, 7);

        final int[] dir = directions[islandIndex];
        final int perpX = dir[1];
        final int perpZ = -dir[0];
        final float facingYaw = yawValues[islandIndex];

        final MerchantType[] regularTypes = MerchantType.firstN(4);

        // spawn speed villagers (2) at direction * speedOffset ± perp
        for (int[] pos : GameManager.speedMerchantPositions(island, dir, perpX, perpZ, speedOffset)) {
            final Location speedLoc = new Location(world, pos[0] + 0.5, ISLAND_Y + 0.5, pos[1] + 0.5, facingYaw, 0);
            spawnMerchant(world, speedLoc, MerchantType.SPEED);
        }

        // spawn regular villagers (4) at direction * regularOffset ± spread
        for (int i = 0; i < 4; i++) {
            int[] pos = GameManager.regularMerchantPos(
                    i, island.getX(), island.getZ(), dir, perpX, perpZ, regularOffset, spread);
            int[] fpos = GameManager.regularMerchantPos(
                    i, island.getX(), island.getZ(), dir, perpX, perpZ, regularOffset + 2, spread);

            final Location villagerLoc = new Location(world, pos[0] + 0.5, ISLAND_Y + 1, pos[1] + 0.5, facingYaw, 0);
            final Villager villager = spawnMerchant(world, villagerLoc, regularTypes[i]);

            merchantVillagers.put(regularTypes[i], villager);

            placeIslandItemFrames(i, world, island, regularTypes, fpos[0], fpos[1]);
        }

        pastedRegions.add(island);
    }

    public void placeIslandItemFrames(int i, World world, Island island, MerchantType[] regularTypes, int frameX,
            int frameZ) {
        final Location frameLoc = new Location(world, frameX + 0.5, ISLAND_Y + 1, frameZ + 0.5);
        final ItemFrame itemFrame = world.spawn(frameLoc, ItemFrame.class);

        itemFrame.setRotation(Rotation.NONE);
        itemFrame.setInvulnerable(true);
        itemFrame.setVisibleByDefault(false);
        itemFrame.setFixed(true);

        ItemStack displayItem = switch (regularTypes[i]) {
            case WEAPONSMITH -> new ItemStack(Material.IRON_SWORD);
            case ARMORSMITH -> new ItemStack(Material.IRON_CHESTPLATE);
            case ALCHEMIST -> {
                ItemStack potion = new ItemStack(Material.POTION);
                PotionContents potionContents = PotionContents.potionContents()
                        .potion(PotionType.HEALING)
                        .build();

                potion.setData(DataComponentTypes.POTION_CONTENTS, potionContents);

                yield potion;
            }
            case BUILDER -> new ItemStack(Material.SANDSTONE);
            default -> null;
        };

        if (displayItem != null) {
            itemFrame.setItem(displayItem);
        }
    }

    public Integer getRespawnProtectionTime() {
        return this.getConfig().getInt("respawn-protection", 4);
    }

    public int getVoidThreshold() {
        return this.getConfig().getInt("void-threshold", 20);
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

    private void clearRegion(World world, Island region) {
        final int radius = getConfig().getInt("islandOffset", 40) + 20;
        final int centerX = region.getX();
        final int centerZ = region.getZ();

        final com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(world);

        final BlockVector3 min = BlockVector3.at(centerX - radius, ISLAND_Y - 5, centerZ - radius);
        final BlockVector3 max = BlockVector3.at(centerX + radius, ISLAND_Y + 20, centerZ + radius);
        final CuboidRegion cuboidRegion = new CuboidRegion(worldEditWorld, min, max);

        try (final EditSession editSession = WorldEdit.getInstance().newEditSession(worldEditWorld)) {
            editSession.setBlocks((Region) cuboidRegion, BlockTypes.AIR.getDefaultState());
        } catch (WorldEditException e) {
            getLogger().severe("Failed to clear region: " + e.getMessage());
        }
    }

    private void pasteSchematic(Island island) {
        final String schematic = getConfig().getString("schematicFilename");
        final File schematicFile = new File(getDataFolder().getParentFile(),
                "FastAsyncWorldEdit/schematics/" + schematic);

        if (!schematicFile.exists()) {
            getLogger().warning("Schematic not found: " + schematicFile.getPath());
            return;
        }

        try {
            final ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                getLogger().severe("Unsupported schematic format: " + schematicFile.getPath());
                return;
            }
            final Clipboard clipboard = format.load(schematicFile);
            final BlockVector3 dimensions = clipboard.getDimensions();

            World bukkitWorld = Bukkit.getWorld(getGameWorld());

            if (bukkitWorld == null) {
                getLogger().warning("World not found: " + getGameWorld());
                return;
            }

            final com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(bukkitWorld);
            final int minX = island.getX();
            final int maxX = island.getX() + dimensions.x();
            final int minZ = island.getZ();
            final int maxZ = island.getZ() + dimensions.z();

            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                    bukkitWorld.getChunkAt(cx, cz).load(true);
                }
            }

            // TODO: change build height limit message
            ISLAND_Y = bukkitWorld.getMaxHeight() - DISTANCE_HEIGHT_LIMIT;

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(worldEditWorld)) {
                final ClipboardHolder holder = new ClipboardHolder(clipboard);

                if (island.getRotation() != 0) {
                    AffineTransform transform = new AffineTransform().rotateY(island.getRotation());
                    holder.setTransform(holder.getTransform().combine(transform));
                }

                final Operation operation = holder
                        .createPaste(editSession)
                        .to(BlockVector3.at(island.getX(), ISLAND_Y, island.getZ()))
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
            }

            getLogger().info(
                    "Pasted schematic: " + schematic + " at (" + island.getX() + ", " + ISLAND_Y + ", " + island.getZ()
                            + ")");

        } catch (IOException event) {
            getLogger().severe("Failed to paste schematic " + schematic + ": " + event.getMessage());
        }
    }

    public String getGameWorld() {
        return getConfig().getString("gameWorld");
    }

    public Villager getMerchantVillager(MerchantType type) {
        return merchantVillagers.get(type);
    }

    public boolean isMerchantVillager(Villager villager) {
        return spawnedVillagers.contains(villager);
    }

    public boolean isSpeedMerchantVillager(Villager villager) {
        return spawnedVillagers.contains(villager) && !merchantVillagers.containsValue(villager);
    }

    public MerchantType getMerchantTypeFor(Villager villager) {
        return merchantVillagers.entrySet().stream()
                .filter(e -> e.getValue().equals(villager))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public boolean isBlockOnIsland(Block block) {
        if (!gameStarted)
            return false;

        int radius = getConfig().getInt("islandOffset", 40) + 10;
        int blockX = block.getX();
        int blockZ = block.getZ();

        for (Island island : pastedRegions) {
            int dx = blockX - island.getX();
            int dz = blockZ - island.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    public Location getSpectatorSpawn() {
        return new Location(
                Bukkit.getWorld(getConfig().getString("gameWorld")),
                0, 100, 0);
    }

    public Location getMainLobby() {
        FileConfiguration config = getConfig();
        if (!config.contains("lobby-spawn"))
            return null;

        String worldName = config.getString("lobby-spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return null;

        return new Location(
                world,
                config.getDouble("lobby-spawn.x"),
                config.getDouble("lobby-spawn.y"),
                config.getDouble("lobby-spawn.z"),
                (float) config.getDouble("lobby-spawn.yaw"),
                (float) config.getDouble("lobby-spawn.pitch"));
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down Rush plugin...");

        // Cancel all scheduled tasks
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("Cancelled all scheduled tasks.");

        // Clean up PlayerActivity action bar task
        // Note: The task is cancelled by the above call, but we need to clean up
        // references

        // Stop all running games and clean up players
        if (gameManager != null) {
            // Clean up legacy games
            for (Game game : new ArrayList<>(gameManager.getGames())) {
                if (game.getState() == GameState.RUNNING || game.getState() == GameState.WAITING) {
                    for (Entity entity : new ArrayList<>(game.getPlayers())) {
                        if (entity instanceof Player player) {
                            game.removePlayer(entity);
                            player.teleport(getMainLobby() != null ? getMainLobby()
                                    : Bukkit.getWorlds().get(0).getSpawnLocation());
                            player.getInventory().clear();
                            player.setGameMode(GameMode.ADVENTURE);
                        }
                    }
                    game.stop();
                }
            }

            // Clean up GameRooms - teleport players out, then remove worlds
            for (GameRoom room : new ArrayList<>(gameManager.getAllGameRooms())) {
                gameManager.removeGameRoom(room.getId());
            }
            getLogger().info("Cleaned up all game rooms.");
        }

        // Remove all scoreboards
        if (scoreboardManager != null) {
            for (Player player : new ArrayList<>(playerBoards.keySet())) {
                scoreboardManager.removeScoreboard(player);
            }
            scoreboardManager.removeAllScoreboards();
            getLogger().info("Removed all scoreboards.");
        }

        // Clean up holograms from AuthorCommand
        if (commandManager != null && commandManager.getAuthorCommand() != null) {
            commandManager.getAuthorCommand().cleanupAll();
            getLogger().info("Cleaned up author holograms.");
        }

        // Clean up holograms from LeaderboardCommand
        if (commandManager != null && commandManager.getLeaderboardCommand() != null) {
            commandManager.getLeaderboardCommand().cleanupAll();
            getLogger().info("Cleaned up leaderboard holograms.");
        }

        // Clear villagers and game entities
        clearGame();

        // Clear player boards map
        playerBoards.clear();

        // Close database managers (must be last)
        if (playerStatisticManager != null) {
            playerStatisticManager.close();
        }
        if (playerLevelManager != null) {
            playerLevelManager.close();
        }

        getLogger().info("Rush plugin shutdown complete.");
    }
}
