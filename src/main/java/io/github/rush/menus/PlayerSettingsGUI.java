package io.github.rush.menus;

import io.github.rush.Main;
import io.github.rush.settings.PlayerSettings;
import io.github.rush.settings.PlayerSettingsManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class PlayerSettingsGUI {

    private static final String TITLE = "§8Paramètres du joueur";

    public static void openPlayerSettings(Player player) {
        final GUI gui = new GUI(TITLE, 1);
        final PlayerSettingsManager settingsManager = Main.getInstance().getPlayerSettingsManager();
        final PlayerSettings settings = settingsManager.loadSettings(player.getUniqueId());

        ItemStack scoreboardItem = createScoreboardToggleItem(settings.isScoreboardEnabled());
        gui.addItem(4, scoreboardItem, p -> {
            boolean newState = !settingsManager.isScoreboardEnabled(p.getUniqueId());
            settingsManager.setScoreboardEnabled(p.getUniqueId(), newState);

            if (!newState) {
                Main.getInstance().getScoreboardManager().removeScoreboard(p);
            }

            openPlayerSettings(p);
        });

        gui.openGUI(player);
    }

    private static ItemStack createScoreboardToggleItem(boolean enabled) {
        Material material = enabled ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String status = enabled ? "§aActivé" : "§cDésactivé";
        meta.displayName(Component.text("§fScoreboard"));
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.LORE, ItemLore.lore(
                List.of(Component.text("§7État: " + status), Component.text("§7Clic pour basculer"))
        ));

        return item;
    }
}
