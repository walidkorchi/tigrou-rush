package io.github.rush.replay;

import io.github.rush.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        return ItemBuilder.of(isPaused ? Material.GRAY_DYE : Material.LIME_DYE)
                .name(isPaused ? "§7▶ Reprendre" : "§a⏸ Pause")
                .build();
    }

    private static ItemStack buildCompass() {
        return ItemBuilder.of(Material.COMPASS)
                .name("§6Téléportation")
                .lore("§7Clic droit pour choisir un joueur")
                .build();
    }

    public static ItemStack buildSpeedDown(double currentSpeed) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name("§c§l−  Ralentir  §e" + formatSpeed(currentSpeed))
                .lore("§71.0× → 0.5× → 0.25×")
                .build();
    }

    private static ItemStack buildRewind() {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name("§e§l«« −5 secondes")
                .build();
    }

    private static ItemStack buildForward() {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name("§e§l+5 secondes »»")
                .build();
    }

    public static ItemStack buildSpeedUp(double currentSpeed) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name("§a§l+  Accélérer  §e" + formatSpeed(currentSpeed))
                .lore("§71.0× → 2.0× → 3.0× → 4.0×")
                .build();
    }

    private static ItemStack buildMenu() {
        return ItemBuilder.of(Material.NETHER_STAR)
                .name("§b§lReplay Viewer")
                .lore("§7Ouvre le menu du replay")
                .build();
    }

    public static boolean isPauseResumeDye(ItemStack item) {
        if (item == null) return false;
        return item.getType() == Material.GRAY_DYE || item.getType() == Material.LIME_DYE;
    }
}
