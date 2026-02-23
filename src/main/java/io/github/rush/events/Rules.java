package io.github.rush.events;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;

import io.github.rush.Main;

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
