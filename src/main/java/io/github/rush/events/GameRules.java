package io.github.rush.events;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.MenuType;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BoundingBox;

import io.github.rush.Main;
import io.github.rush.abstracts.Team;
import io.github.rush.entities.GameMannequin;
import io.github.rush.entities.GamePlayer;
import io.github.rush.entities.Merchant;
import io.github.rush.entities.MerchantType;
import org.bukkit.Bukkit;
import io.github.rush.game.Game;
import io.github.rush.game.GameRoom;
import io.github.rush.game.GameState;
import io.github.rush.guis.ShopGUI;

import java.util.Set;
import java.util.function.Predicate;

public class GameRules implements Listener {

    private final Main plugin;

    // tolerance for floating-point imprecision in position tracking
    private static final double EPSILON = 0.05;

    public GameRules(Main plugin) {
        this.plugin = plugin;
    }

    private static final Set<Material> PLACEABLE_BLOCKS = Set.of(
            Material.SANDSTONE, Material.END_STONE, Material.TNT);

    static final Set<Material> BREAKABLE_BLOCKS = Set.of(
            Material.SANDSTONE, Material.END_STONE, Material.TNT);

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        final Game game = getRunningGameForWorld(event.getBlock().getWorld().getName());

        if (game == null)
            return;

        if (!PLACEABLE_BLOCKS.contains(event.getBlock().getType())) {
            event.setCancelled(true);
            return;
        }

        if (!game.isBlockInRingPath(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        if (game.isBlockOnResourceSpawn(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    Component.translatable("rush.blockPlaceOnResourceSpawn"));
            return;
        }

        if (game.isBlockNearRegularMerchant(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    Component.translatable("rush.blockPlaceNearRegularMerchant"));
            return;
        }

        if (event.getBlock().getType() != Material.TNT
                && hasNearbyBlock(event.getBlock(), plugin.getConfig().getInt("bedProtectionRadius"),
                        b -> b.getBlockData() instanceof Bed)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.translatable("rush.blockPlaceNearBeds"));
            return;
        }

        if (game.isBlockInForbiddenZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    Component.translatable("rush.blockPlaceNearForbiddenZone"));
        }
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

        if (breakRoom == null || player.getGameMode() != GameMode.SURVIVAL)
            return;

        // anti-spleef for same team players and mannequins
        if (BREAKABLE_BLOCKS.contains(blockType)) {
            final Game game = breakRoom.getGame();

            if (game != null) {
                final Team breakerTeam = game.getPlayerTeam(new GamePlayer(player));

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

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        final Player player = event.getPlayer();

        if (plugin.getReplayManager() != null && plugin.getReplayManager().isWatching(player)) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getGameManager().isPlayerInWaitingRoom(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        final Entity dmgEntity = event.getEntity();

        // Cancel all damage in WAITING GameRooms (players and mannequins)
        if (dmgEntity instanceof Player || dmgEntity instanceof Mannequin) {
            final GameRoom waitRoom = plugin.getGameManager().getGameRoomByWorld(dmgEntity.getWorld().getName());

            if (waitRoom != null && waitRoom.isWaiting()) {
                event.setCancelled(true);
                return;
            }
        }

        if (dmgEntity instanceof Player player) {
            final Game game = Main.getInstance().getGameManager().getGameForPlayer(player);

            if (game != null && game.isProtected(player))
                event.setCancelled(true);

            return;
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final String worldName = player.getWorld().getName();
        final GameRoom room = plugin.getGameManager().getGameRoomByWorld(worldName);

        if (room != null) {
            if (room.isRunning()) {
                final Game game = room.getGame();

                if (game != null && !game.isSpectator(new GamePlayer(player))) {
                    final int voidDeathThreshold = (room.getIslandY() - Main.getInstance().getVoidThreshold());

                    if (player.getLocation().getY() < voidDeathThreshold) {
                        player.setFallDistance(0);
                        player.setHealth(0);
                    }
                }
            } else if (room.isWaiting()) {
                if (player.getLocation().getY() < 0) {
                    player.setFallDistance(0);
                    player.teleport(room.getLobbyLocation());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (getRunningGameForWorld(event.getBed().getWorld().getName()) == null)
            return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onBedInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        final Block block = event.getClickedBlock();

        if (block == null || !(block.getBlockData() instanceof Bed))
            return;
        if (getRunningGameForWorld(block.getWorld().getName()) == null)
            return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        final Player player = event.getPlayer();

        if (getRunningGameForWorld(player.getWorld().getName()) == null) {
            return;
        }

        if (!Merchant.isMerchant(villager)) {
            return;
        }

        event.setCancelled(true);

        final MerchantType type = Merchant.getType(villager);

        if (type == MerchantType.SPEED) {
            ShopGUI.openMainMenu(player);
        } else if (type != null) {
            final org.bukkit.inventory.Merchant merchant = Merchant.createBukkitMerchant(type);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.openInventory(MenuType.MERCHANT
                        .builder()
                        .title(Component.translatable(Merchant.typeKey(type)))
                        .merchant(merchant)
                        .build(player));
            });
        }
    }

    @EventHandler
    public void onVillagerDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled())
            return;
        if (!(event.getEntity() instanceof Villager villager))
            return;
        if (!Merchant.isMerchant(villager))
            return;

        event.setCancelled(true);
    }

    private boolean hasNearbyBlock(Block center, int radius, Predicate<Block> predicate) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0)
                        continue;
                    if (predicate.test(center.getRelative(x, y, z))) {
                        return true;
                    }
                }
            }
        }

        return false;
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

    private Game getRunningGameForWorld(String worldName) {
        final GameRoom room = plugin.getGameManager().getGameRoomByWorld(worldName);

        if (room != null && room.getGame() != null && room.getGame().getState() == GameState.RUNNING)
            return room.getGame();
        else return null;
    }

}
