package io.github.rush.events;

import net.kyori.adventure.text.Component;
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
import io.github.rush.entities.Merchant;
import io.github.rush.entities.MerchantType;
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

    private static final Set<Material> PLACEABLE_BLOCKS = Set.of(
            Material.SANDSTONE, Material.END_STONE, Material.TNT);

    private static final Set<Material> BREAKABLE_BLOCKS = Set.of(
            Material.SANDSTONE, Material.END_STONE);

    private Game getRunningGameForWorld(String worldName) {
        if (plugin.isGameStarted() && worldName.equals(plugin.getGameWorld())) {
            Game game = plugin.getGameManager().getCurrentGame();
            if (game != null && game.getState() == GameState.RUNNING)
                return game;
        }
        io.github.rush.game.GameRoom room = plugin.getGameManager().getGameRoomByWorld(worldName);
        if (room != null && room.getGame() != null && room.getGame().getState() == GameState.RUNNING)
            return room.getGame();
        return null;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Game game = getRunningGameForWorld(event.getBlock().getWorld().getName());
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

        if (event.getBlock().getType() != Material.TNT
                && hasNearbyBlock(event.getBlock(), plugin.getConfig().getInt("bedProtectionRadius"),
                        b -> b.getBlockData() instanceof Bed)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Cannot place blocks near beds!"));
            return;
        }

        if (game.isBlockInForbiddenZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    Component.text("§cVous ne pouvez pas placer de blocs dans cette zone avant l'overtime!"));
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (getRunningGameForWorld(event.getBlock().getWorld().getName()) == null)
            return;

        if (!BREAKABLE_BLOCKS.contains(event.getBlock().getType())) {
            event.setCancelled(true);
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

        Block block = event.getClickedBlock();
        if (block == null || !(block.getBlockData() instanceof Bed))
            return;

        if (getRunningGameForWorld(block.getWorld().getName()) == null)
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
        if (getRunningGameForWorld(player.getWorld().getName()) == null) {
            return;
        }

        if (!Merchant.isMerchant(villager)) {
            return;
        }

        event.setCancelled(true);

        MerchantType type = Merchant.getType(villager);
        if (type == MerchantType.SPEED) {
            ShopGUI.openMainMenu(player);
        } else if (type != null) {
            ShopGUI.Category category = switch (type) {
                case WEAPONSMITH -> ShopGUI.Category.WEAPONS;
                case BUILDER -> ShopGUI.Category.BLOCKS;
                case ALCHEMIST -> ShopGUI.Category.POTIONS;
                case ARMORSMITH -> ShopGUI.Category.ARMOR;
                default -> null;
            };
            if (category != null) {
                ShopGUI.openCategory(player, category);
            }
        }
    }

    @EventHandler
    public void onVillagerDamage(EntityDamageByEntityEvent event) {
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
}
