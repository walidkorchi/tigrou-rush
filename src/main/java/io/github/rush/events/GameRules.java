package io.github.rush.events;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.menus.ShopGUI;

import java.util.Set;
import java.util.function.Predicate;

public class GameRules implements Listener {

    private final Main plugin;

    public GameRules(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if the game has started and the given world is the game world.
     *
     * @param worldName the world name to check
     * @return true if game is started and world matches game world, false otherwise
     */
    public boolean isGameEventRegistered(String worldName) {
        if (!plugin.isGameStarted()) {
            return false;
        }
        return worldName.equals(plugin.getGameWorld());
    }

    private static final Set<Material> PLACEABLE_BLOCKS = Set.of(
            Material.SANDSTONE, Material.END_STONE, Material.TNT);

    private static final Set<Material> BREAKABLE_BLOCKS = Set.of(
            Material.SANDSTONE, Material.END_STONE);

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isGameEventRegistered(event.getBlock().getWorld().getName()))
            return;

        if (!PLACEABLE_BLOCKS.contains(event.getBlock().getType())) {
            event.setCancelled(true);
            return;
        }

        if (event.getBlock().getType() != Material.TNT
                && hasNearbyBlock(event.getBlock(), plugin.getConfig().getInt("bedProtectionRadius"),
                        b -> b.getBlockData() instanceof Bed)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Cannot place blocks near beds!"));
            return;
        }

        Game game = plugin.getGameManager().getCurrentGame();
        if (game != null && game.getState() == GameState.RUNNING
                && game.isBlockInForbiddenZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    Component.text("§cVous ne pouvez pas placer de blocs dans cette zone avant l'overtime!"));
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isGameEventRegistered(event.getBlock().getWorld().getName()))
            return;

        if (!BREAKABLE_BLOCKS.contains(event.getBlock().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (!isGameEventRegistered(event.getBed().getWorld().getName()))
            return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onBedInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Block block = event.getClickedBlock();
        if (block == null || !(block.getBlockData() instanceof Bed))
            return;

        if (!isGameEventRegistered(block.getWorld().getName()))
            return;

        event.setCancelled(true);
    }

    // Removed legacy onVillagerInteract — merchant interaction is handled by
    // onPlayerInteractEntity

    @EventHandler
    public void onCraft(CraftItemEvent cie) {
        Player player = (Player) cie.getWhoClicked();
        Game game = Main.getInstance().getGameManager().getGameOfPlayer(player);

        if (game == null) {
            return;
        }

        if (game.getState() == GameState.STOPPED) {
            return;
        }

        cie.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        Player player = event.getPlayer();

        String gameWorld = Main.getInstance().getGameWorld();
        if (gameWorld == null || !player.getWorld().getName().equals(gameWorld)) {
            return;
        }

        Game game = Main.getInstance().getGameManager().getCurrentGame();
        if (game == null || game.getState() != GameState.RUNNING) {
            return;
        }

        if (!Main.getInstance().isMerchantVillager(villager)) {
            return;
        }

        event.setCancelled(true);

        if (Main.getInstance().isSpeedMerchantVillager(villager)) {
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                player.openMerchant(villager, true);
            });
        } else {
            ShopGUI.openMainMenu(player);
        }
    }

    @EventHandler
    public void onVillagerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager villager))
            return;

        if (!plugin.isMerchantVillager(villager))
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
}
