package io.github.rush.replay;

import io.github.rush.menus.GUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public final class ReplayFollowGUI {

    private ReplayFollowGUI() {}

    public static void open(Player viewer, ReplayPlayback playback) {
        var mannequins = playback.getMannequinByPlayer();
        int rows = Math.max(1, (int) Math.ceil(mannequins.size() / 9.0));
        GUI gui = new GUI("§8Suivre un joueur", rows);

        int slot = 0;
        for (UUID uuid : mannequins.keySet()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);
            final String displayName = name;
            final UUID targetUuid = uuid;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            meta.displayName(Component.text("§f" + displayName));

            UUID currentTarget = playback.getFollowTarget(viewer.getUniqueId());
            if (targetUuid.equals(currentTarget)) {
                meta.lore(List.of(
                        Component.text("§aSuivi en cours"),
                        Component.text("§7Cliquer pour arrêter")));
            } else {
                meta.lore(List.of(Component.text("§7Cliquer pour suivre")));
            }
            head.setItemMeta(meta);

            gui.addItem(slot, head, p -> {
                UUID current = playback.getFollowTarget(p.getUniqueId());
                if (targetUuid.equals(current)) {
                    playback.clearFollowTarget(p.getUniqueId());
                } else {
                    playback.setFollowTarget(p.getUniqueId(), targetUuid);
                }
                p.closeInventory();
            });
            slot++;
        }

        gui.openGUI(viewer);
    }
}
