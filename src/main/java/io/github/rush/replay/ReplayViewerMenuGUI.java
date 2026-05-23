package io.github.rush.replay;

import io.github.rush.Main;
import io.github.rush.TranslationLoader;
import io.github.rush.menus.GUI;
import io.github.rush.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ReplayViewerMenuGUI {

    private static final int SLOT_LEAVE = 11;
    private static final int SLOT_NIGHT_VISION = 15;

    private ReplayViewerMenuGUI() {}

    public static void open(Player player, ReplayPlayback playback) {
        final GUI gui = new GUI(Component.translatable("gui.replay_viewer.menu_title"), 3);

        gui.addItem(SLOT_LEAVE, ItemBuilder.of(Material.OAK_DOOR).name(TranslationLoader.txt("gui.replay_viewer.leave")).build(), p -> {
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
        final boolean active = player.hasPotionEffect(PotionEffectType.NIGHT_VISION);
        return ItemBuilder.of(active ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(TranslationLoader.txt("gui.replay_viewer.night_vision"))
                .build();
    }
}
