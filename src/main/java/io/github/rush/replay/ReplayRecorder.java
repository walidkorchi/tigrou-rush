package io.github.rush.replay;

import io.github.rush.Main;
import io.github.rush.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.Map;

public final class ReplayRecorder implements Listener {

    static final UUID GLOBAL = new UUID(0L, 0L);

    private final Game game;
    private final String worldName;
    private final long startMs = System.currentTimeMillis();
    private final Map<UUID, List<ReplayAction>> actionMap = new HashMap<>();
    private BukkitTask movementTask;

    public ReplayRecorder(Game game, String worldName) {
        this.game = game;
        this.worldName = worldName;
        Bukkit.getPluginManager().registerEvents(this, Main.getInstance());
        movementTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::sampleMovement, 0L, 2L);
    }

    private long elapsed() {
        return System.currentTimeMillis() - startMs;
    }

    private List<ReplayAction> actionsFor(UUID uuid) {
        return actionMap.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    private void sampleMovement() {
        long ts = elapsed();
        for (Entity entity : game.getPlayers()) {
            if (entity instanceof Player player) {
                var loc = player.getLocation();
                actionsFor(player.getUniqueId()).add(
                        new MoveAction(ts, player.getUniqueId(),
                                loc.getX(), loc.getY(), loc.getZ(),
                                loc.getYaw(), loc.getPitch()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().getWorld().getName().equals(worldName)) return;
        var block = event.getBlock();
        actionsFor(event.getPlayer().getUniqueId()).add(new BlockChangeAction(elapsed(),
                block.getX(), block.getY(), block.getZ(), worldName,
                block.getType().name(),
                event.getBlockReplacedState().getType().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.getPlayer().getWorld().getName().equals(worldName)) return;
        var block = event.getBlock();
        actionsFor(event.getPlayer().getUniqueId()).add(new BlockChangeAction(elapsed(),
                block.getX(), block.getY(), block.getZ(), worldName,
                "AIR",
                block.getType().name()));
    }

    public void recordDeath(UUID uuid) {
        actionsFor(uuid).add(new DeathAction(elapsed(), uuid));
    }

    public void recordRespawn(UUID uuid, double x, double y, double z) {
        actionsFor(uuid).add(new RespawnAction(elapsed(), uuid, x, y, z));
    }

    public void recordBedDestroy(String teamColorName, UUID destroyerUuid) {
        actionsFor(GLOBAL).add(new BedDestroyAction(elapsed(), teamColorName, destroyerUuid));
    }

    public void recordPhaseChange(String phaseName) {
        actionsFor(GLOBAL).add(new PhaseAction(elapsed(), phaseName));
    }

    public void stop(String sessionId, String winnerTeamColorName, String hostName,
                     List<String> participantNames, String mapTypeName,
                     Map<String, String> teamColorsByPlayerUuid) {
        HandlerList.unregisterAll(this);
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
        long durationMs = elapsed();
        ReplayHeader header = new ReplayHeader(sessionId, hostName, startMs, durationMs,
                winnerTeamColorName, participantNames, mapTypeName, teamColorsByPlayerUuid);
        ReplayFile file = new ReplayFile(header, new HashMap<>(actionMap));
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(),
                () -> Main.getInstance().getReplayStorage().save(file));
    }
}
