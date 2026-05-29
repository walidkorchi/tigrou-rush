package io.github.rush.game;

import io.github.rush.abstracts.Team;

import io.github.rush.entities.GameCombatant;
import io.github.rush.entities.GamePlayer;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;

import io.github.rush.Main;
import io.github.rush.storage.ConfigManager;
import io.github.rush.utils.i18n;
import io.github.rush.utils.RushLogger;
import io.github.rush.guis.GUI;
import io.github.rush.guis.HostConfigGUI;
import io.github.rush.guis.TeamSelectionGUI;
import io.github.rush.objects.Island;
import io.github.rush.utils.ItemBuilder;

import io.github.rush.utils.VoidWorld;
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
import org.bukkit.block.data.type.EnderChest;
import org.bukkit.entity.ItemFrame;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import io.github.rush.entities.Merchant;
import io.github.rush.entities.MerchantType;
import io.github.rush.utils.ReplayUtils.ReplayFile;
import io.github.rush.utils.ReplayUtils.ReplayHeader;
import io.github.rush.replay.ReplayPlayback;
import io.github.rush.sound.RushSounds;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages multiple game rooms and all game-related operations.
 */
public class GameManager {

    private final Main plugin;
    private final Map<String, GameRoom> gameRooms = new HashMap<>();
    private final Map<Player, GameRoom> playerGameRoomMap = new HashMap<>();
    private final Map<UUID, GameRoomConfig.Builder> pendingConfigs = new HashMap<>();
    private final Map<UUID, ReconnectData> reconnectDataMap = new HashMap<>();
    private final Map<UUID, ReplayFile> pendingArchives = new HashMap<>();

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

    private int computeIslandY(World world) {
        return world.getMaxHeight() - plugin.getConfig().getInt("distance-height-limit");
    }

    public GameManager(Main plugin) {
        this.plugin = plugin;

        Island.Type.reload(new File(plugin.getDataFolder().getParentFile(),
                "FastAsyncWorldEdit/schematics/islands"));
    }

    public void storePendingArchive(UUID hostUUID, ReplayFile replayFile) {
        pendingArchives.put(hostUUID, replayFile);
    }

    /**
     * Returns and removes the pending archive for this player, or null if none.
     */
    public ReplayFile consumePendingArchive(UUID hostUUID) {
        return pendingArchives.remove(hostUUID);
    }

