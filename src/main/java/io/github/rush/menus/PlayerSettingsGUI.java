package io.github.rush.menus;

import io.github.rush.Main;
import io.github.rush.settings.PlayerSettings;
import io.github.rush.settings.PlayerSettingsManager;
import io.github.rush.utils.ItemBuilder;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerSettingsGUI {

    public static void openPlayerSettings(Player player) {
        final GUI gui = new GUI(Component.translatable("gui.player_settings.title").toString(), 1);
        final PlayerSettingsManager settingsManager = Main.getInstance().getPlayerSettingsManager();
        final PlayerSettings settings = settingsManager.loadSettings(player.getUniqueId());

        final ItemStack scoreboardItem = createScoreboardToggleItem(settings.isScoreboardEnabled());

        gui.addItem(3, scoreboardItem, p -> {
            boolean newState = !settingsManager.isScoreboardEnabled(p.getUniqueId());
            settingsManager.setScoreboardEnabled(p.getUniqueId(), newState);

            if (!newState) {
                Main.getInstance().getScoreboardManager().removeScoreboard(p);
            }

            openPlayerSettings(p);
        });

        final ItemStack musicItem = createMusicToggleItem(settings.isMusicEnabled());

        gui.addItem(5, musicItem, p -> {
            boolean newState = !settingsManager.isMusicEnabled(p.getUniqueId());
            settingsManager.setMusicEnabled(p.getUniqueId(), newState);

            // TODO: play/stop custom lobby music for player

            openPlayerSettings(p);
        });

        gui.openGUI(player);
    }

    private static ItemStack createScoreboardToggleItem(boolean enabled) {
        return createToggleItem(Material.GREEN_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
                "gui.player_settings.scoreboard", enabled);
    }

    private static ItemStack createMusicToggleItem(boolean enabled) {
        return createToggleItem(Material.MUSIC_DISC_CAT, Material.MUSIC_DISC_11,
                "gui.player_settings.music", enabled);
    }

    private static ItemStack createToggleItem(Material on, Material off, String labelKey, boolean enabled) {
        Component status = Component
                .translatable(enabled ? "gui.player_settings.enabled" : "gui.player_settings.disabled");
        return ItemBuilder.of(enabled ? on : off)
                .name(Component.translatable(labelKey))
                .lore(
                        Component.translatable("gui.player_settings.status", status),
                        Component.translatable("gui.player_settings.click_toggle"))
                .build();
    }
}
