package io.github.rush.replay;

import io.github.rush.TranslationLoader;
import io.github.rush.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ReplayViewerInventory {

    public static final int SLOT_COMPASS = 0;
    public static final int SLOT_SPEED_DOWN = 2;
    public static final int SLOT_REWIND = 3;
    public static final int SLOT_PAUSE_RESUME = 4;
    public static final int SLOT_FORWARD = 5;
    public static final int SLOT_SPEED_UP = 6;
    public static final int SLOT_MENU = 8;

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
                .name(TranslationLoader.txt(isPaused ? "gui.replay_viewer.resume" : "gui.replay_viewer.pause"))
                .build();
    }

    private static ItemStack buildCompass() {
        return ItemBuilder.of(Material.COMPASS)
                .name(TranslationLoader.txt("gui.replay_viewer.compass"))
                .lore(TranslationLoader.txt("gui.replay_viewer.compass_lore"))
                .build();
    }

    public static ItemStack buildSpeedDown(double currentSpeed) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(TranslationLoader.txt("gui.replay_viewer.speed_down",
                        formatSpeed(currentSpeed)))
                .lore(TranslationLoader.txt("gui.replay_viewer.speed_down_lore"))
                .build();
    }

    private static ItemStack buildRewind() {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(TranslationLoader.txt("gui.replay_viewer.rewind"))
                .build();
    }

    private static ItemStack buildForward() {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(TranslationLoader.txt("gui.replay_viewer.forward"))
                .build();
    }

    public static ItemStack buildSpeedUp(double currentSpeed) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(TranslationLoader.txt("gui.replay_viewer.speed_up",
                        formatSpeed(currentSpeed)))
                .lore(TranslationLoader.txt("gui.replay_viewer.speed_up_lore"))
                .build();
    }

    private static ItemStack buildMenu() {
        return ItemBuilder.of(Material.NETHER_STAR)
                .name(TranslationLoader.txt("gui.replay_viewer.menu"))
                .lore(TranslationLoader.txt("gui.replay_viewer.menu_lore"))
                .build();
    }

    public static boolean isPauseResumeDye(ItemStack item) {
        if (item == null) return false;
        return item.getType() == Material.GRAY_DYE || item.getType() == Material.LIME_DYE;
    }
}
