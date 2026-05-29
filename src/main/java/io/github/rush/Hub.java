package io.github.rush;

import io.github.rush.game.Game;
import io.github.rush.utils.ItemBuilder;
import io.github.rush.utils.i18n;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public final class Hub {

    private Hub() {
    }

    public static void resetPlayer(Player player) {
        player.teleport(Main.getInstance().getMainLobby());
        player.setAllowFlight(false);
        player.setFallDistance(0);
        player.setGameMode(GameMode.ADVENTURE);
        Game.resetPlayerHealth(player);
        restoreInventory(player);
    }

    public static void restoreInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, createCompassItem());
        player.getInventory().setItem(1, createPlayerSkullItem(player));
        player.getInventory().setItem(7, createGameHostItem());
        player.getInventory().setItem(8, createSettingsItem());
    }

    public static ItemStack createPlayerSkullItem(Player player) {
        final ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(i18n.txt("rush.skull_name", player.getName()));
        skull.setItemMeta(meta);
        skull.setData(DataComponentTypes.LORE,
                ItemLore.lore(List.of(i18n.txt("rush.skull_lore1"))));
        return skull;
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

    public static ItemStack createHostPanelItem() {
        return ItemBuilder.of(Material.NETHER_STAR).name(i18n.txt("rush.host_panel_name")).build();
    }

    public static boolean isAtHub(Player player) {
        return player.getWorld().getName().equals(Main.getInstance().getHubWorld());
    }

    public static boolean isUndroppableArmor(Material material) {
        return switch (material.getEquipmentSlot()) {
            case HEAD, LEGS, FEET -> true;
            default -> false;
        };
    }
}
