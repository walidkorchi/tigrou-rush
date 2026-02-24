package io.github.rush.events;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import io.github.rush.Main;
import io.github.rush.entities.MerchantType;

import java.util.function.Predicate;

public class Rules implements Listener {

    private final Main plugin;

    public Rules(Main plugin) {
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

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isGameEventRegistered(event.getBlock().getWorld().getName()))
            return;

        if (event.getBlock().getType() == Material.TNT)
            return;

        if (hasNearbyBlock(event.getBlock(), plugin.getConfig().getInt("bedProtectionRadius"),
                b -> b.getBlockData() instanceof Bed)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Cannot place blocks near beds!"));
        }
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (!isGameEventRegistered(event.getBed().getWorld().getName()))
            return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onVillagerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager))
            return;

        if (!isGameEventRegistered(villager.getWorld().getName()))
            return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager))
            return;

        if (!isGameEventRegistered(villager.getWorld().getName()))
            return;

        if (villager.getProfession() != org.bukkit.entity.Villager.Profession.LIBRARIAN)
            return;

        event.setCancelled(true);

        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Speed Merchant"));

        inv.setItem(0, new ItemStack(Material.IRON_SWORD));
        inv.setItem(1, new ItemStack(Material.IRON_CHESTPLATE));
        inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE));
        inv.setItem(9, new ItemStack(Material.SANDSTONE));

        event.getPlayer().openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player))
            return;

        if (!event.getView().getTitle().equals("Speed Merchant"))
            return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        MerchantType type = switch (slot) {
            case 0 -> MerchantType.WEAPONSMITH;
            case 1 -> MerchantType.ARMORSMITH;
            case 2 -> MerchantType.ALCHEMIST;
            case 9 -> MerchantType.BUILDER;
            default -> null;
        };

        if (type == null)
            return;

        Villager targetVillager = plugin.getMerchantVillager(type);
        if (targetVillager == null || targetVillager.isDead())
            return;

        player.closeInventory();

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                player.openMerchant(targetVillager, true);
            }
        };
        task.runTask(plugin);
    }

    private boolean hasNearbyBlock(final Block center, final int radius, final Predicate<Block> predicate) {
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
