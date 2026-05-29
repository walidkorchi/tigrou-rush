package io.github.rush.events;

import io.github.rush.Main;
import io.github.rush.abstracts.Team;
import io.github.rush.entities.GamePlayer;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.storage.PlayerLevelManager;
import io.github.rush.storage.PlayerLevelManager.PlayerLevel;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class Chat implements Listener {

    private final Main plugin;

    public Chat(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncChatEvent event) {
        final Player player = event.getPlayer();

        // world-scoped > only same-world players receive the message
        event.viewers().removeIf(
                audience -> audience instanceof Player viewer && !viewer.getWorld().equals(player.getWorld()));

        final PlayerLevel playerLevel = Main.getInstance().getPlayerLevelManager().loadPlayerLevel(player.getUniqueId());
        final int ri = playerLevel.getRankIndex();
        Component rankComponent = MiniMessage.miniMessage().deserialize(playerLevel.getFormattedRank()).color(NamedTextColor.WHITE);
        Component hoverText;

        if (ri >= 0) {
            final String rankName = PlayerLevel.getPrestigeName(ri) + " "
                    + PlayerLevel.getGemName(ri) + " "
                    + PlayerLevel.getLevelInRank(ri);
            hoverText = Component.text(rankName, NamedTextColor.GOLD)
                    .append(Component.newline())
                    .append(Component.text("XP: ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%,d", playerLevel.getTotalXP()), NamedTextColor.YELLOW));
        } else {
            hoverText = Component.text("Non classé", NamedTextColor.GRAY)
                    .append(Component.newline())
                    .append(Component.text("XP: ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%,d", playerLevel.getTotalXP()), NamedTextColor.YELLOW));
        }

        rankComponent = rankComponent.hoverEvent(HoverEvent.showText(hoverText));

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        final boolean isGlobal = message.startsWith("@");

        if (isGlobal)
            message = message.substring(1).trim();

        final Component tail = Component.text(" > ", NamedTextColor.WHITE)
                .append(Component.text(message, NamedTextColor.WHITE));
        final Component rankBadge = Component.text("[", NamedTextColor.GRAY)
                .append(rankComponent)
                .append(Component.text("]", NamedTextColor.GRAY));

        Component formatComponent;

        if (plugin.getGameManager().isPlayerInWaitingRoom(player)) {
            formatComponent = rankBadge
                    .append(Component.text(" [", NamedTextColor.GRAY))
                    .append(Component.text("Lobby", NamedTextColor.BLUE))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(player.displayName())
                    .append(tail);
        } else {
            final Game game = Main.getInstance().getGameManager().getGameForPlayer(player);
            final Team team = (game != null && game.getState() == GameState.RUNNING)
                    ? game.getPlayerTeam(new GamePlayer(player))
                    : null;

            if (team != null) {
                final Team.Color color = team.getColor();

                formatComponent = rankBadge
                        .append(Component.text(" [", NamedTextColor.GRAY))
                        .append(Component.text(color.name(), color.getTextColor()))
                        .append(Component.text("] ", NamedTextColor.GRAY))
                        .append(player.displayName())
                        .append(tail);

                if (!isGlobal) {
                    event.setCancelled(true);

                    for (Player recipient : plugin.getServer().getOnlinePlayers()) {
                        Team recipientTeam = game.getPlayerTeam(new GamePlayer(recipient));
                        if (recipientTeam != null && recipientTeam.equals(team)) {
                            recipient.sendMessage(formatComponent);
                        }
                    }

                    return;
                }
            } else {
                formatComponent = rankBadge
                        .append(Component.text(" ", NamedTextColor.WHITE))
                        .append(player.displayName())
                        .append(tail);
            }
        }

        event.renderer((source, sourceDisplayName, msg, viewer) -> formatComponent);
    }
}
