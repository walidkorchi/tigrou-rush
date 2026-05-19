package io.github.rush.menus;

import io.github.rush.Main;
import io.github.rush.settings.PlayerSettings;
import io.github.rush.settings.PlayerSettingsManager;
import io.github.rush.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerSettingsGUI {

    public static void openPlayerSettings(Player player) {
        final GUI gui = new GUI("§8Paramètres du joueur", 1);
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

            if (newState) {
                if (Main.getInstance().getMusicManager() != null) {
                    Main.getInstance().getMusicManager().playForPlayer(p);
                }
            } else {
                if (Main.getInstance().getMusicManager() != null) {
                    Main.getInstance().getMusicManager().stopForPlayer(p);
                }
            }

            openPlayerSettings(p);
        });

        gui.openGUI(player);
    }

    private static ItemStack createScoreboardToggleItem(boolean enabled) {
        return createToggleItem(Material.GREEN_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE, "§fScoreboard", enabled);
    }

    private static ItemStack createMusicToggleItem(boolean enabled) {
        return createToggleItem(Material.MUSIC_DISC_CAT, Material.MUSIC_DISC_11, "§fMusique", enabled);
    }

    private static ItemStack createToggleItem(Material on, Material off, String label, boolean enabled) {
        String status = enabled ? "§aActivé" : "§cDésactivé";
        return ItemBuilder.of(enabled ? on : off)
                .name(label)
                .lore("§7État: " + status, "§7Clic pour basculer")
                .build();
    }
}
