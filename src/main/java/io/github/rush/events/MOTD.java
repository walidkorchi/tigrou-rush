package io.github.rush.events;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MOTD implements Listener {

    private static final Component MOTD = MiniMessage.miniMessage().deserialize(
            "<color:#B8291B>[TR]</color>"
                    + " <white><bold>Serveur dédi\u00e9 au Rush BedWars</bold></white>"
                    + "\n"
                    + "<color:#FF55FF>\u00ab En actif d\u00e9veloppement - Infos sur Github \u00bb</color>");

    @EventHandler
    public void onServerListPing(PaperServerListPingEvent event) {
        event.motd(MOTD);
    }
}
