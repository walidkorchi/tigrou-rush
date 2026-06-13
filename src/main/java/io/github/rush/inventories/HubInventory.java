package io.github.rush.inventories;

import io.github.rush.utils.i18n;
import net.kyori.adventure.text.Component;
import io.github.rush.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class HubInventory {

    public static final int SLOT_GAME_BROWSER = 0;
    public static final int SLOT_PLAYER_PROFILE = 1;
    public static final int SLOT_GAME_HOST_CREATOR = 7;
    public static final int SLOT_SETTINGS = 8;

    private HubInventory() {
    }

    public static void give(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(SLOT_GAME_BROWSER, createCompassItem());
        player.getInventory().setItem(SLOT_PLAYER_PROFILE, createPlayerSkullItem(player));
        player.getInventory().setItem(SLOT_GAME_HOST_CREATOR, createGameHostItem());
        player.getInventory().setItem(SLOT_SETTINGS, createSettingsItem());
    }

    public static ItemStack createPlayerSkullItem(Player player) {
        return ItemBuilder.<SkullMeta>of(Material.PLAYER_HEAD)
                .meta(m -> m.setOwningPlayer(player))
                .name(i18n.txt("rush.skull_name", player.getName()))
                .lore(i18n.txt("rush.skull_lore1"))
                .build();
    }

    public static ItemStack createCompassItem() {
        return ItemBuilder.of(Material.COMPASS)
                .name(i18n.txt("rush.compass_name"))
                .lore(
                        Component.translatable("rush.compass_lore1"),
                        Component.translatable("rush.compass_lore2"))
                .build();
    }

    public static ItemStack createSettingsItem() {
        return ItemBuilder.of(Material.REPEATER)
                .name(i18n.txt("rush.settings_name"))
                .build();
    }

    public static ItemStack createGameHostItem() {
        return ItemBuilder.of(Material.BEACON)
                .name(i18n.txt("rush.create_game_name"))
                .lore(
                        Component.translatable("rush.create_game_lore1"),
                        Component.translatable("rush.create_game_lore2"))
                .build();
    }

}