    /**
     * Creates a new game room with its own world using the async pipeline.
     * Room is registered immediately in CREATING state, transitions to WAITING
     * once schematics are pasted and the host is confirmed still online.
     */
    public void createGameRoom(Player host, GameRoomConfig config) {
        final UUID hostUUID = host.getUniqueId();
        final String worldName = "rush_game_" + (++worldCounter) + "_" + host.getName();
        final LoadingBar bar = new LoadingBar(plugin, host, 5, i18n.raw("rush.loading_create_world"));

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                World gameWorld = createVoidWorld(worldName);

                if (gameWorld != null) {
                    bar.update(i18n.raw("rush.loading_init"), 1);

                    Location lobbyLocation = new Location(gameWorld, 0, 0, 0);
                    final GameRoom room = new GameRoom(host.getName(), hostUUID, gameWorld, config, lobbyLocation);

                    room.getGame().setState(GameState.CREATING);
                    gameRooms.put(room.getId(), room);

                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        bar.update(i18n.raw("rush.loading_lobby"), 2);
                        File waitingRoomFile = plugin.getConfigManager().getSchematicFile("waiting_room.schem");

                        bar.update(i18n.raw("rush.loading_islands"), 3);
                        final File islandFile = plugin.getConfigManager()
                                .getSchematicFile(config.mapType().schematicName());

                        bar.update(i18n.raw("rush.loading_build"), 4);

                        BlockVector3 waitingTarget = BlockVector3.at(0, 64, 0); // fallback
                        if (waitingRoomFile != null) {
                            final ClipboardFormat fmt = ClipboardFormats.findByFile(waitingRoomFile);

                            if (fmt != null) {
                                try (ClipboardReader reader = fmt.getReader(new FileInputStream(waitingRoomFile));
                                        final ClipboardHolder holder = new ClipboardHolder(reader.read())) {

                                    waitingTarget = BlockVector3.at(0, holder.getClipboard().getOrigin().getY(), 0);

                                    final EditSession editSession = WorldEdit.getInstance()
                                            .newEditSession(BukkitAdapter.adapt(gameWorld));

                                    Operations.complete(holder.createPaste(editSession)
                                            .to(waitingTarget).ignoreAirBlocks(false).build());
                                } catch (IOException | WorldEditException e) {
                                    RushLogger.error(i18n.log(
                                            "internal.game_manager.schematic_paste_failed", e.getMessage()));
                                }
                            } else {
                                RushLogger.error(i18n.log(
                                        "internal.game_manager.schematic_unknown_format",
                                        waitingRoomFile.getPath()));
                            }
                        }

                        if (islandFile != null) {
                            for (Island isl : room.getIslands()) {
                                pasteSchematicFile(gameWorld, islandFile,
                                        BlockVector3.at(isl.getX(), room.getIslandY(), isl.getZ()),
                                        isl.getRotation());
                            }
                        }

                        final BlockVector3 finalWaitingTarget = waitingTarget;

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            final List<Island> islands = room.getIslands();

                            for (int i = 0; i < islands.size(); i++) {
                                spawnMerchantsForIsland(room, islands.get(i), i);
                            }

                            lobbyLocation.setX(finalWaitingTarget.getX());
                            lobbyLocation.setY(finalWaitingTarget.getY());
                            lobbyLocation.setZ(finalWaitingTarget.getZ());
                            gameWorld.setSpawnLocation(finalWaitingTarget.getX(),
                                    finalWaitingTarget.getY(), finalWaitingTarget.getZ());

                            final Player onlineHost = Bukkit.getPlayer(hostUUID);

                            if (onlineHost == null) {
                                bar.cancel();
                                cancelRoomCreation(room);
                                return;
                            }

                            room.getGame().setState(GameState.WAITING);
                            playerGameRoomMap.put(onlineHost, room);

                            onlineHost.teleport(lobbyLocation);
                            onlineHost.getInventory().clear();
                            onlineHost.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());
                            onlineHost.getInventory().setItem(8, createHostPanelItem());

                            bar.stop(plugin);
                        });
                    });
                } else {
                    bar.cancel();
                    host.sendMessage(Component.translatable("rush.room_create_failed"));
                    return;
                }
            } catch (Exception e) {
                bar.cancel();
                RushLogger.error(i18n.log("internal.game_manager.world_create_failed", e.getMessage()));
                e.printStackTrace();
                host.sendMessage(Component.translatable("rush.room_create_failed"));
            }
        });
    }

    public void cancelRoomCreation(GameRoom room) {
        gameRooms.remove(room.getId());

        final World world = room.getWorld();
        if (world != null) {
            destroyWorld(world, false);
        }
    }

    private void pasteSchematicFile(World world, File schematicFile, BlockVector3 target, int rotation) {
        final ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);

        if (format != null) {
            // Must be called from an async thread. Load and paste happen here on the same
            // thread — FAWE's DiskOptimizedClipboard ties its MappedByteBuffer to the
            // loading thread, so both operations must share it.
            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile));
                    final ClipboardHolder holder = new ClipboardHolder(reader.read())) {

                if (rotation != 0) {
                    AffineTransform transform = new AffineTransform().rotateY(rotation);
                    holder.setTransform(holder.getTransform().combine(transform));
                }

                final EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world));
                final Operation operation = holder.createPaste(editSession).to(target).ignoreAirBlocks(false).build();

                Operations.complete(operation);
                RushLogger.info(i18n.log("internal.game_manager.schematic_pasted", target));
            } catch (IOException | WorldEditException e) {
                RushLogger.error(i18n.log("internal.game_manager.schematic_paste_failed", e.getMessage()));
            }
        } else {
            RushLogger.error(i18n.log("internal.game_manager.schematic_unknown_format", schematicFile.getPath()));
            return;
        }
    }

    public ItemStack createHostPanelItem() {
        return ItemBuilder.of(Material.NETHER_STAR).name(i18n.txt("rush.host_panel_name")).build();
    }

    private World createVoidWorld(String worldName) {
        final WorldCreator worldCreator = new WorldCreator(worldName)
                .generator(new VoidWorld())
                .environment(World.Environment.NORMAL);
        final World gameWorld = Bukkit.createWorld(worldCreator);

        @SuppressWarnings("removal")
        final List<GameRule<Boolean>> gameRules = List.of(
                GameRule.DO_DAYLIGHT_CYCLE,
                GameRule.DO_WEATHER_CYCLE,
                GameRule.DO_INSOMNIA);

        if (gameWorld != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
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
            room.getGame().addObserver(new GamePlayer(player));
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
        return null;
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

            for (Player player : new ArrayList<>(world.getPlayers())) {
                resetPlayerHubState(player);
            }

            if (room.getGame() != null) {
                room.getGame().stop();
            }

            destroyWorld(world, true);
        }
    }

    private void destroyWorld(World world, boolean log) {
        Bukkit.unloadWorld(world, false);
        if (log) {
            RushLogger.info(i18n.log("internal.game_manager.world_unloaded", world.getName()));
        }
        File worldFolder = new File(Bukkit.getWorldContainer(), world.getName());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ConfigManager.deleteDirectory(worldFolder);
            if (log) {
                RushLogger.info(i18n.log("internal.game_manager.world_folder_deleted",
                        worldFolder.getAbsolutePath()));
            }
        });
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
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(header.startTimestamp()), ZoneId.systemDefault());
        String date = String.format("%02d/%02d/%04d %02d:%02d",
                dt.getDayOfMonth(), dt.getMonthValue(), dt.getYear(),
                dt.getHour(), dt.getMinute());

        long totalSeconds = header.durationMs() / 1000;
        String duration = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);

        String winner = header.winnerTeamColorName() != null ? header.winnerTeamColorName() : "Aucun";

        return ItemBuilder.of(Material.ORANGE_WOOL)
                .name(i18n.txt("rush.replay_item_name", header.hostName()))
                .lore(
                        Component.translatable("rush.replay_item_host", Component.text(header.hostName())),
                        Component.translatable("rush.replay_item_date", Component.text(date)),
                        Component.translatable("rush.replay_item_duration", Component.text(duration)),
                        Component.translatable("rush.replay_item_winner", Component.text(winner)),
                        Component.translatable("rush.replay_item_players",
                                Component.text(header.participantNames().size())),
                        Component.empty(),
                        Component.translatable("rush.replay_item_click"))
                .build();
    }

    public void openDeleteConfirmation(Player admin, GameRoom room) {
        final GUI gui = new GUI(Component.translatable("rush.delete_confirm_title"), 3);

        gui.addItem(13, createGameRoomItem(room, false));

        final ItemStack confirmItem = ItemBuilder.of(Material.BARRIER)
                .name(i18n.txt("rush.delete_confirm_name"))
                .lore(
                        Component.translatable("rush.delete_confirm_lore1"),
                        Component.translatable("rush.delete_confirm_lore2"))
                .build();
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

        final ItemStack cancelItem = ItemBuilder.of(Material.LIME_CONCRETE)
                .name(i18n.txt("rush.delete_cancel"))
                .build();
        gui.addItem(15, cancelItem, p -> {
            p.closeInventory();
            openGameList(p);
        });

        gui.openGUI(admin);
    }

    public void resetPlayerHubState(Player player) {
        player.teleport(Main.getInstance().getMainLobby());
        player.setAllowFlight(false);
        player.setFallDistance(0);
        player.setGameMode(GameMode.ADVENTURE);
        Game.resetPlayerHealth(player);
        restoreHubInventory(player);
    }

    public void restoreHubInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, createCompassItem());
        player.getInventory().setItem(1, createPlayerSkullItem(player));
        player.getInventory().setItem(7, createGameHostItem());
        player.getInventory().setItem(8, createSettingsItem());
    }

    public void recordDisconnect(UUID uuid, ReconnectData data) {
        reconnectDataMap.put(uuid, data);
    }

    public ReconnectData consumeReconnectData(UUID uuid) {
        return reconnectDataMap.remove(uuid);
    }

    public void handleReconnect(Player player, GameRoom room, ReconnectData data) {
        // edge case: game ended before the reconnect task ran — send to hub
        if (!player.isOnline() || !room.isRunning()) {
            resetPlayerHubState(player);
        }

        final Game game = room.getGame();

        if (data.wasSpectator()) {
            game.addObserver(new GamePlayer(player));
            return;
        }

        if (data.teamColorName() != null) {
            final Team team = game.getTeam(data.teamColorName());

            if (team != null && !team.isBedDestroyed()) {
                // Re-add to team and apply respawn behaviour (same as normal death)
                team.addPlayer(new GamePlayer(player));
                player.setGameMode(GameMode.SURVIVAL);
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                player.setFoodLevel(20);
                player.setSaturation(20f);
                player.getInventory().clear();
                game.equipEntity(new GamePlayer(player), team);

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
                game.addSpectator(new GamePlayer(player));
            }
        } else {
            // Was a free player at disconnect time — rejoin as observer
            game.addObserver(new GamePlayer(player));
        }
    }

    public ItemStack createPlayerSkullItem(Player player) {
        final ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(i18n.txt("rush.skull_name", player.getName()));
        skull.setItemMeta(meta);
        skull.setData(DataComponentTypes.LORE,
                ItemLore.lore(List.of(i18n.txt("rush.skull_lore1"))));
        return skull;
    }

    public ItemStack createCompassItem() {
        return ItemBuilder.of(Material.COMPASS)
                .name(i18n.txt("rush.compass_name"))
                .lore(
                        Component.translatable("rush.compass_lore1"),
                        Component.translatable("rush.compass_lore2"))
                .build();
    }

    public ItemStack createSettingsItem() {
        return ItemBuilder.of(Material.REPEATER)
                .name(i18n.txt("rush.settings_name"))
                .build();
    }

    public ItemStack createGameHostItem() {
        return ItemBuilder.of(Material.BEACON)
                .name(i18n.txt("rush.create_game_name"))
                .lore(
                        Component.translatable("rush.create_game_lore1"),
                        Component.translatable("rush.create_game_lore2"))
                .build();
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

    private void spawnMerchantsForIsland(GameRoom room, Island island, int islandIndex) {
        placeIslandMerchants(room.getWorld(), island, islandIndex, room.getIslandY());
    }

    public static void placeIslandMerchants(World world, Island island, int islandIndex,
            int islandY) {
        int speedOffset = Main.getInstance().getConfig().getInt("villagerSpeedOffset");
        int regularOffset = Main.getInstance().getConfig().getInt("villagerRegularOffset", speedOffset - 1);

        int[] dir = Island.Layout.ISLAND_DIRECTIONS[islandIndex];
        int perpX = dir[1];
        int perpZ = -dir[0];
        float facingYaw = Island.Layout.YAW_VALUES[islandIndex];

        // Spawn speed villagers (2) — one directly behind each ender chest
        for (int[] pos : speedMerchantPositions(island, dir, perpX, perpZ, speedOffset)) {
            Location speedLoc = new Location(world, pos[0] + 0.5, islandY + 0.5, pos[1] + 0.5,
                    facingYaw, 0);
            spawnMerchant(world, speedLoc, MerchantType.SPEED);
        }

        // Spawn regular villagers (4)
        MerchantType[] regularTypes = MerchantType.firstN(4);
        for (int i = 0; i < 4; i++) {
            int[] pos = regularMerchantPos(i, island.getX(), island.getZ(), dir, perpX, perpZ, regularOffset);
            Location villagerLoc = new Location(world, pos[0] + 0.5, islandY + 1, pos[1] + 0.5,
                    facingYaw, 0);
            spawnMerchant(world, villagerLoc, regularTypes[i]);
        }
    }

    public static List<Location> placeIslandEnderChests(World world, int spawnX, int spawnZ, int y, int[] dir,
            int perpX, int enderChestOffset, BlockFace facing, int count) {
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

    public static int[][] speedMerchantPositions(Island island, int[] dir, int perpX, int perpZ,
            int speedOffset) {
        int[][] positions = new int[2][2];
        for (int i = 0; i < 2; i++) {
            int sign = (i == 0) ? 1 : -1;
            positions[i][0] = island.getX() + dir[0] * speedOffset + perpX * sign;
            positions[i][1] = island.getZ() + dir[1] * speedOffset + perpZ * sign;
        }
        return positions;
    }

    public static int[] regularMerchantPos(int i, int islandX, int islandZ, int[] dir, int perpX, int perpZ,
            int offset) {
        final int sign = (i < 2) ? 1 : -1;
        final int idx = (i % 2 == 0) ? 0 : 1;
        return new int[] {
                islandX + dir[0] * offset + perpX * Island.Layout.MERCHANT_SPREADS.get(idx) * sign,
                islandZ + dir[1] * offset + perpZ * Island.Layout.MERCHANT_SPREADS.get(idx) * sign
        };
    }

    private static void spawnMerchant(World world, Location location, MerchantType type) {
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

        if (type.getDisplayItem() != null) {
            final float yaw = location.getYaw();
            final double rad = Math.toRadians(yaw);
            // 2 blocks toward center from the merchant's block position.
            // Integer block coords avoid the double +0.5 offset from the spawn Location.
            // Yaw in the spawn Location drives ItemFrame facing — setFacingDirection is
            // intentionally avoided because it repositions the entity onto a block face.
            final int dx = (int) Math.round(-Math.sin(rad));
            final int dz = (int) Math.round(Math.cos(rad));
            final int frameX = location.getBlockX() + dx * 2;
            final int frameZ = location.getBlockZ() + dz * 2;

            final ItemFrame frame = world.spawn(
                    new Location(world, frameX + 0.5, location.getY(), frameZ + 0.5, yaw, 0),
                    ItemFrame.class);

            frame.setItem(new ItemStack(type.getDisplayItem()));
            frame.setInvulnerable(true);
            frame.setFixed(true);
            frame.setVisible(false);
        }
    }

    private static final String REPLAY_WORLD_PREFIX = "rush_replay_";

    /**
     * Creates a void world for a replay session, pastes island schematics, then
     * calls onReady
     * with the resulting ReplayPlayback on the main thread. Must be called from the
     * main thread.
     */
    public void createReplayWorld(Player viewer, ReplayFile file, Consumer<ReplayPlayback> onReady) {
        final String sessionId = file.header().sessionId();

        final LoadingBar bar = new LoadingBar(plugin, viewer, 4, i18n.raw("rush.loading_create_world"));

        final World world = createVoidWorld(REPLAY_WORLD_PREFIX + sessionId);

        if (world == null) {
            bar.cancel();
            RushLogger.error(i18n.log("internal.game_manager.replay_world_create_failed", sessionId));
            return;
        }

        final World finalWorld = world;
        final int islandY = computeIslandY(world);
        final int islandOffset = plugin.getConfig().getInt("islandOffset");
        GameRoom.IslandType islandType = GameRoom.IslandType.FOUR_ISLANDS;

        if (file.header().islandTypeName() != null) {
            try {
                islandType = GameRoom.IslandType.valueOf(file.header().islandTypeName());
            } catch (IllegalArgumentException ignored) {
            }
        }

        int maxTeams = file.header().maxTeams();
        if (maxTeams < 2)
            maxTeams = 2;

        final List<Island> islands = new ArrayList<>();
        for (Island.Layout.IslandPosition p : Island.Layout.positionsFor(islandType, islandOffset)) {
            islands.add(new Island(p.x(), p.z(), p.rotation()));
        }

        final Island.Type resolvedMapType = Island.Type.byName(file.header().mapTypeName())
                .orElseGet(() -> Island.Type.all().isEmpty() ? null : Island.Type.all().get(0));

        bar.update(i18n.raw("rush.loading_islands"), 2);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File islandFile = resolvedMapType != null
                    ? plugin.getConfigManager().getSchematicFile(resolvedMapType.schematicName())
                    : null;

            bar.update(i18n.raw("rush.loading_build"), 3);

            if (islandFile != null) {
                for (Island island : islands) {
                    pasteSchematicFile(finalWorld, islandFile,
                            BlockVector3.at(island.getX(), islandY, island.getZ()),
                            island.getRotation());
                }
            } else {
                RushLogger.warn(i18n.log("internal.game_manager.replay_world_no_schematic",
                        sessionId, resolvedMapType != null ? resolvedMapType.schematicName() : "none"));
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                bar.update(i18n.raw("rush.loading_build"), 4);
                populateReplayWorld(finalWorld, file, islands);
                bar.stop(plugin);
                onReady.accept(new ReplayPlayback(file, finalWorld));
            });
        });
    }

    private void populateReplayWorld(World world, ReplayFile file, List<Island> islands) {
        int islandY = computeIslandY(world);

        Map<String, String> teamColors = file.header().teamColorsByPlayerUuid();
        if (teamColors == null || teamColors.isEmpty())
            return;

        Set<String> uniqueColors = new HashSet<>(teamColors.values());
        List<String> orderedTeams = uniqueColors.stream()
                .sorted(Comparator.comparingInt(c -> Team.Color.valueOf(c).ordinal()))
                .toList();

        Map<Integer, String> islandToTeam = new HashMap<>();
        int teamIdx = 0;
        for (int slot : Game.islandSlotOrder(islands.size())) {
            if (slot < islands.size() && teamIdx < orderedTeams.size()) {
                islandToTeam.put(slot, orderedTeams.get(teamIdx));
                teamIdx++;
            }
        }

        boolean extraHearts = false;
        try {
            extraHearts = file.header().extraHearts();
        } catch (Exception ignored) {
        }

        for (int slot = 0; slot < islands.size(); slot++) {
            final Island island = islands.get(slot);

            if (islandToTeam.containsKey(slot)) {
                Team.placeIslandBed(world, island, slot, islandY, Team.Color.valueOf(islandToTeam.get(slot)));
            } else if (extraHearts) {
                final Set<Team.Color> takenColors = islandToTeam.values().stream()
                        .map(Team.Color::valueOf)
                        .collect(Collectors.toSet());
                final List<Team.Color> extraColors = Game.randomExtraBedColors(takenColors);

                Team.placeIslandBed(world, island, slot, islandY,
                        extraColors.get(slot % extraColors.size()));
            }

            final int enderChestOffset = plugin.getConfig().getInt("villagerSpeedOffset") - 1;
            final int[] dir = Island.Layout.ISLAND_DIRECTIONS[slot];

            placeIslandEnderChests(world, island.getX(), island.getZ(), islandY, dir, dir[1], enderChestOffset,
                    Team.facingTowardsCenter(slot), 2);
            placeIslandMerchants(world, island, slot, islandY);
        }
    }

    /**
     * Unloads and deletes a replay world created by createReplayWorld.
     */
    public void destroyReplayWorld(World world) {
        if (world == null)
            return;
        destroyWorld(world, false);
    }

    /**
     * Called when a GameRoom's game starts.
     */
    public void onGameRoomStarted(GameRoom room) {
        RushLogger.info(i18n.log("internal.game_manager.game_started", room.getId()));

        for (GameCombatant participant : room.getGame().getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                final Player player = gp.player();

                player.sendMessage(Component.translatable("rush.game_started"));
                room.getGame().getPlayerStatistic(player);
            }
        }
    }

    /**
     * Called when a GameRoom's game ends.
     * Teleports players to lobby after game-end music finishes + 3 seconds
     * Cleans up game world 5 seconds right after teleport
     */
    public void onGameRoomEnded(GameRoom room) {
        RushLogger.info(i18n.log("internal.game_manager.game_ended", room.getId()));

        final long teleportDelay = ((RushSounds.GAME_END_MUSIC.getDurationMs() / 1000) + 3L) * 20L;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (GameCombatant participant : room.getGame().getPlayers()) {
                if (participant instanceof GamePlayer gp) {
                    resetPlayerHubState(gp.player());
                }
            }
            for (GamePlayer gp : room.getGame().getSpectators()) {
                resetPlayerHubState(gp.player());
            }
        }, teleportDelay);

        Bukkit.getScheduler().runTaskLater(plugin, () -> removeGameRoom(room.getId()), teleportDelay + 100L);
    }

}
