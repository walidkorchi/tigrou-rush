package io.github.rush.menus;

import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;
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
        List<Player> players = room.getGame().getPlayers().stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .toList();

        // Rows: 1 for the force-start button + ceil(players / 9) for kick list, min 2
        // rows
        int rows = Math.max(2, 1 + (int) Math.ceil(players.size() / 9.0));
        GUI gui = new GUI("§8Panneau de l'Hôte", rows);

        // Delete room button (slot 0, top row left)
        ItemStack deleteRoom = ItemBuilder.of(Material.BARRIER)
                .name("§c§lSupprimer la partie")
                .lore("§7Supprime la partie et renvoie", "§7tous les joueurs au lobby.")
                .build();

        gui.addItem(0, deleteRoom, p -> {
            p.closeInventory();
            for (Entity entity : new java.util.ArrayList<>(room.getWorld().getPlayers())) {
                if (entity instanceof Player roomPlayer) {
                    roomPlayer.sendMessage(Component.text("§cLa partie a été supprimée par l'hôte."));
                }
            }
            manager.removeGameRoom(room.getId());
        });

        // Force-start button (slot 4, top row centre)
        ItemStack forceStart = ItemBuilder.of(Material.LIME_DYE)
                .name("§a§lForcer le démarrage")
                .lore("§7Démarre la partie immédiatement", "§7sans attendre le compte à rebours.")
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

            ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
                    .name("§c§lExpulser §f" + target.getName())
                    .lore("§7Clic pour expulser " + target.getName(), "§7de la salle d'attente.")
                    .build();

            final Player kicked = target;
            gui.addItem(slot, head, p -> {
                p.closeInventory();
                room.removePlayer(kicked);
                manager.removePlayerFromGameRoom(kicked);
                kicked.teleport(kicked.getServer().getWorlds().get(0).getSpawnLocation());
                kicked.getInventory().clear();
                kicked.sendMessage(Component.text("§cVous avez été expulsé de la partie."));
                p.sendMessage(Component.text("§7" + kicked.getName() + " a été expulsé."));
            });
            slot++;
        }

        gui.openGUI(host);
    }
}
