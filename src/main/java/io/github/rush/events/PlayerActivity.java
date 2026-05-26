package io.github.rush.events;

import io.github.rush.Main;
import io.github.rush.commands.AuthorCommand;
import io.github.rush.game.Game;
import io.github.rush.entities.GameMannequin;
import io.github.rush.entities.GamePlayer;
import io.github.rush.replay.ReplayFollowGUI;
import io.github.rush.replay.ReplayPlayback;
import io.github.rush.replay.ReplayViewerInventory;
import io.github.rush.replay.ReplayViewerMenuGUI;
import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;
import io.github.rush.game.GameState;
import io.github.rush.abstracts.Team;
import io.github.rush.guis.HostPanelGUI;
import io.github.rush.sound.RushSounds;
import java.util.UUID;
import io.github.rush.guis.GUI;
import io.github.rush.guis.PlayerSettingsGUI;
import io.github.rush.guis.TeamSelectionGUI;
import io.github.rush.storage.PlayerLevelManager;
import io.github.rush.storage.PlayerLevelManager.PlayerLevel;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

public class PlayerActivity implements Listener {

    private final Main plugin;

    // tolerance for floating-point imprecision in position tracking
    private static final double EPSILON = 0.05;

    public PlayerActivity(Main plugin) {
        this.plugin = plugin;

        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sendActionBarToAll, 0L, 40L);
    }

    private void sendActionBarToAll() {
        for (GameRoom room : plugin.getGameManager().getAllGameRooms()) {
            GameRoom.sendReadyActionBar(room);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        event.joinMessage(Component.translatable("rush.chat_join", Component.text(player.getName())));
        plugin.getTablistManager().onPlayerJoin(player);

        // edge case: player reconnects after log off in a middle of a RUNNING game room
        final GameManager.ReconnectData reconnectData = plugin.getGameManager()
                .consumeReconnectData(player.getUniqueId());

        if (reconnectData != null) {
            final GameRoom room = plugin.getGameManager().getGameRoom(reconnectData.roomId());

            if (room != null && room.isRunning()) {
                plugin.getGameManager().addPlayerToGameRoom(player, room);
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.getGameManager().handleReconnect(player, room, reconnectData));
            } else {
                // edge case: game ended while offline
                plugin.getGameManager().resetPlayerHubState(player);
            }
        } else {
            plugin.getGameManager().resetPlayerHubState(player);
        }

        RushSounds.LOBBY_MUSIC.play(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();

        event.quitMessage(Component.translatable("rush.chat_quit", Component.text(player.getName())));

        if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
            plugin.getReplayManager().leaveReplay(player);
        }

        // TODO: stop music for player if needed

        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().removeScoreboard(player);
        }

        if (plugin.getTablistManager() != null) {
            plugin.getTablistManager().onPlayerQuit(player);
        }

        if (plugin.getPlayerSettingsManager() != null) {
            plugin.getPlayerSettingsManager().removePlayer(player.getUniqueId());
        }

        if (plugin.getGameManager() != null) {
            // edge case n1: host disconnect during game phase creation, we cancel the room
            plugin.getGameManager().getAllGameRooms().stream()
                    .filter(r -> r.getGame().getState() == GameState.CREATING
                            && player.getUniqueId().equals(r.getHostUUID()))
                    .findFirst()
                    .ifPresent(r -> plugin.getGameManager().cancelRoomCreation(r));

            // edge case n2: host disconnect during WAITING phase, we transfer host status
            plugin.getGameManager().getAllGameRooms().stream()
                    .filter(r -> r.getGame().getState() == GameState.WAITING
                            && player.getUniqueId().equals(r.getHostUUID()))
                    .findFirst()
                    .ifPresent(r -> {
                        final UUID nextHostUUID = GameRoom.nextHost(
                                r.getJoinOrder(),
                                player.getUniqueId(),
                                uuid -> Bukkit.getPlayer(uuid) != null);

                        if (nextHostUUID != null) {
                            final Player nextHost = Bukkit.getPlayer(nextHostUUID);

                            r.setHostUUID(nextHostUUID);
                            r.setHostName(nextHost.getName());

                            nextHost.getInventory().setItem(8, plugin.getGameManager().createHostPanelItem());
                            nextHost.sendMessage(Component.translatable("rush.room_host_transfer"));
                        } else {
                            plugin.getGameManager().removeGameRoom(r.getId());
                        }
                    });

            // take away host GUI from the disconnecting player if they had it
            final GameRoom room = plugin.getGameManager().getGameRoomOfPlayer(player);

            if (room != null) {
                // Snapshot in-game state so the player can be restored on reconnect
                if (room.isRunning()) {
                    final Game roomGame = room.getGame();
                    final Team team = roomGame.getPlayerTeam(new GamePlayer(player));
                    final boolean wasSpectator = roomGame.isSpectator(new GamePlayer(player));

                    plugin.getGameManager().recordDisconnect(player.getUniqueId(),
                            new GameManager.ReconnectData(
                                    room.getId(),
                                    team != null ? team.getColor().name() : null,
                                    wasSpectator));
                }

                room.removePlayer(player);
                plugin.getGameManager().removePlayerFromGameRoom(player);

                // edge case : room empty and waiting > room is removed
                if (room.getPlayerCount() == 0 && room.isWaiting()) {
                    plugin.getGameManager().removeGameRoom(room.getId());
                }
            }

        }

        plugin.setFastBoard(player, null);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled())
            return;

        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final Material blockType = block.getType();
        final String worldName = player.getWorld().getName();

        final GameRoom breakRoom = Main.getInstance().getGameManager().getGameRoomByWorld(worldName);

        if (breakRoom == null) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // sandstone/endstone are emancipated from island block protection logic
        if (blockType == Material.SANDSTONE || blockType == Material.END_STONE) {
            final Game game = breakRoom.getGame();

            if (game != null) {
                final Team breakerTeam = game.getPlayerTeam(new GamePlayer(player));

                // anti-spleef for same team players and mannequins
                for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation(), 2, 2, 2)) {
                    if (!(entity instanceof Player) && !(entity instanceof Mannequin)) {
                        continue;
                    }
                    if (entity.equals(player) || !isStandingOn(entity, block)) {
                        continue;
                    }

                    final Team entityTeam = game.getPlayerTeam(
                            entity instanceof Player p
                                    ? new GamePlayer(p)
                                    : new GameMannequin((Mannequin) entity));

                    if (breakerTeam != null && entityTeam != null && breakerTeam.equals(entityTeam)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            return;
        }

        event.setCancelled(true);
    }

    /**
     * Checks if an entity is physically standing
     * on a given block bounding boxes only
     */
    private boolean isStandingOn(Entity entity, Block block) {
        final BoundingBox entityBox = entity.getBoundingBox();
        final BoundingBox blockBox = block.getBoundingBox();

        // skip empty bounding box (no collision shape)
        if (blockBox.getVolume() == 0) {
            return false;
        }

        final double feetY = entityBox.getMinY();
        final double blockTopY = blockBox.getMaxY();

        if (Math.abs(feetY - blockTopY) > EPSILON) {
            return false;
        }

        // entity's hitbox must overlap the block horizontally
        // (handles edge-standing on up to 4 blocks)
        return entityBox.getMinX() < blockBox.getMaxX()
                && entityBox.getMaxX() > blockBox.getMinX()
                && entityBox.getMinZ() < blockBox.getMaxZ()
                && entityBox.getMaxZ() > blockBox.getMinZ();
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        final Player player = event.getPlayer();

        if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
            event.setCancelled(true);
            return;
        }

        if (isPlayerInQueue(player)) {
            event.setCancelled(true);
            return;
        }

        // prevents dropping armor during game
        if (isPlayerInGame(player)) {
            final ItemStack item = event.getItemDrop().getItemStack();

            if (isArmorItem(item.getType())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        sendActionBarToAll();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final String worldName = player.getWorld().getName();

        if (worldName.equals(plugin.getHubWorld()) && player.getLocation().getY() < 0) {
            final Location lobby = plugin.getMainLobby();

            if (lobby != null) {
                player.setFallDistance(0);
                player.teleport(lobby);
            }

            return;
        }

        final GameRoom room = plugin.getGameManager().getGameRoomByWorld(worldName);

        if (room != null && room.isRunning()) {
            final Game game = room.getGame();

            if (game != null && !game.isSpectator(new GamePlayer(player))) {
                rescueFromVoid(player, game, room.getIslandY());
            }
        }
    }

    private void rescueFromVoid(Player player, Game game, int islandY) {
        final double voidThreshold = islandY - Main.getInstance().getVoidThreshold();

        if (player.getLocation().getY() < voidThreshold) {
            player.setFallDistance(0);
            player.setHealth(0);
            // PlayerDeathEvent fires: onPlayerDie cancels the death screen,
            // restores health/food, and calls handleEntityDeath.
        }
    }

    @EventHandler
    public void onPlayerDie(PlayerDeathEvent pd) {
        final Player player = pd.getEntity();
        final Game game = Main.getInstance().getGameManager().getGameForPlayer(player);

        if (game == null || game.getState() != GameState.RUNNING)
            return;

        pd.setCancelled(true);
        pd.setDroppedExp(0);
        pd.deathMessage(null);

        // TODO: use in resetPlayerHealth
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        Player killer = player.getKiller();

        handleEntityDeath(game, player, killer);
    }

    @EventHandler
    public void onMannequinDie(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Mannequin mannequin)) {
            return;
        }

        GameRoom room = Main.getInstance().getGameManager().getGameRoomByWorld(mannequin.getWorld().getName());
        if (room == null)
            return;
        Game game = room.getGame();

        if (game == null || game.getPlayerTeam(new GameMannequin(mannequin)) == null) {
            return;
        }

        event.setCancelled(true);
        event.setDroppedExp(0);
        event.getDrops().clear();

        // Restore health immediately so the entity survives the cancelled death.
        mannequin.setHealth(mannequin.getAttribute(Attribute.MAX_HEALTH).getValue());

        final Player killer = mannequin.getKiller();
        // Schedule 1 tick out: teleporting inside EntityDeathEvent is silently dropped
        // even when the event is cancelled, because the entity is still in dying state.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (mannequin.isDead())
                return;
            game.handleMannequinDeath(mannequin, killer);
        }, 1L);
    }

    private void handleEntityDeath(Game game, Entity entity, Player killer) {
        game.onPlayerDeath(new GamePlayer((Player) entity), killer);

        Team team = game.getPlayerTeam(new GamePlayer((Player) entity));
        boolean bedDestroyed = team != null && team.isBedDestroyed();

        if (!bedDestroyed && team != null) {
            Location bedLoc = team.getBedLocation();
            Location spawn = bedLoc != null
                    ? new Location(bedLoc.getWorld(), bedLoc.getX() + 0.5, bedLoc.getY() + 1, bedLoc.getZ() + 0.5)
                    : team.getSpawnLocation();

            if (spawn != null) {
                entity.teleport(spawn);
            }

            game.equipEntity(new GamePlayer((Player) entity), team);

            if (entity instanceof Player player) {
                game.addProtection(player);
            }
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isPlayerInQueue(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent pie) {
        if (pie.getAction() != Action.PHYSICAL && pie.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = pie.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Replay viewer: route hotbar actions and block all hub logic
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
            if (pie.getAction() == Action.RIGHT_CLICK_AIR || pie.getAction() == Action.RIGHT_CLICK_BLOCK) {
                pie.setCancelled(true);
                ReplayPlayback playback = plugin.getReplayManager().getPlayback(player);
                if (playback == null)
                    return;
                int slot = player.getInventory().getHeldItemSlot();
                if (ReplayViewerInventory.isPauseResumeDye(item)) {
                    playback.togglePause();
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f,
                            playback.isPaused() ? 0.8f : 1.2f);
                } else if (item != null && item.getType() == Material.COMPASS) {
                    if (playback.getFollowTarget(player.getUniqueId()) != null) {
                        playback.clearFollowTarget(player.getUniqueId());
                    } else {
                        ReplayFollowGUI.open(player, playback);
                    }
                } else if (item != null && item.getType() == Material.PLAYER_HEAD) {
                    if (slot == ReplayViewerInventory.SLOT_REWIND) {
                        playback.seek(Math.max(0, playback.getPlayheadMs() - 5000));
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.7f);
                    } else if (slot == ReplayViewerInventory.SLOT_FORWARD) {
                        playback.seek(Math.min(playback.getDurationMs(), playback.getPlayheadMs() + 5000));
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.3f);
                    } else if (slot == ReplayViewerInventory.SLOT_SPEED_DOWN) {
                        playback.stepSpeedDown();
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                    } else if (slot == ReplayViewerInventory.SLOT_SPEED_UP) {
                        playback.stepSpeedUp();
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
                    }
                } else if (item != null && item.getType() == Material.NETHER_STAR) {
                    ReplayViewerMenuGUI.open(player, playback);
                }
            }
            return;
        }

        if (pie.getAction() == Action.RIGHT_CLICK_AIR || pie.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (item != null && item.getType() == Material.COMPASS) {
                // Spectator compass: return to lobby (GameRoom or legacy game)
                Game spectatorGame = Main.getInstance().getGameManager().getGameForPlayer(player);
                if (spectatorGame != null && spectatorGame.isSpectator(new GamePlayer(player))) {
                    spectatorGame.removeSpectator(new GamePlayer(player));
                    GameRoom spectatorRoom = plugin.getGameManager().getGameRoomOfPlayer(player);
                    if (spectatorRoom != null) {
                        plugin.getGameManager().removePlayerFromGameRoom(player);
                    }
                    pie.setCancelled(true);
                    return;
                }
                // Open game listing GUI
                Main.getInstance().getGameManager().openGameList(player);
                pie.setCancelled(true);
                return;
            }

            if (item != null && item.getType() == Material.BEACON) {
                Main.getInstance().getGameManager().openHostConfigGUI(player);
                pie.setCancelled(true);
                return;
            }

            if (item != null && item.getType() == Material.NETHER_STAR) {
                GameRoom hostRoom = plugin.getGameManager().getGameRoomOfPlayer(player);
                if (hostRoom != null && player.getUniqueId().equals(hostRoom.getHostUUID())) {
                    HostPanelGUI.open(player, hostRoom, plugin.getGameManager());
                    pie.setCancelled(true);
                    return;
                }
            }

            if (item != null && item.getType() == Material.WHITE_BANNER) {
                TeamSelectionGUI.openTeamSelection(player);
                pie.setCancelled(true);
                return;
            }

            if (item != null && item.getType() == Material.SLIME_BALL) {
                TeamSelectionGUI.openLeaveTeamMenu(player);
                pie.setCancelled(true);
                return;
            }

            if (item != null && (item.getType() == Material.LIME_DYE || item.getType() == Material.RED_DYE)) {
                TeamSelectionGUI.toggleReady(player);
                pie.setCancelled(true);
                return;
            }

            if (item != null && item.getType() == Material.REPEATER) {
                PlayerSettingsGUI.openPlayerSettings(player);
                pie.setCancelled(true);
                return;
            }

            Block clickedBlock = pie.getClickedBlock();
            if (clickedBlock != null && isGlass(clickedBlock.getType())) {
                Location blockLoc = clickedBlock.getLocation();
                for (Location glassLoc : plugin.getCommandManager().getAuthorCommand().getGlassBlocks().keySet()) {
                    if (blockLoc.getWorld().equals(glassLoc.getWorld())
                            && blockLoc.getBlockX() == glassLoc.getBlockX()
                            && blockLoc.getBlockY() == glassLoc.getBlockY()
                            && blockLoc.getBlockZ() == glassLoc.getBlockZ()) {
                        AuthorCommand.playCookieFountain(blockLoc.add(0.5, 0.5, 0.5));
                        pie.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // Block interactive block access in hub for non-OP players
        if (isHubPlayer(player) && !player.isOp()) {
            Block block = pie.getClickedBlock();
            if (pie.getAction() == Action.RIGHT_CLICK_BLOCK && block != null && isHubRestrictedBlock(block)) {
                pie.setCancelled(true);
                return;
            }
        }

        // Always prevent crop trampling in hub for non-OP players, regardless of game
        // state
        if (isHubPlayer(player) && !player.isOp() && pie.getAction() == Action.PHYSICAL) {
            pie.setCancelled(true);
            return;
        }

        // Block interactive block access and crop trampling in WAITING GameRooms
        if (!player.isOp()) {
            GameRoom interactRoom = plugin.getGameManager().getGameRoomByWorld(player.getWorld().getName());
            if (interactRoom != null && interactRoom.isWaiting()) {
                if (pie.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    Block block = pie.getClickedBlock();
                    if (block != null && isHubRestrictedBlock(block)) {
                        pie.setCancelled(true);
                        return;
                    }
                } else if (pie.getAction() == Action.PHYSICAL) {
                    pie.setCancelled(true);
                    return;
                }
            }
        }

    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getInventory().getHolder() instanceof GUI gui) {
            event.setCancelled(true);
            gui.onClick(player, event.getRawSlot(), event.getClick());
            return;
        }

        if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
            event.setCancelled(true);
            return;
        }

        if (isPlayerInQueue(player)) {
            event.setCancelled(true);
            return;
        }

        // Disable 2x2 crafting grid while in game
        if (isPlayerInGame(player)) {
            if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
                if (event.getRawSlot() >= 0 && event.getRawSlot() <= 4) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Prevent taking off armor during game
        if (isPlayerInGame(player)) {
            // Check if clicking on armor slots
            if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }
            // Check if shift-clicking armor into inventory (trying to unequip)
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem != null && isArmorItem(currentItem.getType())) {
                    event.setCancelled(true);
                    return;
                }
            }
            // Prevent dropping armor via click
            if (event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                    event.getAction() == InventoryAction.DROP_ALL_SLOT) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem != null && isArmorItem(currentItem.getType())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInvInteract(InventoryInteractEvent event) {
        if ((event.getWhoClicked() instanceof Player player)) {
            if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
                event.setCancelled(true);
                return;
            }
            if (isPlayerInQueue(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
            event.setCancelled(true);
            return;
        }
        if (isPlayerInQueue(player)) {
            event.setCancelled(true);
        }
    }

    private boolean isPlayerInQueue(Player player) {
        if (player.isOp())
            return false;

        GameRoom queueRoom = plugin.getGameManager().getGameRoomByWorld(player.getWorld().getName());
        return queueRoom != null && queueRoom.isWaiting();
    }

    private boolean isHubPlayer(Player player) {
        return player.getWorld().getName().equals(plugin.getHubWorld());
    }

    private boolean isPlayerInGame(Player player) {
        if (plugin.getGameManager() == null)
            return false;
        GameRoom room = plugin.getGameManager().getGameRoomByWorld(player.getWorld().getName());
        return room != null && room.isRunning();
    }

    private boolean isArmorItem(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") ||
                name.endsWith("_CHESTPLATE") ||
                name.endsWith("_LEGGINGS") ||
                name.endsWith("_BOOTS");
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        Entity dmgEntity = event.getEntity();

        // Cancel all damage in WAITING GameRooms (players and mannequins)
        if (dmgEntity instanceof Player || dmgEntity instanceof Mannequin) {
            GameRoom waitRoom = plugin.getGameManager().getGameRoomByWorld(dmgEntity.getWorld().getName());
            if (waitRoom != null && waitRoom.isWaiting()) {
                event.setCancelled(true);
                return;
            }
        }

        if (!(dmgEntity instanceof Player player)) {
            return;
        }

        // Hub players are always invulnerable
        if (isHubPlayer(player)) {
            event.setCancelled(true);
            return;
        }

        Game game = Main.getInstance().getGameManager().getGameForPlayer(player);
        if (game != null && game.isProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof Player) && !(victim instanceof Mannequin)) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        Game game = plugin.getGameManager().getGameForPlayer(attacker);
        if (game == null) {
            return;
        }

        if (game.isProtected(attacker)) {
            event.setCancelled(true);
            return;
        }

        Team victimTeam = game.getPlayerTeam(
                victim instanceof Player p
                        ? new GamePlayer(p)
                        : new GameMannequin((Mannequin) victim));
        Team attackerTeam = game.getPlayerTeam(new GamePlayer(attacker));

        if (victimTeam != null && victimTeam.equals(attackerTeam)) {
            event.setCancelled(true);
            return;
        }

        if (victim instanceof Player playerVictim) {
            game.getKillTracker().recordDamage(playerVictim, attacker, event.getFinalDamage());
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // World-scope: only same-world players receive the message
        event.viewers().removeIf(
                audience -> audience instanceof Player viewer && !viewer.getWorld().equals(player.getWorld()));

        PlayerLevelManager levelManager = Main.getInstance().getPlayerLevelManager();
        PlayerLevel playerLevel = levelManager.loadPlayerLevel(player.getUniqueId());

        Component rankComponent = MiniMessage.miniMessage().deserialize(playerLevel.getFormattedRank());
        Component rankBadge = Component.text("[", NamedTextColor.GRAY)
                .append(rankComponent)
                .append(Component.text("]", NamedTextColor.GRAY));

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean isGlobal = message.startsWith("@");
        if (isGlobal) {
            message = message.substring(1).trim();
        }

        Component tail = Component.text(" > ", NamedTextColor.WHITE)
                .append(Component.text(message, NamedTextColor.WHITE));

        Component formatComponent;

        if (isPlayerInQueue(player)) {
            formatComponent = rankBadge
                    .append(Component.text(" [", NamedTextColor.GRAY))
                    .append(Component.text("Lobby", NamedTextColor.BLUE))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(player.displayName())
                    .append(tail);
        } else {
            Game game = Main.getInstance().getGameManager().getGameForPlayer(player);
            Team team = (game != null && game.getState() == GameState.RUNNING)
                    ? game.getPlayerTeam(new GamePlayer(player))
                    : null;

            if (team != null) {
                Team.Color color = team.getColor();

                formatComponent = rankBadge
                        .append(Component.text(" [", NamedTextColor.GRAY))
                        .append(Component.text(color.name(), color.getTextColor()))
                        .append(Component.text("] ", NamedTextColor.GRAY))
                        .append(player.displayName())
                        .append(tail);

                if (!isGlobal) {
                    event.setCancelled(true);
                    for (Player recipient : plugin.getServer().getOnlinePlayers()) {
                        Team recipientTeam = game.getPlayerTeam(new GamePlayer(recipient));
                        if (recipientTeam != null && recipientTeam.equals(team)) {
                            recipient.sendMessage(formatComponent);
                        }
                    }
                    return;
                }
            } else {
                formatComponent = rankBadge
                        .append(Component.text(" ", NamedTextColor.WHITE))
                        .append(player.displayName())
                        .append(tail);
            }
        }

        event.renderer((source, sourceDisplayName, msg, viewer) -> formatComponent);
    }

    private boolean isGlass(Material material) {
        String name = material.name();
        return material == Material.GLASS
                || material == Material.TINTED_GLASS
                || name.endsWith("_STAINED_GLASS");
    }

    private boolean isHubRestrictedBlock(Block block) {
        Material material = block.getType();
        return block.getState() instanceof Container
                || Tag.DOORS.isTagged(material)
                || Tag.TRAPDOORS.isTagged(material);
    }
}
