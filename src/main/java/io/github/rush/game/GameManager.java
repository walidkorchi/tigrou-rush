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
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import io.github.rush.Main;
import io.github.rush.storage.ConfigManager;
import io.github.rush.utils.i18n;
import io.github.rush.utils.RushLogger;
import io.github.rush.Hub;
import io.github.rush.guis.GUI;
import io.github.rush.guis.ConfirmationGUI;
import io.github.rush.guis.HostConfigGUI;
import io.github.rush.guis.TeamSelectionGUI;
import io.github.rush.objects.Island;
import io.github.rush.utils.ItemBuilder;

import io.github.rush.utils.VoidWorld;

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

import io.github.rush.inventories.HubInventory;
import io.github.rush.inventories.GamePlayerInventory;
import io.github.rush.entities.Merchant;
import io.github.rush.entities.MerchantType;
import io.github.rush.utils.ReplayUtils.ReplayFile;
import io.github.rush.utils.ReplayUtils.ReplayHeader;
import io.github.rush.replay.ReplayPlayback;
import io.github.rush.utils.Sounds;

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
    private final Map<String, BlockVector3[]> lobbyBounds = new HashMap<>();

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

                    final Location lobbyLocation = new Location(gameWorld, 0, 0, 0);
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
                                        ClipboardHolder holder = new ClipboardHolder(reader.read());
                                        EditSession editSession = WorldEdit.getInstance()
                                                .newEditSession(BukkitAdapter.adapt(gameWorld))) {

                                    waitingTarget = BlockVector3.at(0, holder.getClipboard().getOrigin().getY(), 0);

                                    Operations.complete(holder.createPaste(editSession)
                                            .to(waitingTarget).ignoreAirBlocks(false).build());

                                    BlockVector3 pasteOffset = waitingTarget
                                            .subtract(holder.getClipboard().getOrigin());
                                    lobbyBounds.put(room.getId(), new BlockVector3[] {
                                            holder.getClipboard().getRegion().getMinimumPoint().add(pasteOffset),
                                            holder.getClipboard().getRegion().getMaximumPoint().add(pasteOffset)
                                    });
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

                            for (Island island : islands) {
                                spawnMerchantsForIsland(room, island);
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
                            GamePlayerInventory.give(onlineHost);

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
            destroyWorld(world);
        }
    }

    private void pasteSchematicFile(World world, File schematicFile, BlockVector3 target, int rotation) {
        final ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);

        if (format != null) {
            // Must be called from an async thread. Load and paste happen here on the same
            // thread — FAWE's DiskOptimizedClipboard ties its MappedByteBuffer to the
            // loading thread, so both operations must share it.
            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile));
                    ClipboardHolder holder = new ClipboardHolder(reader.read());
                    EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {

                if (rotation != 0) {
                    AffineTransform transform = new AffineTransform().rotateY(rotation);
                    holder.setTransform(holder.getTransform().combine(transform));
                }

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

        if (room.isLocked() && !player.getUniqueId().equals(room.getHostUUID())) {
            player.sendMessage(i18n.txt("rush.room_locked"));
            return;
        }

        playerGameRoomMap.put(player, room);
        room.getJoinOrder().add(player.getUniqueId());
        player.teleport(room.getLobbyLocation());
        player.getInventory().clear();
        player.getInventory().setItem(0, GamePlayerInventory.createBannerItem());

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

    public boolean isPlayerInWaitingRoom(Player player) {
        GameRoom room = getGameRoomByWorld(player.getWorld().getName());
        return room != null && room.isWaiting();
    }

    public boolean isPlayerInRunningGame(Player player) {
        if (gameRooms.isEmpty())
            return false;
        GameRoom room = getGameRoomByWorld(player.getWorld().getName());
        return room != null && room.isRunning();
    }

    public void removeGameRoom(String id) {
        final GameRoom room = gameRooms.remove(id);

        if (room != null) {
            playerGameRoomMap.values().remove(room);

            final World world = room.getWorld();

            for (Player player : new ArrayList<>(world.getPlayers())) {
                Hub.resetPlayer(player);
            }

            if (room.getGame() != null) {
                room.getGame().stop();
            }

            destroyWorld(world);
        }
    }

    public void destroyWorld(World world) {
        Bukkit.unloadWorld(world, false);
        RushLogger.info(i18n.log("internal.game_manager.world_unloaded", world.getName()));

        final File worldFolder = new File(Bukkit.getWorldContainer(), world.getName());

        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                ConfigManager.deleteDirectory(worldFolder);
                RushLogger.info(i18n.log("internal.game_manager.world_folder_deleted",
                        worldFolder.getAbsolutePath()));
            });
        } else {
            // Plugin is disabling (server shutdown) — scheduler rejects new tasks,
            // so delete the world folder synchronously on the main thread instead.
            ConfigManager.deleteDirectory(worldFolder);
            RushLogger.info(i18n.log("internal.game_manager.world_folder_deleted",
                    worldFolder.getAbsolutePath()));
        }
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
            if (isAdmin) {
                gui.addItem(slot, createReplayItem(replay, true),
                        p -> Main.getInstance().getReplayManager().joinReplay(p, targetReplay),
                        p -> openDeleteReplayConfirmation(p, targetReplay));
            } else {
                gui.addItem(slot, createReplayItem(replay, false),
                        p -> Main.getInstance().getReplayManager().joinReplay(p, targetReplay));
            }
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

        final List<Component> lore = new ArrayList<>(List.of(
                i18n.txt("rush.room_lore_host", room.getHostName()),
                i18n.txt("rush.room_lore_status", i18n.raw(status)),
                i18n.txt("rush.room_lore_map", room.getConfig().mapType().displayName()),
                i18n.txt("rush.room_lore_players", room.getPlayerCount(), room.getMaxPlayers()),
                Component.empty(),
                i18n.txt(actionLine)));

        if (isAdmin) {
            lore.add(Component.empty());
            lore.add(i18n.txt("rush.room_admin_delete_hint"));
        }

        return ItemBuilder.of(material)
                .name(room.getDisplayName())
                .lore(lore.toArray(new Component[0]))
                .build();
    }

    private ItemStack createReplayItem(ReplayHeader header, boolean isAdmin) {
        final LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(header.startTimestamp()), ZoneId.systemDefault());
        final String date = String.format("%02d/%02d/%04d %02d:%02d",
                dt.getDayOfMonth(), dt.getMonthValue(), dt.getYear(),
                dt.getHour(), dt.getMinute());
        final long totalSeconds = header.durationMs() / 1000;
        final String duration = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
        final String winner = header.winnerTeamColorName() != null ? header.winnerTeamColorName() : "Aucun";

        final List<Component> lore = new ArrayList<>(List.of(
                i18n.txt("rush.replay_item_host", header.hostName()),
                i18n.txt("rush.replay_item_date", date),
                i18n.txt("rush.replay_item_duration", duration),
                i18n.txt("rush.replay_item_winner", winner),
                i18n.txt("rush.replay_item_players", header.participantNames().size()),
                Component.empty(),
                i18n.txt("rush.replay_item_click")));

        if (isAdmin) {
            lore.add(Component.empty());
            lore.add(i18n.txt("rush.replay_admin_delete_hint"));
        }

        return ItemBuilder.of(Material.ORANGE_WOOL)
                .name(i18n.txt("rush.replay_item_name", header.hostName()))
                .lore(lore.toArray(new Component[0]))
                .build();
    }

    public void openDeleteConfirmation(Player admin, GameRoom room) {
        final ItemStack confirmItem = ItemBuilder.of(Material.BARRIER)
                .name(i18n.txt("rush.delete_confirm_name"))
                .lore(
                        Component.translatable("rush.delete_confirm_lore1"),
                        Component.translatable("rush.delete_confirm_lore2"))
                .build();
        final ItemStack cancelItem = ItemBuilder.of(Material.LIME_CONCRETE)
                .name(i18n.txt("rush.delete_cancel"))
                .build();

        ConfirmationGUI.of(createGameRoomItem(room, false))
                .confirm(confirmItem, p -> {
                    if (getGameRoom(room.getId()) == null) {
                        p.sendMessage(Component.translatable("rush.room_not_found"));
                        return;
                    }
                    for (Player roomPlayer : new ArrayList<>(room.getWorld().getPlayers())) {
                        roomPlayer.sendMessage(Component.translatable("rush.room_admin_deleted"));
                    }
                    removeGameRoom(room.getId());
                    p.sendMessage(Component.translatable("rush.room_deleted",
                            Component.text(room.getHostName())));
                })
                .cancel(cancelItem, p -> openGameList(p))
                .open(admin);
    }

    public void openDeleteReplayConfirmation(Player admin, ReplayHeader replay) {
        final ItemStack confirmItem = ItemBuilder.of(Material.BARRIER)
                .name(i18n.txt("rush.replay_delete_confirm_name"))
                .lore(
                        Component.translatable("rush.replay_delete_confirm_lore1"),
                        Component.translatable("rush.replay_delete_confirm_lore2"))
                .build();
        final ItemStack cancelItem = ItemBuilder.of(Material.LIME_CONCRETE)
                .name(i18n.txt("rush.replay_delete_cancel"))
                .build();

        ConfirmationGUI.of(createReplayItem(replay, false))
                .confirm(confirmItem, p -> {
                    Main.getInstance().getReplayStorage().delete(replay.sessionId());
                    p.sendMessage(i18n.txt("rush.replay_deleted", replay.hostName()));
                    openGameList(p);
                })
                .cancel(cancelItem, p -> openGameList(p))
                .open(admin);
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
            Hub.resetPlayer(player);
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

    private void spawnMerchantsForIsland(GameRoom room, Island island) {
        placeIslandMerchants(room.getWorld(), island, room.getIslandY());
    }

    public static void placeIslandMerchants(World world, Island island, int islandY) {
        int speedOffset = Main.getInstance().getConfig().getInt("villagerSpeedOffset");
        int regularOffset = Main.getInstance().getConfig().getInt("villagerRegularOffset", speedOffset - 1);

        int[] dir = new int[] { island.getDirX(), island.getDirZ() };
        int perpX = dir[1];
        int perpZ = -dir[0];
        float facingYaw = island.getMerchantYaw();

        // Spawn speed villagers (2) — one directly behind each ender chest
        for (int[] pos : Island.Layout.speedMerchantPositions(island, dir, perpX, perpZ, speedOffset)) {
            final Location loc = new Location(world, pos[0] + 0.5, islandY + 0.5, pos[1] + 0.5, facingYaw, 0);
            spawnMerchant(world, loc, MerchantType.SPEED);
        }

        // Spawn regular villagers (4)
        MerchantType[] regularTypes = MerchantType.firstN(4);
        for (int i = 0; i < 4; i++) {
            final int[] pos = Island.Layout.regularMerchantPos(i, island.getX(), island.getZ(), dir, perpX, perpZ,
                    regularOffset);
            final Location loc = new Location(world, pos[0] + 0.5, islandY + 1, pos[1] + 0.5, facingYaw, 0);

            spawnMerchant(world, loc, regularTypes[i]);
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
        final int islandY = Island.Layout.computeIslandY(world.getMaxHeight(),
                plugin.getConfig().getInt("distance-height-limit"));
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
            islands.add(new Island(p.x(), p.z(), p.rotation(), p.dirX(), p.dirZ(), p.merchantYaw(), p.facing()));
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
        int islandY = Island.Layout.computeIslandY(world.getMaxHeight(),
                Main.getInstance().getConfig().getInt("distance-height-limit"));

        Map<String, String> teamColors = file.header().teamColorsByPlayerUuid();
        if (teamColors == null || teamColors.isEmpty())
            return;

        Set<String> uniqueColors = new HashSet<>(teamColors.values());
        List<String> orderedTeams = uniqueColors.stream()
                .sorted(Comparator.comparingInt(c -> Team.Color.valueOf(c).ordinal()))
                .toList();

        Map<Integer, String> islandToTeam = new HashMap<>();
        int teamIdx = 0;
        for (int slot : Island.Layout.islandSlotOrder(islands.size())) {
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
                Team.placeIslandBed(world, island, islandY, Team.Color.valueOf(islandToTeam.get(slot)));
            } else if (extraHearts) {
                final Set<Team.Color> takenColors = islandToTeam.values().stream()
                        .map(Team.Color::valueOf)
                        .collect(Collectors.toSet());
                final List<Team.Color> extraColors = Game.randomExtraBedColors(takenColors);

                Team.placeIslandBed(world, island, islandY, extraColors.get(slot % extraColors.size()));
            }

            final int enderChestOffset = plugin.getConfig().getInt("villagerSpeedOffset") - 1;
            final int[] dir = new int[] { island.getDirX(), island.getDirZ() };

            placeIslandEnderChests(world, island.getX(), island.getZ(), islandY, dir, dir[1], enderChestOffset,
                    island.getFacing(), 2);
            placeIslandMerchants(world, island, islandY);
        }
    }

    /**
     * Called when a GameRoom's game starts.
     */
    public void onGameRoomStarted(GameRoom room) {
        clearLobbyAsync(room);

        RushLogger.info(i18n.log("internal.game_manager.game_started", room.getId()));

        for (GameCombatant participant : room.getGame().getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                final Player player = gp.player();

                player.sendMessage(Component.translatable("rush.game_started"));
                room.getGame().getPlayerStatistic(player);
            }
        }
    }

    private void clearLobbyAsync(GameRoom room) {
        final BlockVector3[] bounds = lobbyBounds.remove(room.getId());
        if (bounds == null)
            return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (EditSession editSession = WorldEdit.getInstance()
                    .newEditSession(BukkitAdapter.adapt(room.getWorld()))) {
                CuboidRegion region = new CuboidRegion(
                        BukkitAdapter.adapt(room.getWorld()), bounds[0], bounds[1]);
                Pattern airPattern = pos -> BlockTypes.AIR.getDefaultState().toBaseBlock();
                editSession.setBlocks((com.sk89q.worldedit.regions.Region) region, airPattern);
            } catch (WorldEditException e) {
                RushLogger.error(i18n.log("internal.game_manager.lobby_clear_failed", e.getMessage()));
            }
        });
    }

    /**
     * Called when a GameRoom's game ends.
     * Teleports players to lobby after game-end music finishes + 3 seconds
     * Cleans up game world 5 seconds right after teleport
     */
    public void onGameRoomEnded(GameRoom room) {
        RushLogger.info(i18n.log("internal.game_manager.game_ended", room.getId()));

        final long teleportDelay = ((Sounds.GAME_END_MUSIC.getDurationMs() / 1000) + 3L) * 20L;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (GameCombatant participant : room.getGame().getPlayers()) {
                if (participant instanceof GamePlayer gp) {
                    Hub.resetPlayer(gp.player());
                }
            }
            for (GamePlayer gp : room.getGame().getSpectators()) {
                Hub.resetPlayer(gp.player());
            }
        }, teleportDelay);

        Bukkit.getScheduler().runTaskLater(plugin, () -> removeGameRoom(room.getId()), teleportDelay + 100L);
    }

}
