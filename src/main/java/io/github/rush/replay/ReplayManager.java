package io.github.rush.replay;

import io.github.rush.utils.ReplayUtils.ReplayFile;
import io.github.rush.utils.ReplayUtils.ReplayHeader;

import io.github.rush.Main;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public final class ReplayManager {

    private final Main plugin;
    private final Map<String, ReplayPlayback> activePlaybacks = new HashMap<>();
    private final Map<String, List<Player>> pendingViewers = new HashMap<>();
    private final Map<Player, String> viewerToSession = new HashMap<>();

    public ReplayManager(Main plugin) {
        this.plugin = plugin;
    }

    public void joinReplay(Player player, ReplayHeader header) {
        final String sessionId = header.sessionId();

        // add viewer immediately if replay world is already ready
        final ReplayPlayback existing = activePlaybacks.get(sessionId);
        if (existing != null) {
            existing.addViewer(player);
            viewerToSession.put(player, sessionId);
            return;
        }

        // queue the viewer whilst the world is being created
        if (pendingViewers.containsKey(sessionId)) {
            pendingViewers.get(sessionId).add(player);
            viewerToSession.put(player, sessionId);
            player.sendMessage(Component.translatable("rush.replay_loading"));
            return;
        }

        pendingViewers.put(sessionId, new ArrayList<>(List.of(player)));
        viewerToSession.put(player, sessionId);
        player.sendMessage(Component.translatable("rush.replay_loading"));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final Optional<ReplayFile> opt = plugin.getReplayStorage().load(sessionId);

            Bukkit.getScheduler().runTask(plugin, () -> {
                opt.ifPresentOrElse(
                        file -> plugin.getGameManager().createReplayWorld(file, playback -> {
                            activePlaybacks.put(sessionId, playback);

                            final List<Player> pending = pendingViewers.remove(sessionId);

                            if (pending != null) {
                                for (Player p : pending) {
                                    if (p.isOnline()) {
                                        playback.addViewer(p);
                                    } else {
                                        viewerToSession.remove(p);
                                    }
                                }
                            }
                        }),
                        () -> {
                            final List<Player> pending = pendingViewers.remove(sessionId);

                            if (pending != null) {
                                for (Player p : pending) {
                                    viewerToSession.remove(p);
                                    p.sendMessage(Component.translatable("rush.replay_not_found"));
                                }
                            }
                        });
            });
        });
    }

    public void leaveReplay(Player player) {
        final String sessionId = viewerToSession.remove(player);
        if (sessionId == null)
            return;

        final ReplayPlayback playback = activePlaybacks.get(sessionId);
        if (playback != null && playback.removeViewer(player)) {
            activePlaybacks.remove(sessionId);
            playback.cleanup();
        }
    }

    public boolean isWatching(Player player) {
        return viewerToSession.containsKey(player);
    }

    public ReplayPlayback getPlayback(Player player) {
        final String sessionId = viewerToSession.get(player);
        return sessionId != null ? activePlaybacks.get(sessionId) : null;
    }
}
