package io.github.rush.guis;

import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;
import io.github.rush.utils.i18n;
import io.github.rush.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class HostPanelGUI {

    private HostPanelGUI() {
    }

    public static void open(Player host, GameRoom room, GameManager manager) {
        final GUI gui = new GUI(Component.translatable("rush.host_panel_title"), 1);

        // Delete room button (slot 0, top row left)
        final ItemStack deleteRoom = ItemBuilder.of(Material.BARRIER)
                .name(i18n.txt("rush.host_panel_delete"))
                .lore(
                        i18n.txt("rush.host_panel_delete_lore1"),
                        i18n.txt("rush.host_panel_delete_lore2"))
                .build();

        gui.addItem(0, deleteRoom, p -> {
            p.closeInventory();
            for (Entity entity : new java.util.ArrayList<>(room.getWorld().getPlayers())) {
                if (entity instanceof Player roomPlayer) {
                    roomPlayer.sendMessage(Component.translatable("rush.room_deleted_by_host"));
                }
            }
            manager.removeGameRoom(room.getId());
        });

        // Force-start button (slot 4, top row centre)
        final ItemStack forceStart = ItemBuilder.of(Material.LIME_DYE)
                .name(i18n.txt("rush.host_panel_force_start"))
                .lore(
                        i18n.txt("rush.host_panel_force_start_lore1"),
                        i18n.txt("rush.host_panel_force_start_lore2"))
                .build();

        gui.addItem(4, forceStart, p -> {
            p.closeInventory();
            room.getGame().forceStart();
        });

        // Lock / unlock button (slot 8, top-row right)
        final boolean locked = room.isLocked();
        final ItemStack lockItem = ItemBuilder.of(locked ? Material.RED_DYE : Material.LIME_DYE)
                .name(locked ? i18n.txt("rush.host_panel_unlock") : i18n.txt("rush.host_panel_lock"))
                .lore(locked ? i18n.txt("rush.host_panel_unlock_lore") : i18n.txt("rush.host_panel_lock_lore"))
                .build();

        gui.addItem(8, lockItem, p -> {
            room.setLocked(!room.isLocked());
            open(p, room, manager);
        });

        // Manage players button (slot 2) — opens HostPlayerListGUI
        final ItemStack managePlayers = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(i18n.txt("rush.host_panel_manage_players"))
                .lore(i18n.txt("rush.host_panel_manage_players_lore"))
                .build();

        gui.addItem(2, managePlayers, p -> HostPlayerListGUI.open(p, room, manager));

        gui.openGUI(host);
    }
}
