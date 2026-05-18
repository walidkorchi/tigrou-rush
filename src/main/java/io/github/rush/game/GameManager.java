package io.github.rush.game;

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
import io.github.rush.Main;
import io.github.rush.menus.GUI;
import io.github.rush.menus.TeamSelectionGUI;
import io.github.rush.world.VoidGenerator;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import net.kyori.adventure.util.TriState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Manages multiple game rooms and all game-related operations.
 */
public class GameManager {

    private final Main plugin;
    private final Map<String, GameRoom> gameRooms = new HashMap<>();
    private final Map<Player, GameRoom> playerGameRoomMap = new HashMap<>();
    private final Map<String, Game> legacyGames = new HashMap<>();
    private final Map<Player, Game> playerGameMap = new HashMap<>();

    private int worldCounter = 0;

    public GameManager(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a new game room with its own world.
     * Shows loading progress on player's action bar.
     */
    public void createGameRoom(Player host, GameRoom.IslandType islandType, GameRoom.TeamSize teamSize) {
        final String worldName = "rush_game_" + (++worldCounter) + "_" + host.getName();

        // Step 1: Creating world
        sendLoadingBar(host, "Création du monde", 0, 3);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                World gameWorld = createVoidWorld(worldName);
                if (gameWorld == null) {
                    host.sendMessage(Component.text("§cErreur lors de la création du monde!"));
                    return;
                }

                // Step 2: World created, preparing lobby
                sendLoadingBar(host, "Préparation du lobby", 1, 3);

                Location lobbyLocation = new Location(gameWorld, 1000, 64, 1000);
                GameRoom room = new GameRoom(host.getName(), gameWorld, islandType, teamSize, lobbyLocation);

                // Paste lobby schematic asynchronously with progress updates
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    // Step 3: Pasting schematic
                    sendLoadingBar(host, "Génération de la carte", 2, 3);

                    pasteLobbySchematic(gameWorld, lobbyLocation);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // Step 4: Finalizing
                        sendLoadingBar(host, "Finalisation", 3, 3);
                        finalizeGameRoomCreation(host, room, islandType, teamSize);

                        // Clear action bar after a short delay
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            host.sendActionBar(Component.empty());
                        }, 40L);
                    });
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error creating game world: " + e.getMessage());
                e.printStackTrace();
                host.sendMessage(Component.text("§cErreur lors de la création de la partie!"));
            }
        });
    }

    /**
     * Sends a loading bar to a player's action bar.
     */
    private void sendLoadingBar(Player player, String message, int currentStep, int totalSteps) {
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < totalSteps; i++) {
            if (i < currentStep) {
                bar.append("§a█"); // Completed
            } else if (i == currentStep) {
                bar.append("§e█"); // Current
            } else {
                bar.append("§8█"); // Not started
            }
        }
        bar.append("§7] §f").append(message);

        player.sendActionBar(Component.text(bar.toString()));
    }

    private World createVoidWorld(String worldName) {
        final WorldCreator worldCreator = new WorldCreator(worldName)
                .generator(new VoidGenerator())
                .environment(World.Environment.NORMAL)
                .keepSpawnLoaded(TriState.FALSE); // prevent spawn chunk loading which causes hang
        final World gameWorld = Bukkit.createWorld(worldCreator);

        if (gameWorld != null) {
            // Set spawn on main thread after world is created
            Bukkit.getScheduler().runTask(plugin, () -> {
                gameWorld.setSpawnLocation(0, 64, 0);
                gameWorld.setAutoSave(false);
            });
        }

        return gameWorld;
    }

    private void pasteLobbySchematic(World world, Location location) {
        String schematicName = "rush_lobby.schem";
        File schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        File schematicFile = new File(schematicsFolder, schematicName);

        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Lobby schematic not found: " + schematicFile.getPath());
            return;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            plugin.getLogger().warning("Unknown schematic format for: " + schematicName);
            return;
        }

        try (FileInputStream fis = new FileInputStream(schematicFile);
                ClipboardReader reader = format.getReader(fis)) {

            Clipboard clipboard = reader.read();

            // Must run paste on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                    BlockVector3 to = BlockVector3.at(location.getX(), location.getY(), location.getZ());

                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(to)
                            .ignoreAirBlocks(false)
                            .build();

                    Operations.complete(operation);
                    plugin.getLogger().info("Pasted lobby schematic at " + location);
                } catch (WorldEditException e) {
                    plugin.getLogger().severe("Error pasting lobby schematic: " + e.getMessage());
                }
            });

        } catch (IOException e) {
            plugin.getLogger().severe("Error reading lobby schematic: " + e.getMessage());
        }
    }

    private void finalizeGameRoomCreation(Player host, GameRoom room, GameRoom.IslandType islandType,
            GameRoom.TeamSize teamSize) {
        room.setConfig(new GameRoomConfig(islandType, islandType.getCount(), teamSize, MapType.NORMAL, false, false));
        gameRooms.put(room.getId(), room);
        playerGameRoomMap.put(host, room);

        host.teleport(room.getLobbyLocation());
        host.getInventory().clear();
        host.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());

        host.sendMessage(Component.text("§aPartie créée avec succès!"));
        host.sendMessage(Component.text("§7Type: " + islandType.getDisplayName()));
        host.sendMessage(Component.text("§7Équipes: " + teamSize.getDisplayName()));
    }

    /**
     * Adds a player to a game room and teleports them.
     */
    public void joinGameRoom(Player player, GameRoom room) {
        if (room.isFull()) {
            player.sendMessage(Component.text("§cCette partie est pleine!"));
            return;
        }

        if (!room.isWaiting()) {
            player.sendMessage(Component.text("§cCette partie a déjà commencé!"));
            return;
        }

        playerGameRoomMap.put(player, room);
        player.teleport(room.getLobbyLocation());
        player.getInventory().clear();
        player.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());

        player.sendMessage(Component.text("§aVous avez rejoint la partie de §f" + room.getHostName()));
    }

    public void addPlayerToGameRoom(Player player, GameRoom room) {
        playerGameRoomMap.put(player, room);
    }

    public void removePlayerFromGameRoom(Player player) {
        playerGameRoomMap.remove(player);
    }

    public GameRoom getGameRoomOfPlayer(Player player) {
        return playerGameRoomMap.get(player);
    }

    public GameRoom getGameRoom(String id) {
        return gameRooms.get(id);
    }

    public List<GameRoom> getAllGameRooms() {
        return new ArrayList<>(gameRooms.values());
    }

    public void removeGameRoom(String id) {
        final GameRoom room = gameRooms.remove(id);

        if (room != null) {
            playerGameRoomMap.values().remove(room);

            final World world = room.getWorld();

            if (world != null) {
                // teleport all players out of the world first
                Location fallback = plugin.getMainLobby();

                if (fallback == null || fallback.getWorld() == null) {
                    fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
                }

                for (Player player : new ArrayList<>(world.getPlayers())) {
                    player.teleport(fallback);
                    player.getInventory().clear();
                    player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                }

                // stop the game if running
                if (room.getGame() != null) {
                    room.getGame().stop();
                }

                // unload the world without saving (since it was autoSave=false)
                final boolean unloaded = Bukkit.unloadWorld(world, false);

                if (!unloaded) {
                    plugin.getLogger().warning("Failed to unload world: " + world.getName());
                } else {
                    plugin.getLogger().info("Unloaded world: " + world.getName());
                }

                // delete world folder asynchronously to avoid blocking the main thread
                final File worldFolder = new File(Bukkit.getWorldContainer(), world.getName());

                if (worldFolder.exists()) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        deleteDirectory(worldFolder);
                        plugin.getLogger().info("Deleted world folder: " + worldFolder.getAbsolutePath());
                    });
                }
            }
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectory(File directory) {
        if (!directory.exists()) {
            return;
        }

        final File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }

        directory.delete();
    }

    private GameRoom.IslandType selectedIslandType = GameRoom.IslandType.FOUR_ISLANDS;
    private GameRoom.TeamSize selectedTeamSize = GameRoom.TeamSize.VS4;

    /**
     * Opens the game listing GUI for a player.
     */
    public void openGameList(Player player) {
        final List<GameRoom> rooms = getAllGameRooms();

        final int rows = Math.max(3, (rooms.size() / 9) + 2);
        final GUI gui = new GUI("§8Liste des parties", rows);

        int slot = 0;

        for (GameRoom room : rooms) {
            final GameRoom targetRoom = room;

            gui.addItem(slot, createGameRoomItem(room), p -> joinGameRoom(p, targetRoom));
            slot++;

            if (slot >= (rows * 9) - 9) {
                break;
            }
        }

        int createSlot = (rows * 9) - 1; // create game button (bottom right)

        gui.addItem(createSlot, createGameCreationItem(), this::openGameCreation);

        gui.openGUI(player);
    }

    private ItemStack createGameRoomItem(GameRoom room) {
        Material material;
        String status;

        if (room.isRunning()) {
            material = Material.GREEN_WOOL;
            status = "§aEn cours";
        } else if (room.isWaiting()) {
            material = Material.YELLOW_WOOL;
            status = "§eEn attente";
        } else {
            material = Material.RED_WOOL;
            status = "§cTerminée";
        }

        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(room.getDisplayName()));
        item.setItemMeta(meta);

        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Hôte: §f" + room.getHostName()),
                Component.text("§7Status: " + status),
                Component.text("§7Joueurs: §f" + room.getPlayerCount() + "/" + room.getMaxPlayers()),
                Component.empty(),
                Component.text(room.isFull() ? "§cPartie pleine" : "§aClic pour rejoindre"))));

        return item;
    }

    private ItemStack createGameCreationItem() {
        final ItemStack item = new ItemStack(Material.NETHER_STAR);
        final ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("§6§lCréer une partie"));
        item.setItemMeta(meta);

        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Clic pour créer une nouvelle partie"),
                Component.empty(),
                Component.text("§7Configurez le nombre d'îles et"),
                Component.text("§7la taille des équipes."))));

        return item;
    }

    /**
     * Creates the compass item for joining games.
     */
    public ItemStack createCompassItem() {
        final ItemStack compass = new ItemStack(Material.COMPASS);
        final ItemMeta meta = compass.getItemMeta();

        meta.displayName(Component.text("§f§lRejoindre une partie"));
        compass.setItemMeta(meta);

        compass.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Clic droit pour voir les parties disponibles"))));

        return compass;
    }

    /**
     * Opens the game creation GUI for a player.
     */
    public void openGameCreation(Player player) {
        final GUI gui = new GUI("§8Créer une partie", 3);

        // Island type selection (row 1)
        gui.addItem(10, createIslandTypeItem(GameRoom.IslandType.FOUR_ISLANDS,
                selectedIslandType == GameRoom.IslandType.FOUR_ISLANDS),
                p -> selectIslandType(p, GameRoom.IslandType.FOUR_ISLANDS));

        gui.addItem(12, createIslandTypeItem(GameRoom.IslandType.EIGHT_ISLANDS,
                selectedIslandType == GameRoom.IslandType.EIGHT_ISLANDS),
                p -> selectIslandType(p, GameRoom.IslandType.EIGHT_ISLANDS));

        // Team size selection (row 2)
        gui.addItem(14, createTeamSizeItem(GameRoom.TeamSize.VS2,
                selectedTeamSize == GameRoom.TeamSize.VS2),
                p -> selectTeamSize(p, GameRoom.TeamSize.VS2));

        gui.addItem(15, createTeamSizeItem(GameRoom.TeamSize.VS3,
                selectedTeamSize == GameRoom.TeamSize.VS3),
                p -> selectTeamSize(p, GameRoom.TeamSize.VS3));

        gui.addItem(16, createTeamSizeItem(GameRoom.TeamSize.VS4,
                selectedTeamSize == GameRoom.TeamSize.VS4),
                p -> selectTeamSize(p, GameRoom.TeamSize.VS4));

        // Create button (bottom center)
        gui.addItem(22, createConfirmItem(), this::createGameFromGUI);

        // Back button (bottom left)
        gui.addItem(18, createBackItem(), this::openGameList);

        gui.openGUI(player);
    }

    private ItemStack createIslandTypeItem(GameRoom.IslandType type, boolean selected) {
        Material material = type == GameRoom.IslandType.FOUR_ISLANDS ? Material.GRASS_BLOCK : Material.STONE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = selected ? "§a" + type.getDisplayName() + " §7(§aSélectionné§7)" : "§7" + type.getDisplayName();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);

        String status = type == GameRoom.IslandType.EIGHT_ISLANDS ? "§cNon disponible" : "§aDisponible";

        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Îles: §f" + type.getCount()),
                Component.empty(),
                Component.text(status),
                Component.empty(),
                Component.text("§eClic pour sélectionner"))));

        return item;
    }

    private ItemStack createTeamSizeItem(GameRoom.TeamSize size, boolean selected) {
        Material material = switch (size) {
            case VS2 -> Material.LEATHER_HELMET;
            case VS3 -> Material.CHAINMAIL_HELMET;
            case VS4 -> Material.IRON_HELMET;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = selected ? "§a" + size.getDisplayName() + " §7(§aSélectionné§7)" : "§7" + size.getDisplayName();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);

        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Joueurs par équipe: §f" + size.getPlayersPerTeam()),
                Component.empty(),
                Component.text("§eClic pour sélectionner"))));

        return item;
    }

    private ItemStack createConfirmItem() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("§a§lCréer la partie"));
        item.setItemMeta(meta);

        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Créez une partie avec:"),
                Component.text("§7- " + selectedIslandType.getDisplayName()),
                Component.text("§7- " + selectedTeamSize.getDisplayName()),
                Component.empty(),
                Component.text("§aClic pour créer"))));

        return item;
    }

    private ItemStack createBackItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("§cRetour"));
        item.setItemMeta(meta);

        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Retour à la liste des parties"))));

        return item;
    }

    private void selectIslandType(Player player, GameRoom.IslandType type) {
        if (type == GameRoom.IslandType.EIGHT_ISLANDS) {
            player.sendMessage(Component.text("§cLes parties à 8 îles ne sont pas encore disponibles!"));
            return;
        }
        selectedIslandType = type;
        openGameCreation(player);
    }

    private void selectTeamSize(Player player, GameRoom.TeamSize size) {
        selectedTeamSize = size;
        openGameCreation(player);
    }

    private void createGameFromGUI(Player player) {
        if (selectedIslandType == GameRoom.IslandType.EIGHT_ISLANDS) {
            player.sendMessage(Component.text("§cLes parties à 8 îles ne sont pas encore disponibles!"));
            return;
        }

        player.closeInventory();
        player.sendMessage(Component.text("§aCréation de la partie en cours..."));
        createGameRoom(player, selectedIslandType, selectedTeamSize);
    }

    // Legacy methods for backward compatibility
    public Game createGame(String name) {
        if (legacyGames.containsKey(name)) {
            return null;
        }
        Game game = new Game(name);
        legacyGames.put(name, game);
        return game;
    }

    public Game getGame(String name) {
        return legacyGames.get(name);
    }

    public void addPlayerToGame(Player player, Game game) {
        playerGameMap.put(player, game);
    }

    public void removePlayerFromGame(Player player) {
        playerGameMap.remove(player);
    }

    public Game getGameOfPlayer(Player player) {
        return playerGameMap.get(player);
    }

    public Collection<Game> getGames() {
        return legacyGames.values();
    }

    public void removeGame(String name) {
        legacyGames.remove(name);
    }

    public Game getCurrentGame() {
        for (Game game : legacyGames.values()) {
            if (game.getState() == GameState.RUNNING || game.getState() == GameState.WAITING) {
                return game;
            }
        }
        return null;
    }

    /**
     * Loads island schematics for a game room.
     */
    public void loadIslandsForGameRoom(GameRoom room) {
        if (room.isIslandsLoaded()) {
            return;
        }

        String schematicName = plugin.getConfig().getString("schematicFilename");
        File schematicFile = new File(plugin.getDataFolder().getParentFile(), "WorldEdit/schematics/" + schematicName);

        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Schematic not found: " + schematicFile.getPath());
            return;
        }

        ClipboardFormat format = ClipboardFormats.findByPath(schematicFile.toPath());
        if (format == null) {
            plugin.getLogger().warning("Unknown schematic format for: " + schematicName);
            return;
        }

        int islandIndex = 0;
        for (io.github.rush.objects.Island island : room.getIslands()) {
            pasteIslandSchematic(room.getWorld(), island, schematicFile, format, room.getIslandY());
            spawnMerchantsForIsland(room, island, islandIndex);
            islandIndex++;
        }

        room.setIslandsLoaded(true);
    }

    private void pasteIslandSchematic(World world, io.github.rush.objects.Island island, File schematicFile,
            ClipboardFormat format, int islandY) {
        try (FileInputStream fis = new FileInputStream(schematicFile);
                ClipboardReader reader = format.getReader(fis)) {

            Clipboard clipboard = reader.read();
            BlockVector3 dimensions = clipboard.getDimensions();

            // Load chunks
            int minX = island.getX();
            int maxX = island.getX() + dimensions.x();
            int minZ = island.getZ();
            int maxZ = island.getZ() + dimensions.z();

            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                    world.getChunkAt(cx, cz).load(true);
                }
            }

            // Paste schematic
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);

                if (island.getRotation() != 0) {
                    AffineTransform transform = new AffineTransform().rotateY(island.getRotation());
                    holder.setTransform(holder.getTransform().combine(transform));
                }

                Operation operation = holder
                        .createPaste(editSession)
                        .to(BlockVector3.at(island.getX(), islandY, island.getZ()))
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
            }

            plugin.getLogger()
                    .info("Pasted island schematic at (" + island.getX() + ", " + islandY + ", " + island.getZ() + ")");

        } catch (IOException | WorldEditException e) {
            plugin.getLogger().severe("Failed to paste island schematic: " + e.getMessage());
        }
    }

    private void spawnMerchantsForIsland(GameRoom room, io.github.rush.objects.Island island, int islandIndex) {
        // Direction vectors pointing toward center (0,0)
        int[][] directions = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        float[] yawValues = { -90f, 90f, 0f, 180f };
        List<Integer> spread = List.of(5, 7);

        int speedOffset = plugin.getConfig().getInt("villagerSpeedOffset", 10);
        int regularOffset = plugin.getConfig().getInt("villagerRegularOffset", speedOffset - 1);

        int[] dir = directions[islandIndex];
        int perpX = dir[1];
        int perpZ = -dir[0];
        float facingYaw = yawValues[islandIndex];

        // Spawn speed villagers (2)
        for (int i = 0; i < 2; i++) {
            int sign = (i == 0) ? 1 : -1;
            int speedX = island.getX() + (dir[0] * speedOffset) + (perpX * sign * spread.get(0));
            int speedZ = island.getZ() + (dir[1] * speedOffset) + (perpZ * sign * spread.get(0));

            Location speedLoc = new Location(room.getWorld(), speedX + 0.5, room.getIslandY() + 0.5, speedZ + 0.5,
                    facingYaw, 0);
            spawnMerchant(room.getWorld(), speedLoc, io.github.rush.entities.MerchantType.SPEED);
        }

        // Spawn regular villagers (4)
        io.github.rush.entities.MerchantType[] regularTypes = io.github.rush.entities.MerchantType.firstN(4);
        for (int i = 0; i < 4; i++) {
            int sign = (i < 2) ? 1 : -1;
            int spreadIdx = (i % 2 == 0) ? 0 : 1;
            int regX = island.getX() + (dir[0] * regularOffset) + (perpX * spread.get(spreadIdx) * sign);
            int regZ = island.getZ() + (dir[1] * regularOffset) + (perpZ * spread.get(spreadIdx) * sign);

            Location villagerLoc = new Location(room.getWorld(), regX + 0.5, room.getIslandY() + 1, regZ + 0.5,
                    facingYaw, 0);
            spawnMerchant(room.getWorld(), villagerLoc, regularTypes[i]);
        }
    }

    private void spawnMerchant(World world, Location location, io.github.rush.entities.MerchantType type) {
        org.bukkit.entity.Villager villager = world.spawn(location, org.bukkit.entity.Villager.class);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setSilent(true);

        if (type == io.github.rush.entities.MerchantType.SPEED) {
            villager.setBaby();
        }

        io.github.rush.entities.Merchant.apply(villager, type);
    }

    /**
     * Called when a GameRoom's game starts.
     */
    public void onGameRoomStarted(GameRoom room) {
        plugin.getLogger().info("Game started in room: " + room.getId());
        // TODO: Additional logic when game starts (statistics, notifications, etc.)
    }

    /**
     * Called when a GameRoom's game ends.
     */
    public void onGameRoomEnded(GameRoom room) {
        plugin.getLogger().info("Game ended in room: " + room.getId());

        // Teleport all players back to main lobby
        Location mainLobby = plugin.getMainLobby();
        for (org.bukkit.entity.Entity entity : room.getGame().getPlayers()) {
            if (entity instanceof Player player) {
                player.teleport(mainLobby);
                player.getInventory().clear();
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
        }

        // Schedule world cleanup
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeGameRoom(room.getId());
        }, 100L); // 5 seconds delay
    }
}
