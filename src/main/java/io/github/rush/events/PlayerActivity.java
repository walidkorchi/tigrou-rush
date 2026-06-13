package io.github.rush.events;

import io.github.rush.Main;
import io.github.rush.abstracts.ActionBar;
import io.github.rush.commands.AuthorCommand;
import io.github.rush.game.Game;
import io.github.rush.entities.GameMannequin;
import io.github.rush.entities.GamePlayer;
import io.github.rush.replay.ReplayPlayback;
import io.github.rush.inventories.ReplayViewerInventory;
import io.github.rush.inventories.GamePlayerInventory;
import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;
import io.github.rush.game.GameState;
import io.github.rush.Hub;
import io.github.rush.abstracts.Team;
import io.github.rush.guis.AnvilInputGUI;
import io.github.rush.guis.HostPanelGUI;
import io.github.rush.utils.Sounds;
import java.util.UUID;
import io.github.rush.guis.PlayerSettingsGUI;
import io.github.rush.guis.ReplayFollowGUI;
import io.github.rush.guis.ReplayViewerMenuGUI;
import io.github.rush.guis.TeamSelectionGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class PlayerActivity implements Listener {

    private final Main plugin;

    public PlayerActivity(Main plugin) {
        this.plugin = plugin;
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
                Hub.resetPlayer(player);
            }
        } else {
            Hub.resetPlayer(player);
        }

        Sounds.LOBBY_MUSIC.play(player);
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

                            nextHost.getInventory().setItem(GamePlayerInventory.SLOT_HOST_PANEL,
                                    GamePlayerInventory.createHostPanelItem());
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

                // edge case: room empty and waiting > room is removed
                if (room.getPlayerCount() == 0 && room.isWaiting()) {
                    plugin.getGameManager().removeGameRoom(room.getId());
                }
            }

        }

        plugin.setFastBoard(player, null);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        final Player player = event.getPlayer();

        // prevents dropping replay controls from inventory
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
            event.setCancelled(true);
            return;
        }

        // prevents dropping team selection or host items
        // from inventory when player is in queue in waiting lobby room
        if (plugin.getGameManager().isPlayerInWaitingRoom(player)) {
            event.setCancelled(true);
            return;
        }

        // prevents dropping armor during game
        if (plugin.getGameManager().isPlayerInRunningGame(player)
                && Hub.isUndroppableArmor(event.getItemDrop().getItemStack().getType())) {
            event.setCancelled(true);
            return;
        }

        if (!player.isOp() && Hub.isAtHub(player)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        ActionBar.sendReadyToAll();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final String worldName = player.getWorld().getName();

        if (Hub.isAtHub(player) && player.getLocation().getY() < 0) {
            player.setFallDistance(0);
            player.teleport(plugin.getMainLobby());
            return;
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

        handleEntityDeath(game, player);
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

    private void handleEntityDeath(Game game, Entity entity) {
        final Player player = (Player) entity;
        final Player killer = player.getKiller();

        game.onPlayerDeath(new GamePlayer(player), killer);

        Team team = game.getPlayerTeam(new GamePlayer(player));
        boolean bedDestroyed = team != null && team.isBedDestroyed();

        if (!bedDestroyed && team != null) {
            Location bedLoc = team.getBedLocation();
            Location spawn = bedLoc != null
                    ? new Location(bedLoc.getWorld(), bedLoc.getX() + 0.5, bedLoc.getY() + 1, bedLoc.getZ() + 0.5)
                    : team.getSpawnLocation();

            if (spawn != null) {
                entity.teleport(spawn);
                entity.setVelocity(new Vector(0, 0, 0));
            }

            game.equipEntity(new GamePlayer(player), team);

            if (entity instanceof Player p) {
                game.addProtection(p);
            }
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.getGameManager().isPlayerInWaitingRoom(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        // The vanilla XP bar is repurposed for rank display — block all natural XP gain
        // so that orb pickups and giveExp() calls never overwrite setLevel()/setExp().
        event.setAmount(0);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Replay viewer: route hotbar actions and block all hub logic
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
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

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (item != null && item.getType() == Material.COMPASS) {
                // Spectator compass: return to lobby (GameRoom or legacy game)
                Game spectatorGame = Main.getInstance().getGameManager().getGameForPlayer(player);
                if (spectatorGame != null && spectatorGame.isSpectator(new GamePlayer(player))) {
                    spectatorGame.removeSpectator(new GamePlayer(player));
                    GameRoom spectatorRoom = plugin.getGameManager().getGameRoomOfPlayer(player);
                    if (spectatorRoom != null) {
                        plugin.getGameManager().removePlayerFromGameRoom(player);
                    }
                    event.setCancelled(true);
                    return;
                }
                // Open game listing GUI
                Main.getInstance().getGameManager().openGameList(player);
                event.setCancelled(true);
                return;
            }

            if (item != null && item.getType() == Material.BEACON) {
                Main.getInstance().getGameManager().openHostConfigGUI(player);
                event.setCancelled(true);
                return;
            }

            if (item != null && item.getType() == Material.NETHER_STAR) {
                GameRoom hostRoom = plugin.getGameManager().getGameRoomOfPlayer(player);
                if (hostRoom != null && player.getUniqueId().equals(hostRoom.getHostUUID())) {
                    HostPanelGUI.open(player, hostRoom, plugin.getGameManager());
                    event.setCancelled(true);
                    return;
                }
            }

            if (item != null && item.getType() == Material.WHITE_BANNER) {
                TeamSelectionGUI.openTeamSelection(player);
                event.setCancelled(true);
                return;
            }

            if (item != null && item.getType() == Material.SLIME_BALL) {
                TeamSelectionGUI.openLeaveTeamMenu(player);
                event.setCancelled(true);
                return;
            }

            if (item != null && (item.getType() == Material.LIME_DYE || item.getType() == Material.RED_DYE)) {
                TeamSelectionGUI.toggleReady(player);
                event.setCancelled(true);
                return;
            }

            if (item != null && item.getType() == Material.REPEATER) {
                PlayerSettingsGUI.openPlayerSettings(player);
                event.setCancelled(true);
                return;
            }

            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && isGlass(clickedBlock.getType())) {
                Location blockLoc = clickedBlock.getLocation();
                for (Location glassLoc : plugin.getCommandManager().getAuthorCommand().getGlassBlocks().keySet()) {
                    if (blockLoc.getWorld().equals(glassLoc.getWorld())
                            && blockLoc.getBlockX() == glassLoc.getBlockX()
                            && blockLoc.getBlockY() == glassLoc.getBlockY()
                            && blockLoc.getBlockZ() == glassLoc.getBlockZ()) {
                        AuthorCommand.playCookieFountain(blockLoc.add(0.5, 0.5, 0.5));
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        final GameRoom interactRoom = plugin.getGameManager().getGameRoomByWorld(player.getWorld().getName());

        // prevent crop trampling + block interaction both
        // in hub and WAITING GameRooms for non-OP players, regardless of game state
        if (!player.isOp() && (interactRoom != null && interactRoom.isWaiting()) || Hub.isAtHub(player)) {
            if (event.getAction() == Action.PHYSICAL || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        // Anvil input capture must run before the waiting-room guard so hosts
        // can interact with the rename field while in a game room.
        if (AnvilInputGUI.tryConsume(player, event))
            return;

        if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
            event.setCancelled(true);
            return;
        }

        if (Hub.isAtHub(player)) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getGameManager().isPlayerInWaitingRoom(player)) {
            event.setCancelled(true);
            return;
        }

        // disables 2x2 crafting grid in the entire server
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            if (event.getRawSlot() >= 0 && event.getRawSlot() <= 4) {
                event.setCancelled(true);
                return;
            }
        }

        // prevents taking off undropable armor during a game
        if (plugin.getGameManager().isPlayerInRunningGame(player)) {
            // edge case n1: if clicking on armor slots
            if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }

            // edge case n2: if shift-clicking armor into inventory (trying to unequip)
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem != null && Hub.isUndroppableArmor(currentItem.getType())) {
                    event.setCancelled(true);
                    return;
                }
            }

            // edge case n3: dropping armor via click
            if (event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                    event.getAction() == InventoryAction.DROP_ALL_SLOT) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem != null && Hub.isUndroppableArmor(currentItem.getType())) {
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
            if (plugin.getGameManager().isPlayerInWaitingRoom(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
                event.setCancelled(true);
                return;
            }
            if (Hub.isAtHub(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (Hub.isAtHub(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        final Entity dmgEntity = event.getEntity();

        if (dmgEntity instanceof Player player) {
            if (Hub.isAtHub(player)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        final Player player = (Player) event.getWhoClicked();
        final Game game = Main.getInstance().getGameManager().getGameForPlayer(player);

        if (game != null && game.getState() == GameState.STOPPED)
            return;

        event.setCancelled(true);
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
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;
        if (!(event.getView() instanceof AnvilView anvilView))
            return;
        // Clear the input slot so Bukkit doesn't return it to the player's inventory.
        anvilView.getTopInventory().setItem(0, null);
        AnvilInputGUI.cancel(player);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (event.getView() instanceof AnvilView anvilView
                && anvilView.getPlayer() instanceof Player player
                && AnvilInputGUI.hasPending(player)) {
            anvilView.setRepairCost(0);
        }
    }

    private boolean isGlass(Material material) {
        String name = material.name();
        return material == Material.GLASS
                || material == Material.TINTED_GLASS
                || name.endsWith("_STAINED_GLASS");
    }
}
