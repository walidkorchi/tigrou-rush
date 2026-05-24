package io.github.rush.tablist;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class FastTablist {

    private final Player player;
    private Component header = Component.empty();
    private Component footer = Component.empty();

    public FastTablist(Player player) {
        this.player = player;
    }

    public void updateHeader(Component header) {
        this.header = header;
        send();
    }

    public void updateFooter(Component footer) {
        this.footer = footer;
        send();
    }

    public void updateHeaderFooter(Component header, Component footer) {
        this.header = header;
        this.footer = footer;
        send();
    }

    private void send() {
        player.sendPlayerListHeaderAndFooter(this.header, this.footer);
    }

    public void delete() {
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }
}
