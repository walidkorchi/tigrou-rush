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
import io.github.rush.menus.HostConfigGUI;
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
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.rush.replay.ReplayFile;
import io.github.rush.replay.ReplayHeader;
import io.github.rush.replay.ReplayPlayback;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages multiple game rooms and all game-related operations.
 */
public class GameManager {

    private final Main plugin;
    private final Map<String, GameRoom> gameRooms = new HashMap<>();
    private final Map<Player, GameRoom> playerGameRoomMap = new HashMap<>();
    private final Map<String, Game> legacyGames = new HashMap<>();
    private final Map<Player, Game> playerGameMap = new HashMap<>();
    private final Map<UUID, GameRoomConfig.Builder> pendingConfigs = new HashMap<>();
    private final Map<UUID, ReconnectData> reconnectDataMap = new HashMap<>();

    private int worldCounter = 0;

    /**
     * Snapshot of a player's in-game state at the moment of disconnect.
     * Stored by UUID so the same player can be identified on reconnect.
     *
     * @param roomId        ID of the GameRoom the player was in
     * @param teamColorName Color name of the player's team, or null if spectator /
     *                      free
     * @param wasSpectator  True if the player was a spectator at disconnect time
     */
    public record ReconnectData(String roomId, String teamColorName, boolean wasSpectator) {
    }

    public GameManager(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a new game room with its own world using the async pipeline.
     * Room is registered immediately in CREATING state, transitions to WAITING
     * once schematics are pasted and the host is confirmed still online.
     */
    public void createGameRoom(Player host, GameRoomConfig config) {
        final UUID hostUUID = host.getUniqueId();
        final String worldName = "rush_game_" + (++worldCounter) + "_" + host.getName();

        final AtomicReference<Component> currentBar =
                new AtomicReference<>(buildLoadingBarComponent("Création du monde", 0, 5));
        final BukkitTask[] barTask = { null };
        barTask[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player h = Bukkit.getPlayer(hostUUID);
            if (h != null)
                h.sendActionBar(currentBar.get());
        }, 0L, 3L);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Step 1: Create void world (main thread)
                World gameWorld = createVoidWorld(worldName);
                if (gameWorld == null) {
                    barTask[0].cancel();
                    host.sendMessage(Component.text("§cErreur lors de la création du monde!"));
                    return;
                }

                // Step 2: Register room in CREATING state immediately so host-disconnect
                // cleanup can find it even if the host goes offline during paste.
                currentBar.set(buildLoadingBarComponent("Initialisation", 1, 5));
                Location lobbyLocation = new Location(gameWorld, 0, 64, 0);
                GameRoom room = new GameRoom(host.getName(), hostUUID, gameWorld, config, lobbyLocation);
                room.getGame().setState(GameState.CREATING);
                gameRooms.put(room.getId(), room);

                // Steps 3-5: Read and paste schematics fully on async thread —
                // keeps the main thread free so the bar-refresh task fires on time.
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    currentBar.set(buildLoadingBarComponent("Génération du lobby d'attente", 2, 5));
                    Clipboard waitingRoomClip = readSchematic("waiting_room.schem");

                    currentBar.set(buildLoadingBarComponent("Génération des îles", 3, 5));
                    Clipboard islandClip = readSchematic(config.mapType().schematicName());

                    currentBar.set(buildLoadingBarComponent("Construction du monde", 4, 5));

                    // Steps 5-6: Paste schematics and finalize on main thread —
                    // EditSession.close() triggers block onPlace which requires main thread.
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (waitingRoomClip != null) {
                            pasteClipboard(gameWorld, waitingRoomClip, BlockVector3.at(0, 64, 0), 0);
                        }

                        if (islandClip != null) {
                            List<io.github.rush.objects.Island> islands = room.getIslands();
                            for (io.github.rush.objects.Island isl : islands) {
                                pasteClipboard(gameWorld, islandClip,
                                        BlockVector3.at(isl.getX(), room.getIslandY(), isl.getZ()),
                                        isl.getRotation());
                            }
                        }

                        List<io.github.rush.objects.Island> islands = room.getIslands();
                        for (int i = 0; i < islands.size(); i++) {
                            spawnMerchantsForIsland(room, islands.get(i), i);
                        }
                        room.setIslandsLoaded(true);

                        // Step 7: Check host still online — cancel if gone
                        Player onlineHost = Bukkit.getPlayer(hostUUID);
                        if (onlineHost == null) {
                            barTask[0].cancel();
                            cancelRoomCreation(room);
                            return;
                        }

                        // Step 8: Transition to WAITING and hand off to host
                        room.getGame().setState(GameState.WAITING);
                        playerGameRoomMap.put(onlineHost, room);

                        onlineHost.teleport(lobbyLocation);
                        onlineHost.getInventory().clear();
                        onlineHost.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());
                        onlineHost.getInventory().setItem(8, createHostPanelItem());

