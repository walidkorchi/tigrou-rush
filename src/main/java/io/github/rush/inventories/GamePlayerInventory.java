package io.github.rush.inventories;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

import io.github.rush.utils.ItemBuilder;
import io.github.rush.utils.i18n;

public class GamePlayerInventory {

    public static final int SLOT_TEAM_SELECTION = 0;
    public static final int SLOT_LEAVE_TEAM = 0;
    public static final int SLOT_READY_TOGGLER = 1;
    public static final int SLOT_HOST_PANEL = 8;

    public GamePlayerInventory() {
    }

    public static void give(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(SLOT_TEAM_SELECTION, createBannerItem());
        player.getInventory().setItem(SLOT_HOST_PANEL, createHostPanelItem());
    }

    public static ItemStack createBannerItem() {
        return ItemBuilder.<BannerMeta>of(Material.WHITE_BANNER)
                .name(i18n.txt("rush.bannerName"))
                .meta(m -> m.addItemFlags(ItemFlag.HIDE_DYE))
                .build();
    }

    public static ItemStack createReadyItem(boolean ready) {
        return ItemBuilder.of(ready ? Material.LIME_DYE : Material.RED_DYE)
                .name(i18n.txt(ready ? "rush.ready" : "rush.notReady"))
                .lore(i18n.txt(ready ? "rush.notReadyLore" : "rush.readyLore"))
                .build();
    }

    public static ItemStack createHostPanelItem() {
        return ItemBuilder.of(Material.NETHER_STAR)
                .name(i18n.txt("rush.host_panel_name"))
                .build();
    }

    public static ItemStack createSlimeballItem() {
        return ItemBuilder.of(Material.SLIME_BALL)
                .name(i18n.txt("rush.quit_team_confirm"))
                .lore(i18n.txt("rush.quitLore"))
                .build();
    }

}
