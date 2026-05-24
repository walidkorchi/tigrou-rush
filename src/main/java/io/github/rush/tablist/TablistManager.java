package io.github.rush.tablist;

import io.github.rush.Main;
import io.github.rush.statistics.PlayerLevel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class TablistManager {

    private final Main plugin;
    private final Map<Player, FastTablist> tablists = new HashMap<>();

    public TablistManager(Main plugin) {
        this.plugin = plugin;
    }

    public void onPlayerJoin(Player player) {
        tablists.put(player, new FastTablist(player));
        updatePlayerListName(player);
    }

    public void onPlayerQuit(Player player) {
        FastTablist tablist = tablists.remove(player);
        if (tablist != null) {
            tablist.delete();
        }
    }

    public void updatePlayerListName(Player player) {
        PlayerLevel level = plugin.getPlayerLevelManager().loadPlayerLevel(player.getUniqueId());
        Component rank = MiniMessage.miniMessage().deserialize(level.getFormattedRank());
        Component nameComponent = Component.text("[", NamedTextColor.GRAY)
                .append(rank)
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(player.displayName());
        player.playerListName(nameComponent);
    }

    public void updateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayerListName(player);
        }
    }
}
