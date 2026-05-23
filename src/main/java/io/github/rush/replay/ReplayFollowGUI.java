package io.github.rush.replay;

import io.github.rush.TranslationLoader;
import io.github.rush.menus.GUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
        GUI gui = new GUI(Component.translatable("gui.replay_follow.title"), rows);

        int slot = 0;
        for (UUID uuid : mannequins.keySet()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);
            final String displayName = name;
            final UUID targetUuid = uuid;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            meta.displayName(TranslationLoader.txt("rush.replay_follow_name", displayName));

            UUID currentTarget = playback.getFollowTarget(viewer.getUniqueId());
            if (targetUuid.equals(currentTarget)) {
                meta.lore(List.of(
                        TranslationLoader.txt("rush.replay_following"),
                        TranslationLoader.txt("rush.replay_stop_follow")));
            } else {
                meta.lore(List.of(TranslationLoader.txt("rush.replay_click_follow")));
            }
            head.setItemMeta(meta);

            gui.addItem(slot, head, p -> {
                UUID current = playback.getFollowTarget(p.getUniqueId());
                if (targetUuid.equals(current)) {
                    playback.clearFollowTarget(p.getUniqueId());
                } else {
                    playback.setFollowTarget(p.getUniqueId(), targetUuid);
                    var mannequin = playback.getMannequinByPlayer().get(targetUuid);
                    if (mannequin != null) {
                        Location mLoc = mannequin.getLocation();
                        if (mLoc.getY() > playback.getWorld().getMinHeight() + 1) {
                            p.teleport(mLoc);
                        }
                    }
                }
                p.closeInventory();
            });
            slot++;
        }

        gui.openGUI(viewer);
    }
}
