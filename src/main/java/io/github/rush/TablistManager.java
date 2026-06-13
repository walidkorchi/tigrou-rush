package io.github.rush;

import io.github.rush.abstracts.Team;
import io.github.rush.storage.PlayerLevelManager.PlayerLevel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class TablistManager {

    private static final class FastTablist {
        private final Player player;
        // private Component header = Component.empty();
        // private Component footer = Component.empty();

        FastTablist(Player player) {
            this.player = player;
        }

        // void updateHeader(Component header) {
        // this.header = header;
        // send();
        // }

        // void updateFooter(Component footer) {
        // this.footer = footer;
        // send();
        // }

        // void updateHeaderFooter(Component header, Component footer) {
        // this.header = header;
        // this.footer = footer;
        // send();
        // }

        // private void send() {
        // player.sendPlayerListHeaderAndFooter(this.header, this.footer);
        // }

        void delete() {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

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
        updatePlayerListName(player, null);
    }

    public void updatePlayerListName(Player player, Team.Color teamColor) {
        final PlayerLevel level = plugin.getPlayerLevelManager().loadPlayerLevel(player.getUniqueId());
        final Component rank = MiniMessage.miniMessage().deserialize(level.getFormattedRank()).color(NamedTextColor.WHITE);

        Component nameComponent = Component.text("[", NamedTextColor.GRAY)
                .append(rank)
                .append(Component.text("] ", NamedTextColor.GRAY));

        if (teamColor != null) {
            final Component teamIcon = MiniMessage.miniMessage().deserialize(
                    "<image:tland:team_color-" + teamColor.name().toLowerCase() + ">")
                    .color(NamedTextColor.WHITE);
            nameComponent = nameComponent
                    .append(teamIcon)
                    .append(Component.space())
                    .append(player.displayName().color(teamColor.getTextColor()));
        } else {
            nameComponent = nameComponent.append(player.displayName());
        }

        player.playerListName(nameComponent);
    }

    public void updateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayerListName(player);
        }
    }
}
