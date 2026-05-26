package io.github.rush.game;

import io.github.rush.Main;
import org.bukkit.entity.Player;

import java.util.*;

public class GameKillTracker {


    private final Map<UUID, Map<UUID, Double>> damageDealt = new HashMap<>();
    private final Map<UUID, LastHitRecord> lastHitBy = new HashMap<>();

    public void recordDamage(Player victim, Player attacker, double damage) {
        final UUID victimId = victim.getUniqueId();
        final UUID attackerId = attacker.getUniqueId();

        damageDealt.computeIfAbsent(victimId, k -> new HashMap<>())
                .merge(attackerId, damage, Double::sum);
        lastHitBy.put(victimId, new LastHitRecord(attackerId, System.currentTimeMillis()));
    }

    public KillResult resolveKill(Player victim, Player bukkitKiller) {
        final UUID victimId = victim.getUniqueId();
        Player killer = bukkitKiller;

        if (killer == null) {
            final LastHitRecord record = lastHitBy.get(victimId);

            if (record != null && System.currentTimeMillis() - record.timestamp <= Main.getInstance().getConfig().getLong("kill-tracker.last-hit-expiry-ms")) {
                killer = victim.getServer().getPlayer(record.attackerId);
            }
        }

        if (killer == null) {
            clearVictim(victimId);
            return new KillResult(null, List.of());
        }

        final UUID killerId = killer.getUniqueId();
        final Map<UUID, Double> victimDamageMap = damageDealt.getOrDefault(victimId, Map.of());
        final double killerDamage = victimDamageMap.getOrDefault(killerId, 0.0);

        final List<Player> assists = new ArrayList<>();

        if (killerDamage > 0) {
            final double threshold = killerDamage * Main.getInstance().getConfig().getDouble("kill-tracker.assist-threshold");

            for (Map.Entry<UUID, Double> entry : victimDamageMap.entrySet()) {
                if (entry.getKey().equals(killerId))
                    continue;
                if (entry.getValue() >= threshold) {
                    final Player assistPlayer = victim.getServer().getPlayer(entry.getKey());


                    if (assistPlayer != null) {
                        assists.add(assistPlayer);
                    }
                }
            }
        }

        clearVictim(victimId);
        return new KillResult(killer, assists);
    }

    private void clearVictim(UUID victimId) {
        damageDealt.remove(victimId);
        lastHitBy.remove(victimId);
    }

    public void removePlayer(UUID playerId) {
        damageDealt.remove(playerId);
        lastHitBy.remove(playerId);
        damageDealt.values().forEach(map -> map.remove(playerId));
    }

    public void reset() {
        damageDealt.clear();
        lastHitBy.clear();
    }

    public record KillResult(Player killer, List<Player> assists) {
    }

    private record LastHitRecord(UUID attackerId, long timestamp) {
    }
}
