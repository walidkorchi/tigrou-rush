package io.github.rush.replay;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ReplayViewerInventory {

    public static final int SLOT_COMPASS = 0;
    public static final int SLOT_SPEED_DOWN = 1;
    public static final int SLOT_REWIND = 2;
    public static final int SLOT_PAUSE_RESUME = 3;
    public static final int SLOT_FORWARD = 4;
    public static final int SLOT_SPEED_UP = 5;
    public static final int SLOT_MENU = 6;

    private ReplayViewerInventory() {}

    public static void give(Player player, boolean isPaused, double speedMultiplier) {
        player.getInventory().clear();
        player.getInventory().setItem(SLOT_COMPASS, buildCompass());
        player.getInventory().setItem(SLOT_SPEED_DOWN, buildSpeedDown(speedMultiplier));
        player.getInventory().setItem(SLOT_REWIND, buildRewind());
        player.getInventory().setItem(SLOT_PAUSE_RESUME, buildPauseResume(isPaused));
        player.getInventory().setItem(SLOT_FORWARD, buildForward());
        player.getInventory().setItem(SLOT_SPEED_UP, buildSpeedUp(speedMultiplier));
        player.getInventory().setItem(SLOT_MENU, buildMenu());
    }

    private static String formatSpeed(double speed) {
        return (speed == Math.floor(speed)) ? String.format("%.1f×", speed) : speed + "×";
    }

    public static ItemStack buildPauseResume(boolean isPaused) {
        ItemStack item = new ItemStack(isPaused ? Material.GRAY_DYE : Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(isPaused ? "§7▶ Reprendre" : "§a⏸ Pause"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§6Téléportation"));
        meta.lore(List.of(Component.text("§7Clic droit pour choisir un joueur")));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack buildSpeedDown(double currentSpeed) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§c§l−  Ralentir  §e" + formatSpeed(currentSpeed)));
        meta.lore(List.of(Component.text("§71.0× → 0.5× → 0.25×")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildRewind() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§e§l«« −5 secondes"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildForward() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§e§l+5 secondes »»"));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack buildSpeedUp(double currentSpeed) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§a§l+  Accélérer  §e" + formatSpeed(currentSpeed)));
        meta.lore(List.of(Component.text("§71.0× → 2.0× → 3.0× → 4.0×")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildMenu() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§b§lReplay Viewer"));
        meta.lore(List.of(Component.text("§7Ouvre le menu du replay")));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isPauseResumeDye(ItemStack item) {
        if (item == null) return false;
        return item.getType() == Material.GRAY_DYE || item.getType() == Material.LIME_DYE;
    }
}
