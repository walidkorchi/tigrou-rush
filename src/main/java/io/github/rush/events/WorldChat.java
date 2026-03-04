package io.github.rush.events;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import net.kyori.adventure.audience.Audience;

import java.util.Set;

public class WorldChat implements Listener {

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        final Player sender = event.getPlayer();
        final World senderWorld = sender.getWorld();
        final Set<Audience> viewers = event.viewers();

        viewers.removeIf(audience -> {
            if (audience instanceof Player viewer) {
                return !viewer.getWorld().equals(senderWorld);
            }

            return false;
        });
    }
}
