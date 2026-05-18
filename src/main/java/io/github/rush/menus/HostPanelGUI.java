package io.github.rush.menus;

import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class HostPanelGUI {

    private HostPanelGUI() {}

    public static void open(Player host, GameRoom room, GameManager manager) {
        List<Player> players = room.getGame().getPlayers().stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .toList();

        // Rows: 1 for the force-start button + ceil(players / 9) for kick list, min 2 rows
        int rows = Math.max(2, 1 + (int) Math.ceil(players.size() / 9.0));
        GUI gui = new GUI("§8Panneau de l'Hôte", rows);

        // Force-start button (slot 4, top row centre)
        ItemStack forceStart = new ItemStack(Material.LIME_DYE);
        ItemMeta fsMeta = forceStart.getItemMeta();
        fsMeta.displayName(Component.text("§a§lForcer le démarrage"));
        forceStart.setItemMeta(fsMeta);
        forceStart.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Component.text("§7Démarre la partie immédiatement"),
                Component.text("§7sans attendre le compte à rebours."))));

        gui.addItem(4, forceStart, p -> {
            p.closeInventory();
            room.getGame().start();
        });

        // Per-player kick buttons (row 2+, one slot per player)
        int slot = 9;
        for (Player target : players) {
            if (target.equals(host)) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta headMeta = head.getItemMeta();
            headMeta.displayName(Component.text("§c§lExpulser §f" + target.getName()));
            head.setItemMeta(headMeta);
            head.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                    Component.text("§7Clic pour expulser " + target.getName()),
                    Component.text("§7de la salle d'attente."))));

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