                        barTask[0].cancel();
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            Player h = Bukkit.getPlayer(hostUUID);
                            if (h != null)
                                h.sendActionBar(Component.empty());
                        }, 40L);
                    });
                });

            } catch (Exception e) {
                barTask[0].cancel();
                plugin.getLogger().severe("Error creating game world: " + e.getMessage());
                e.printStackTrace();
                host.sendMessage(Component.text("§cErreur lors de la création de la partie!"));
            }
        });
    }

    public void cancelRoomCreation(GameRoom room) {
        gameRooms.remove(room.getId());
        World world = room.getWorld();
        if (world != null) {
            Bukkit.unloadWorld(world, false);
            File worldFolder = new File(Bukkit.getWorldContainer(), world.getName());
            if (worldFolder.exists()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteDirectory(worldFolder));
            }
        }
    }

    private Clipboard readSchematic(String filename) {
        File schematicFile = new File(plugin.getDataFolder().getParentFile(), "WorldEdit/schematics/" + filename);
        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Schematic not found: " + schematicFile.getPath());
            return null;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            plugin.getLogger().warning("Unknown schematic format: " + filename);
            return null;
        }
        try (FileInputStream fis = new FileInputStream(schematicFile);
                ClipboardReader reader = format.getReader(fis)) {
            return reader.read();
        } catch (IOException e) {
            plugin.getLogger().severe("Error reading schematic " + filename + ": " + e.getMessage());
            return null;
        }
    }

    private void pasteClipboard(World world, Clipboard clipboard, BlockVector3 target, int rotation) {
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            if (rotation != 0) {
                AffineTransform transform = new AffineTransform().rotateY(rotation);
                holder.setTransform(holder.getTransform().combine(transform));
            }
            Operation operation = holder.createPaste(editSession).to(target).ignoreAirBlocks(false).build();
            Operations.complete(operation);
            plugin.getLogger().info("Pasted schematic at " + target);
        } catch (WorldEditException e) {
            plugin.getLogger().severe("Error pasting schematic: " + e.getMessage());
        }
    }

    public ItemStack createHostPanelItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§6§lPanneau de l'Hôte"));
        item.setItemMeta(meta);
        return item;
    }

    private Component buildLoadingBarComponent(String message, int currentStep, int totalSteps) {
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < totalSteps; i++) {
            if (i < currentStep) {
                bar.append("§a█");
            } else if (i == currentStep) {
                bar.append("§e█");
            } else {
                bar.append("§8█");
            }
        }
        bar.append("§7] §f").append(message);
        return Component.text(bar.toString());
    }

    private World createVoidWorld(String worldName) {
        final WorldCreator worldCreator = new WorldCreator(worldName)
                .generator(new VoidGenerator())
                .environment(World.Environment.NORMAL);
        final World gameWorld = Bukkit.createWorld(worldCreator);

        if (gameWorld != null) {
            // Set spawn on main thread after world is created
            Bukkit.getScheduler().runTask(plugin, () -> {
                gameWorld.setSpawnLocation(0, 64, 0);
                gameWorld.setAutoSave(false);
                gameWorld.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                gameWorld.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                gameWorld.setGameRule(org.bukkit.GameRule.DO_INSOMNIA, false);
            });
        }

        return gameWorld;
    }

    /**
     * Adds a player to a game room and teleports them.
     */
    public void joinGameRoom(Player player, GameRoom room) {
        if (room.isRunning()) {
            playerGameRoomMap.put(player, room);
            room.getGame().addObserver(player);
            return;
        }

        if (!room.isWaiting()) {
            player.sendMessage(Component.text("§cCette partie n'est plus disponible."));
            return;
        }

        if (room.isFull()) {
            player.sendMessage(Component.text("§cCette partie est pleine!"));
            return;
        }

        playerGameRoomMap.put(player, room);
        room.getJoinOrder().add(player.getUniqueId());
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

    public Game getGameForPlayer(Player player) {
        GameRoom room = getGameRoomOfPlayer(player);
        if (room != null)
            return room.getGame();
        GameRoom worldRoom = getGameRoomByWorld(player.getWorld().getName());
        if (worldRoom != null)
            return worldRoom.getGame();
        return getCurrentGame();
    }

    public GameRoom getGameRoomByWorld(String worldName) {
        for (GameRoom room : gameRooms.values()) {
            if (room.getWorld() != null && room.getWorld().getName().equals(worldName)) {
                return room;
            }
        }
        return null;
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
                    player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                    restoreHubInventory(player);
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

    /**
     * Opens the game listing GUI for a player. Shows WAITING and RUNNING rooms.
     */
    public void openGameList(Player player) {
        final boolean isAdmin = player.isOp();
        final List<GameRoom> rooms = getAllGameRooms().stream()
                .filter(r -> r.isWaiting() || r.isRunning())
                .toList();
        final List<ReplayHeader> replays = Main.getInstance().getReplayStorage().listReplays();

        final int totalItems = rooms.size() + replays.size();
        final int rows = Math.min(6, Math.max(3, (totalItems / 9) + 2));
        final GUI gui = new GUI("§8Liste des parties", rows);

        int slot = 0;
        for (GameRoom room : rooms) {
            if (slot >= rows * 9)
                break;
            final GameRoom targetRoom = room;
            if (isAdmin) {
                gui.addItem(slot, createGameRoomItem(room, true),
                        p -> joinGameRoom(p, targetRoom),
                        p -> openDeleteConfirmation(p, targetRoom));
            } else {
                gui.addItem(slot, createGameRoomItem(room, false), p -> joinGameRoom(p, targetRoom));
            }
            slot++;
        }

        for (ReplayHeader replay : replays) {
            if (slot >= rows * 9)
                break;
            final ReplayHeader targetReplay = replay;
            gui.addItem(slot, createReplayItem(replay),
                    p -> Main.getInstance().getReplayManager().joinReplay(p, targetReplay));
            slot++;
        }

        gui.openGUI(player);
    }

    private ItemStack createGameRoomItem(GameRoom room, boolean isAdmin) {
        final Material material;
        final String status;
        final String actionLine;

        if (room.isRunning()) {
            material = Material.GREEN_WOOL;
            status = "§aEn cours";
            actionLine = "§aClic pour regarder en spectateur";
        } else if (room.isFull()) {
            material = Material.RED_WOOL;
            status = "§eEn attente";
            actionLine = "§cPartie pleine";
        } else {
            material = Material.YELLOW_WOOL;
            status = "§eEn attente";
            actionLine = "§aClic pour rejoindre";
        }

        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(room.getDisplayName()));
        item.setItemMeta(meta);

        final List<Component> lore = new ArrayList<>(List.of(
                Component.text("§7Hôte: §f" + room.getHostName()),
                Component.text("§7Status: " + status),
                Component.text("§7Carte: §f" + room.getConfig().mapType().displayName()),
                Component.text("§7Joueurs: §f" + room.getPlayerCount() + "/" + room.getMaxPlayers()),
                Component.empty(),
                Component.text(actionLine)));

        if (isAdmin) {
            lore.add(Component.empty());
            lore.add(Component.text("§c§lClic droit §7pour supprimer cette partie"));
        }

        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));

        return item;
    }

    private ItemStack createReplayItem(ReplayHeader header) {
        final ItemStack item = new ItemStack(Material.ORANGE_WOOL);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§6§lArchive §7— §f" + header.hostName()));
        item.setItemMeta(meta);

        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(header.startTimestamp()), ZoneId.systemDefault());
        String date = String.format("%02d/%02d/%04d %02d:%02d",
                dt.getDayOfMonth(), dt.getMonthValue(), dt.getYear(),
                dt.getHour(), dt.getMinute());

        long totalSeconds = header.durationMs() / 1000;
        String duration = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);

        String winner = header.winnerTeamColorName() != null ? header.winnerTeamColorName() : "Aucun";

        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Hôte: §f" + header.hostName()),
                Component.text("§7Date: §f" + date),
                Component.text("§7Durée: §f" + duration),
                Component.text("§7Vainqueur: §f" + winner),
                Component.text("§7Joueurs: §f" + header.participantNames().size()),
                Component.empty(),
                Component.text("§eClic pour regarder"))));

        return item;
    }

    public void openDeleteConfirmation(Player admin, GameRoom room) {
        final GUI gui = new GUI("§8Supprimer la partie?", 3);

        gui.addItem(13, createGameRoomItem(room, false));

        final ItemStack confirmItem = new ItemStack(Material.BARRIER);
        final ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.displayName(Component.text("§c§l⚠ CONFIRMER LA SUPPRESSION"));
        confirmItem.setItemMeta(confirmMeta);
        confirmItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Tous les joueurs seront expulsés."),
                Component.text("§7La partie sera définitivement supprimée."))));
        gui.addItem(11, confirmItem, p -> {
            p.closeInventory();
            if (getGameRoom(room.getId()) == null) {
                p.sendMessage(Component.text("§cCette partie n'existe plus."));
                return;
            }
            for (Player roomPlayer : new ArrayList<>(room.getWorld().getPlayers())) {
                roomPlayer.sendMessage(Component.text("§cCette partie a été supprimée par un administrateur."));
            }
            removeGameRoom(room.getId());
            p.sendMessage(Component.text("§aLa partie §f" + room.getHostName() + "§a a été supprimée."));
        });

        final ItemStack cancelItem = new ItemStack(Material.LIME_CONCRETE);
        final ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.displayName(Component.text("§a§lANNULER"));
        cancelItem.setItemMeta(cancelMeta);
        gui.addItem(15, cancelItem, p -> {
            p.closeInventory();
            openGameList(p);
        });

        gui.openGUI(admin);
    }

    public void restoreHubInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, createCompassItem());
        player.getInventory().setItem(7, createGameHostItem());
        final ItemStack settings = new ItemStack(Material.REPEATER);
        final ItemMeta settingsMeta = settings.getItemMeta();
        settingsMeta.displayName(Component.text("§f§lParamètres"));
        settings.setItemMeta(settingsMeta);
        player.getInventory().setItem(8, settings);
    }

    public void recordDisconnect(UUID uuid, ReconnectData data) {
        reconnectDataMap.put(uuid, data);
    }

    public ReconnectData consumeReconnectData(UUID uuid) {
        return reconnectDataMap.remove(uuid);
    }

    public void handleReconnect(Player player, GameRoom room, ReconnectData data) {
        if (!player.isOnline() || !room.isRunning()) {
            // Game ended before the reconnect task ran — send to hub
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            Location fallback = plugin.getMainLobby();
            if (fallback == null || fallback.getWorld() == null) {
                fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
            }
            player.teleport(fallback);
            restoreHubInventory(player);
            return;
        }

        Game game = room.getGame();

        if (data.wasSpectator()) {
            game.addObserver(player);
            return;
        }

        if (data.teamColorName() != null) {
            Team team = game.getTeam(data.teamColorName());

            if (team != null && !team.isBedDestroyed()) {
                // Re-add to team and apply respawn behaviour (same as normal death)
                team.addPlayer(player);
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                player.setFoodLevel(20);
                player.setSaturation(20f);
                player.getInventory().clear();
                game.equipEntity(player, team);

                Location bedLoc = team.getBedLocation();
                Location respawn = bedLoc != null
                        ? new Location(bedLoc.getWorld(), bedLoc.getX() + 0.5, bedLoc.getY() + 1, bedLoc.getZ() + 0.5)
                        : team.getSpawnLocation();
                if (respawn != null) {
                    player.teleport(respawn);
                }

                game.addProtection(player);
                player.sendMessage(Component.text("§aVous avez été reconnecté à votre équipe."));
            } else {
                // Bed was destroyed while offline — eliminated as spectator
                game.addSpectator(player);
            }
        } else {
            // Was a free player at disconnect time — rejoin as observer
            game.addObserver(player);
        }
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
     * Creates the game_host item given to every player on join.
     * Placeholder for tland:game_host CraftEngine item.
     */
    public ItemStack createGameHostItem() {
        final ItemStack item = new ItemStack(Material.BEACON);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§6§lCréer une partie"));
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Clic droit pour configurer"),
                Component.text("§7et créer votre partie."))));
        return item;
    }

    /**
     * Opens the HostConfigGUI for a player, creating a fresh builder if needed.
     */
    public void openHostConfigGUI(Player player) {
        GameRoomConfig.Builder builder = pendingConfigs.computeIfAbsent(
                player.getUniqueId(), id -> GameRoomConfig.builder());
        HostConfigGUI.open(player, builder, this);
    }

    public void removePendingConfig(Player player) {
        pendingConfigs.remove(player.getUniqueId());
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

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
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
        // Direction vectors pointing outward from center (0,0): N→-z, E→+x, S→+z, W→-x
        int[][] directions = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
        // Yaw values facing inward (toward center): N→South=0°, E→West=90°,
        // S→North=180°, W→East=-90°
        float[] yawValues = { 0f, 90f, 180f, -90f };
        List<Integer> spread = List.of(5, 7);

        // speedOffset matches Team.placeEnderChests: ender chests are at outward 12
        // (speedOffset-1),
        // so speed merchants sit directly 1 block behind each ender chest at outward
        // 13.
        int speedOffset = plugin.getConfig().getInt("villagerSpeedOffset", 13);
        int regularOffset = plugin.getConfig().getInt("villagerRegularOffset", speedOffset - 1);

        int[] dir = directions[islandIndex];
        int perpX = dir[1];
        int perpZ = -dir[0];
        float facingYaw = yawValues[islandIndex];

        // Spawn speed villagers (2) — one directly behind each ender chest
        // (perpendicular ±1)
        for (int i = 0; i < 2; i++) {
            int sign = (i == 0) ? 1 : -1;
            int speedX = island.getX() + (dir[0] * speedOffset) + (perpX * sign);
            int speedZ = island.getZ() + (dir[1] * speedOffset) + (perpZ * sign);

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
            villager.setAgeLock(true);
        }

        io.github.rush.entities.Merchant.apply(villager, type);

        if (type.getDisplayItem() != null) {
            float yaw = location.getYaw();
            double rad = Math.toRadians(yaw);
            // 2 blocks toward center from the merchant's block position.
            // Integer block coords avoid the double +0.5 offset from the spawn Location.
            // Yaw in the spawn Location drives ItemFrame facing — setFacingDirection is
            // intentionally avoided because it repositions the entity onto a block face.
            int dx = (int) Math.round(-Math.sin(rad));
            int dz = (int) Math.round(Math.cos(rad));
            int frameX = location.getBlockX() + dx * 2;
            int frameZ = location.getBlockZ() + dz * 2;
            Location frameLoc = new Location(world, frameX + 0.5, location.getY(), frameZ + 0.5, yaw, 0);

            ItemFrame frame = world.spawn(frameLoc, ItemFrame.class);
            frame.setItem(new ItemStack(type.getDisplayItem()));
            frame.setInvulnerable(true);
            frame.setFixed(true);
            frame.setVisible(false);
        }
    }

    /**
     * Creates a void world for a replay session, pastes island schematics, then
     * calls onReady
     * with the resulting ReplayPlayback on the main thread. Must be called from the
     * main thread.
     */
    public void createReplayWorld(ReplayFile file, Consumer<ReplayPlayback> onReady) {
        String sessionId = file.header().sessionId();
        String worldName = "rush_replay_" + sessionId;

        MapType mapType = MapType.NORMAL;
        if (file.header().mapTypeName() != null) {
            try {
                mapType = MapType.valueOf(file.header().mapTypeName());
            } catch (IllegalArgumentException ignored) {
            }
        }
        final MapType resolvedMapType = mapType;

        World world = createVoidWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("Failed to create replay world for session: " + sessionId);
            return;
        }

        final World finalWorld = world;
        final int islandY = world.getMaxHeight() - 12;
        final int islandOffset = plugin.getConfig().getInt("islandOffset", 40);

        final List<io.github.rush.objects.Island> islands = List.of(
                new io.github.rush.objects.Island(-islandOffset, 0, -90),
                new io.github.rush.objects.Island(islandOffset, 0, 90),
                new io.github.rush.objects.Island(0, -islandOffset, 180),
                new io.github.rush.objects.Island(0, islandOffset, 0));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Clipboard islandClip = readSchematic(resolvedMapType.schematicName());

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (islandClip != null) {
                    for (io.github.rush.objects.Island island : islands) {
                        pasteClipboard(finalWorld, islandClip,
                                BlockVector3.at(island.getX(), islandY, island.getZ()),
                                island.getRotation());
                    }
                } else {
                    plugin.getLogger().warning("Replay world for session " + sessionId
                            + " loaded without islands — schematic '"
                            + resolvedMapType.schematicName() + "' not found.");
                }
                onReady.accept(new ReplayPlayback(file, finalWorld));
            });
        });
    }

    /**
     * Unloads and deletes a replay world created by createReplayWorld.
     */
    public void destroyReplayWorld(World world) {
        if (world == null)
            return;
        Bukkit.unloadWorld(world, false);
        File worldFolder = new File(Bukkit.getWorldContainer(), world.getName());
        if (worldFolder.exists()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> deleteDirectory(worldFolder));
        }
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

        final Location mainLobby = plugin.getMainLobby();

        for (org.bukkit.entity.Entity entity : room.getGame().getPlayers()) {
            if (entity instanceof Player player) {
                player.teleport(mainLobby);
                player.getInventory().clear();
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
        }

        for (Player spectator : room.getGame().getSpectators()) {
            spectator.setGameMode(org.bukkit.GameMode.ADVENTURE);
            spectator.teleport(mainLobby);
            spectator.getInventory().clear();
            spectator.sendMessage(Component.text("§7La partie est terminée. Vous avez été renvoyé au lobby."));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> removeGameRoom(room.getId()), 100L);
    }
}
