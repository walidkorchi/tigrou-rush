package io.github.rush.menus;

import io.github.rush.game.GameManager;
import io.github.rush.game.GamePlayer;
import io.github.rush.game.GameRoom;
import io.github.rush.TranslationLoader;
import io.github.rush.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class HostPanelGUI {

    private HostPanelGUI() {
    }

    public static void open(Player host, GameRoom room, GameManager manager) {
        final List<Player> players = room.getGame().getPlayers().stream()
                .filter(GamePlayer.class::isInstance)
                .map(e -> ((GamePlayer) e).player())
                .toList();

        // Rows: 1 for the force-start button + ceil(players / 9) for kick list, min 2 rows
        final int rows = Math.max(2, 1 + (int) Math.ceil(players.size() / 9.0));
        final GUI gui = new GUI(Component.translatable("rush.host_panel_title"), rows);

        // Delete room button (slot 0, top row left)
        final ItemStack deleteRoom = ItemBuilder.of(Material.BARRIER)
                .name(TranslationLoader.txt("rush.host_panel_delete"))
                .lore(
                        TranslationLoader.txt("rush.host_panel_delete_lore1"),
                        TranslationLoader.txt("rush.host_panel_delete_lore2"))
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
                .name(TranslationLoader.txt("rush.host_panel_force_start"))
                .lore(
                        TranslationLoader.txt("rush.host_panel_force_start_lore1"),
                        TranslationLoader.txt("rush.host_panel_force_start_lore2"))
                .build();

        gui.addItem(4, forceStart, p -> {
            p.closeInventory();
            room.getGame().start();
        });

        // Per-player kick buttons (row 2+, one slot per player)
        int slot = 9;
        for (Player target : players) {
            if (target.equals(host))
                continue;

            final ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
                    .name(TranslationLoader.txt("rush.host_panel_kick_name", target.getName()))
                    .lore(
                            TranslationLoader.txt("rush.host_panel_kick_lore1", target.getName()),
                            TranslationLoader.txt("rush.host_panel_kick_lore2"))
                    .build();
            final Player kicked = target;

            gui.addItem(slot, head, p -> {
                p.closeInventory();
                room.removePlayer(kicked);
                manager.removePlayerFromGameRoom(kicked);
                kicked.teleport(kicked.getServer().getWorlds().get(0).getSpawnLocation());
                kicked.getInventory().clear();
                kicked.sendMessage(Component.translatable("rush.room_kicked"));
                p.sendMessage(Component.translatable("rush.player_kicked", Component.text(kicked.getName())));
            });

            slot++;
        }

        gui.openGUI(host);
    }
}
