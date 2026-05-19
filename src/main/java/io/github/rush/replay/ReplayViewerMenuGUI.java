package io.github.rush.replay;

import io.github.rush.Main;
import io.github.rush.menus.GUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ReplayViewerMenuGUI {

    private static final int SLOT_LEAVE = 11;
    private static final int SLOT_NIGHT_VISION = 15;

    private ReplayViewerMenuGUI() {}

    public static void open(Player player, ReplayPlayback playback) {
        GUI gui = new GUI("§8Replay Viewer", 3);

        ItemStack door = new ItemStack(Material.OAK_DOOR);
        ItemMeta doorMeta = door.getItemMeta();
        doorMeta.displayName(Component.text("§cQuitter le replay"));
        door.setItemMeta(doorMeta);

        gui.addItem(SLOT_LEAVE, door, p -> {
            p.closeInventory();
            Main.getInstance().getReplayManager().leaveReplay(p);
        });

        gui.addItem(SLOT_NIGHT_VISION, buildNightVisionItem(player), p -> {
            if (p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            } else {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        Integer.MAX_VALUE, 0, false, false));
            }
            open(p, playback);
        });

        gui.openGUI(player);
    }

    private static ItemStack buildNightVisionItem(Player player) {
        boolean active = player.hasPotionEffect(PotionEffectType.NIGHT_VISION);
        ItemStack item = new ItemStack(active ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§eVision nocturne"));
        item.setItemMeta(meta);
        return item;
    }
}
