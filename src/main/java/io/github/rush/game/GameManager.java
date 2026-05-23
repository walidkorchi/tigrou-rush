package io.github.rush.game;

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
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.EnderChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.rush.entities.MerchantType;
import io.github.rush.replay.ReplayFile;
import io.github.rush.replay.ReplayHeader;
import io.github.rush.replay.ReplayPlayback;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
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

    private long gameEndMusicDurationMs = -1;

    public GameManager(Main plugin) {
        this.plugin = plugin;
    }

    private long getGameEndMusicDurationMs() {
        if (gameEndMusicDurationMs != -1) return gameEndMusicDurationMs;
        File mergeZip = new File(plugin.getDataFolder().getParentFile(),
                "CraftEngine/generated/sounds_merge.zip");
        long duration = readOggDurationFromZip(mergeZip, "music/global/gameendmusic.ogg");
        if (duration <= 0) {
            plugin.getLogger().warning("Could not read gameendmusic.ogg duration, using 27s default");
            duration = 27_000L;
        } else {
            plugin.getLogger().info("Detected gameendmusic.ogg duration: " + (duration / 1000) + "s");
        }
        gameEndMusicDurationMs = duration;
        return duration;
    }

    private long readOggDurationFromZip(File zipFile, String entryPath) {
        if (!zipFile.exists()) return -1;
        File temp = null;
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) return -1;
            temp = File.createTempFile("rush_ogg_", ".ogg");
            try (InputStream in = zip.getInputStream(entry);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
                in.transferTo(out);
            }
            AudioHeader header = AudioFileIO.read(temp).getAudioHeader();
            return (long) (header.getPreciseTrackLength() * 1000);
        } catch (Exception e) {
            return -1;
        } finally {
            if (temp != null) temp.delete();
        }
    }

    /**
     * Creates a new game room with its own world using the async pipeline.
     * Room is registered immediately in CREATING state, transitions to WAITING
     * once schematics are pasted and the host is confirmed still online.
     */
    public void createGameRoom(Player host, GameRoomConfig config) {
        final UUID hostUUID = host.getUniqueId();
        final String worldName = "rush_game_" + (++worldCounter) + "_" + host.getName();

        final AtomicReference<Component> currentBar = new AtomicReference<>(
                buildLoadingBarComponent("Création du monde", 0, 5));
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
                    host.sendMessage(Component.translatable("rush.room_create_failed"));
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
                host.sendMessage(Component.translatable("rush.room_create_failed"));
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
        File schematicFile = new File(plugin.getDataFolder().getParentFile(), "FastAsyncWorldEdit/schematics/" + filename);
        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Schematic not found: " + schematicFile.getPath());
            return null;
        }
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                plugin.getLogger().severe("Unsupported schematic format: " + schematicFile.getPath());
                return null;
            }
            return format.load(schematicFile);
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
        meta.displayName(Component.translatable("rush.host_panel_name"));
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
        @SuppressWarnings("removal")
        final List<GameRule<Boolean>> gameRules = List.of(
                GameRule.DO_DAYLIGHT_CYCLE,
                GameRule.DO_WEATHER_CYCLE,
                GameRule.DO_INSOMNIA);

        if (gameWorld != null) {
            // Set spawn on main thread after world is created
            Bukkit.getScheduler().runTask(plugin, () -> {
                gameWorld.setSpawnLocation(0, 64, 0);
                gameWorld.setAutoSave(false);
                for (GameRule<Boolean> gameRule : gameRules) {
                    gameWorld.setGameRule(gameRule, false);
                }
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
            player.sendMessage(Component.translatable("rush.room_not_available"));
            return;
        }

        if (room.isFull()) {
            player.sendMessage(Component.translatable("rush.room_full"));
            return;
        }

        playerGameRoomMap.put(player, room);
        room.getJoinOrder().add(player.getUniqueId());
        player.teleport(room.getLobbyLocation());
        player.getInventory().clear();
        player.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());

        player.sendMessage(Component.translatable("rush.room_joined", Component.text(room.getHostName())));
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
                    player.setGameMode(GameMode.ADVENTURE);
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
        final GUI gui = new GUI(Component.translatable("rush.room_list_title"), rows);

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
            status = "rush.room_status_running";
            actionLine = "rush.room_action_spectate";
        } else if (room.isFull()) {
            material = Material.RED_WOOL;
            status = "rush.room_status_waiting";
            actionLine = "rush.room_action_full";
        } else {
            material = Material.YELLOW_WOOL;
            status = "rush.room_status_waiting";
            actionLine = "rush.room_action_join";
        }

        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(room.getDisplayName());
        item.setItemMeta(meta);

        final List<Component> lore = new ArrayList<>(List.of(
                Component.translatable("rush.room_lore_host", Component.text(room.getHostName())),
                Component.translatable("rush.room_lore_status", Component.translatable(status)),
                Component.translatable("rush.room_lore_map", Component.text(room.getConfig().mapType().displayName())),
                Component.translatable("rush.room_lore_players",
                        Component.text(room.getPlayerCount()), Component.text(room.getMaxPlayers())),
                Component.empty(),
                Component.translatable(actionLine)));

        if (isAdmin) {
            lore.add(Component.empty());
            lore.add(Component.translatable("rush.room_admin_delete_hint"));
        }

        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));

        return item;
    }

    private ItemStack createReplayItem(ReplayHeader header) {
        final ItemStack item = new ItemStack(Material.ORANGE_WOOL);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.translatable("rush.replay_item_name", Component.text(header.hostName())));
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
                Component.translatable("rush.replay_item_host", Component.text(header.hostName())),
                Component.translatable("rush.replay_item_date", Component.text(date)),
                Component.translatable("rush.replay_item_duration", Component.text(duration)),
                Component.translatable("rush.replay_item_winner", Component.text(winner)),
                Component.translatable("rush.replay_item_players", Component.text(header.participantNames().size())),
                Component.empty(),
                Component.translatable("rush.replay_item_click"))));

        return item;
    }

    public void openDeleteConfirmation(Player admin, GameRoom room) {
        final GUI gui = new GUI(Component.translatable("rush.delete_confirm_title"), 3);

        gui.addItem(13, createGameRoomItem(room, false));

        final ItemStack confirmItem = new ItemStack(Material.BARRIER);
        final ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.displayName(Component.translatable("rush.delete_confirm_name"));
        confirmItem.setItemMeta(confirmMeta);
        confirmItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.translatable("rush.delete_confirm_lore1"),
                Component.translatable("rush.delete_confirm_lore2"))));
        gui.addItem(11, confirmItem, p -> {
            p.closeInventory();
            if (getGameRoom(room.getId()) == null) {
                p.sendMessage(Component.translatable("rush.room_not_found"));
                return;
            }
            for (Player roomPlayer : new ArrayList<>(room.getWorld().getPlayers())) {
                roomPlayer.sendMessage(Component.translatable("rush.room_admin_deleted"));
            }
            removeGameRoom(room.getId());
            p.sendMessage(Component.translatable("rush.room_deleted", Component.text(room.getHostName())));
        });

        final ItemStack cancelItem = new ItemStack(Material.LIME_CONCRETE);
        final ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.displayName(Component.translatable("rush.delete_cancel"));
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
        settingsMeta.displayName(Component.translatable("rush.settings_name"));
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
            player.setGameMode(GameMode.SURVIVAL);
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
                player.setGameMode(GameMode.SURVIVAL);
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
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
                player.sendMessage(Component.translatable("rush.reconnect_success"));
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
        meta.displayName(Component.translatable("rush.compass_name"));
        compass.setItemMeta(meta);
        compass.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.translatable("rush.compass_lore1"),
                Component.translatable("rush.compass_lore2"))));
        return compass;
    }

    /**
     * Creates the game_host item given to every player on join.
     * Placeholder for tland:game_host CraftEngine item.
     */
    public ItemStack createGameHostItem() {
        final ItemStack item = new ItemStack(Material.BEACON);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.translatable("rush.create_game_name"));
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.translatable("rush.create_game_lore1"),
                Component.translatable("rush.create_game_lore2"))));
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
        File schematicFile = new File(plugin.getDataFolder().getParentFile(), "FastAsyncWorldEdit/schematics/" + schematicName);

        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Schematic not found: " + schematicFile.getPath());
            return;
        }

        int islandIndex = 0;
        for (io.github.rush.objects.Island island : room.getIslands()) {
            pasteIslandSchematic(room.getWorld(), island, schematicFile, room.getIslandY());
            spawnMerchantsForIsland(room, island, islandIndex);
            islandIndex++;
        }

        room.setIslandsLoaded(true);
    }

    private void pasteIslandSchematic(World world, io.github.rush.objects.Island island, File schematicFile, int islandY) {
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                plugin.getLogger().severe("Unsupported schematic format: " + schematicFile.getPath());
                return;
            }
            Clipboard clipboard = format.load(schematicFile);
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

        } catch (IOException e) {
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
        for (int[] pos : speedMerchantPositions(island, dir, perpX, perpZ, speedOffset)) {
            Location speedLoc = new Location(room.getWorld(), pos[0] + 0.5, room.getIslandY() + 0.5, pos[1] + 0.5,
                    facingYaw, 0);
            spawnMerchant(room.getWorld(), speedLoc, io.github.rush.entities.MerchantType.SPEED);
        }

        // Spawn regular villagers (4)
        io.github.rush.entities.MerchantType[] regularTypes = io.github.rush.entities.MerchantType.firstN(4);
        for (int i = 0; i < 4; i++) {
            int[] pos = regularMerchantPos(i, island.getX(), island.getZ(), dir, perpX, perpZ, regularOffset, spread);
            Location villagerLoc = new Location(room.getWorld(), pos[0] + 0.5, room.getIslandY() + 1, pos[1] + 0.5,
                    facingYaw, 0);
            spawnMerchant(room.getWorld(), villagerLoc, regularTypes[i]);
        }
    }

    public static List<Location> placeIslandEnderChests(World world, int spawnX, int spawnZ, int y, int[] dir, int perpX, int enderChestOffset, BlockFace facing, int count) {
        List<Location> locations = new ArrayList<>(count);
        int perpZ = -dir[0];
        for (int i = 0; i < count; i++) {
            int sign = (i % 2 == 0) ? 1 : -1;
            int cx = spawnX + dir[0] * enderChestOffset + perpX * sign;
            int cz = spawnZ + dir[1] * enderChestOffset + perpZ * sign;
            EnderChest chestData = (EnderChest) Material.ENDER_CHEST.createBlockData();
            chestData.setFacing(facing);
            world.getBlockAt(cx, y, cz).setBlockData(chestData);
            locations.add(new Location(world, cx, y, cz));
        }
        return locations;
    }

    public static int[][] speedMerchantPositions(io.github.rush.objects.Island island, int[] dir, int perpX, int perpZ, int speedOffset) {
        int[][] positions = new int[2][2];
        for (int i = 0; i < 2; i++) {
            int sign = (i == 0) ? 1 : -1;
            positions[i][0] = island.getX() + dir[0] * speedOffset + perpX * sign;
            positions[i][1] = island.getZ() + dir[1] * speedOffset + perpZ * sign;
        }
        return positions;
    }

    public static int[] regularMerchantPos(int i, int islandX, int islandZ, int[] dir, int perpX, int perpZ, int offset, List<Integer> spread) {
        int sign = (i < 2) ? 1 : -1;
        int idx = (i % 2 == 0) ? 0 : 1;
        return new int[] {
                islandX + dir[0] * offset + perpX * spread.get(idx) * sign,
                islandZ + dir[1] * offset + perpZ * spread.get(idx) * sign
        };
    }

    private void spawnMerchant(World world, Location location, io.github.rush.entities.MerchantType type) {
        Villager villager = world.spawn(location, Villager.class);
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

        GameRoom.IslandType islandType = GameRoom.IslandType.FOUR_ISLANDS;
        if (file.header().islandTypeName() != null) {
            try {
                islandType = GameRoom.IslandType.valueOf(file.header().islandTypeName());
            } catch (IllegalArgumentException ignored) {}
        }
        int maxTeams = file.header().maxTeams();
        if (maxTeams < 2) maxTeams = 2;

        // Paste ALL islands from the layout regardless of how many teams played
        List<IslandLayout.IslandPosition> posList = IslandLayout.positionsFor(islandType, islandOffset);
        final List<io.github.rush.objects.Island> islands = new ArrayList<>();
        for (IslandLayout.IslandPosition p : posList) {
            islands.add(new io.github.rush.objects.Island(p.x(), p.z(), p.rotation()));
        }

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
                populateReplayWorld(finalWorld, file, islands);
                onReady.accept(new ReplayPlayback(file, finalWorld));
            });
        });
    }

    private void populateReplayWorld(World world, ReplayFile file,
                                     List<io.github.rush.objects.Island> islands) {
        int islandY = world.getMaxHeight() - 12;

        Map<String, String> teamColors = file.header().teamColorsByPlayerUuid();
        if (teamColors == null || teamColors.isEmpty()) return;

        Set<String> uniqueColors = new HashSet<>(teamColors.values());
        List<String> orderedTeams = uniqueColors.stream()
                .sorted(Comparator.comparingInt(c -> TeamColor.valueOf(c).ordinal()))
                .toList();

        Map<Integer, String> islandToTeam = new HashMap<>();
        int teamIdx = 0;
        for (int slot : Game.islandSlotOrder(islands.size())) {
            if (slot < islands.size() && teamIdx < orderedTeams.size()) {
                islandToTeam.put(slot, orderedTeams.get(teamIdx));
                teamIdx++;
            }
        }

        // Outward direction vectors: N→-z, E→+x, S→+z, W→-x
        int[][] directions = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
        float[] yawValues = { 0f, 90f, 180f, -90f };
        int speedOffset = plugin.getConfig().getInt("villagerSpeedOffset", 13);
        int regularOffset = plugin.getConfig().getInt("villagerRegularOffset", speedOffset - 1);

        for (Map.Entry<Integer, String> entry : islandToTeam.entrySet()) {
            int slot = entry.getKey();
            TeamColor color = TeamColor.valueOf(entry.getValue());
            io.github.rush.objects.Island island = islands.get(slot);

            int[] dir = directions[slot];
            int perpX = dir[1];
            int perpZ = -dir[0];
            float facingYaw = yawValues[slot];
            int spawnX = island.getX();
            int spawnZ = island.getZ();
            int spawnY = islandY + 2;

            // Bed placement (same math as Team.placeBed)
            int bedX = spawnX + dir[0] * 6;
            int bedZ = spawnZ + dir[1] * 6;
            int bedY = spawnY - 2;

            BlockFace bedFacing = Team.facingTowardsCenter(slot);

            Material bedMat = Team.bedMaterialFor(color);

            Bed footData = (Bed) bedMat.createBlockData();
            footData.setPart(Bed.Part.FOOT);
            footData.setFacing(bedFacing);
            world.getBlockAt(bedX, bedY, bedZ).setBlockData(footData);

            org.bukkit.block.Block headBlock = world.getBlockAt(bedX, bedY, bedZ).getRelative(bedFacing);
            Bed headData = (Bed) bedMat.createBlockData();
            headData.setPart(Bed.Part.HEAD);
            headData.setFacing(bedFacing);
            headBlock.setBlockData(headData);

            // Ender chests (2 per team island)
            int enderChestOffset = speedOffset - 1;
            placeIslandEnderChests(world, spawnX, spawnZ, bedY, dir, perpX, enderChestOffset, Team.facingTowardsCenter(slot), 2);

            // Speed merchants (2 per island)
            for (int[] pos : speedMerchantPositions(island, dir, perpX, perpZ, speedOffset)) {
                Location loc = new Location(world, pos[0] + 0.5, islandY + 0.5, pos[1] + 0.5, facingYaw, 0);
                spawnMerchant(world, loc, MerchantType.SPEED);
            }

            // Regular merchants (4 per island)
            MerchantType[] regularTypes = MerchantType.firstN(4);
            List<Integer> spread = List.of(5, 7);
            for (int i = 0; i < 4; i++) {
                int[] pos = regularMerchantPos(i, island.getX(), island.getZ(), dir, perpX, perpZ, regularOffset, spread);
                Location loc = new Location(world, pos[0] + 0.5, islandY + 1, pos[1] + 0.5, facingYaw, 0);
                spawnMerchant(world, loc, regularTypes[i]);
            }
        }
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
        long musicDurationMs = getGameEndMusicDurationMs();
        long teleportDelay = ((musicDurationMs / 1000) + 3L) * 20L;

        // Teleport players to lobby after game-end music finishes + 3 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Entity entity : room.getGame().getPlayers()) {
                if (entity instanceof Player player) {
                    player.teleport(mainLobby);
                    player.setGameMode(GameMode.ADVENTURE);
                    restoreHubInventory(player);
                }
            }

            for (Player spectator : room.getGame().getSpectators()) {
                spectator.setGameMode(GameMode.ADVENTURE);
                spectator.teleport(mainLobby);
                restoreHubInventory(spectator);
                spectator.sendMessage(Component.translatable("rush.game_ended"));
            }
        }, teleportDelay);

        // Schedule world cleanup 5 seconds after teleport
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeGameRoom(room.getId()), teleportDelay + 100L);
    }

}
