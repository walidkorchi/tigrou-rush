package io.github.rush.statistics;

import io.github.rush.Main;
import io.github.rush.database.DatabaseManager;
import jakarta.persistence.EntityManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerLevelManager {

    private static final int MAX_LEVEL = 150;

    private final Main plugin;
    private final DatabaseManager databaseManager;

    public PlayerLevelManager(Main plugin) {
        this.plugin = plugin;
        this.databaseManager = new DatabaseManager(plugin);
    }

    public int calculateTotalXP(PlayerStatistic stat) {
        return (stat.getWins() * 100) +
                (stat.getLoses() * 20) +
                (stat.getKills() * 15) +
                (stat.getAssists() * 5) -
                (stat.getDeaths() * 10);
    }

    public int calculateLevel(int totalXP) {
        int lvl = 0;
        int remaining = totalXP;
        while (lvl < MAX_LEVEL && remaining >= PlayerLevel.getXPForLevel(lvl + 1)) {
            remaining -= PlayerLevel.getXPForLevel(lvl + 1);
            lvl++;
        }
        return lvl;
    }

    public void recalculateLevelFromStats(UUID uuid) {
        final PlayerStatistic stat = plugin.getPlayerStatisticManager().loadStatistic(uuid);
        final PlayerLevel playerLevel = loadPlayerLevel(uuid);

        final int totalXP = calculateTotalXP(stat);
        final int level = calculateLevel(totalXP);
        final int currentXP = totalXP - PlayerLevel.getCumulativeXP(level);

        playerLevel.setTotalXP(totalXP);
        playerLevel.setLevel(level);
        playerLevel.setCurrentXP(currentXP);

        savePlayerLevel(playerLevel);
    }

    public void addXP(UUID uuid, int xp) {
        final PlayerLevel playerLevel = loadPlayerLevel(uuid);
        final int oldLevel = playerLevel.getLevel();

        playerLevel.addXP(xp);
        final int level = playerLevel.getLevel();
        playerLevel.setCurrentXP(playerLevel.getTotalXP() - PlayerLevel.getCumulativeXP(level));
        savePlayerLevel(playerLevel);

        if (level > oldLevel) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
    }

    public void removeXP(UUID uuid, int xp) {
        final PlayerLevel playerLevel = loadPlayerLevel(uuid);

        playerLevel.removeXP(xp);
        final int level = playerLevel.getLevel();
        playerLevel.setCurrentXP(playerLevel.getTotalXP() - PlayerLevel.getCumulativeXP(level));
        savePlayerLevel(playerLevel);
    }

    public PlayerLevel loadPlayerLevel(UUID uuid) {
        final EntityManager em = databaseManager.getEntityManager();

        try {
            PlayerLevel playerLevel = em.find(PlayerLevel.class, uuid);
            if (playerLevel == null) {
                playerLevel = new PlayerLevel(uuid);
            }
            return playerLevel;
        } finally {
            em.close();
        }
    }

    public PlayerLevel getPlayerLevel(Player player) {
        return loadPlayerLevel(player.getUniqueId());
    }

    public void savePlayerLevel(PlayerLevel playerLevel) {
        final EntityManager em = databaseManager.getEntityManager();

        try {
            em.getTransaction().begin();
            em.merge(playerLevel);
            em.getTransaction().commit();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("Failed to save player level: " + e.getMessage());
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
        } finally {
            em.close();
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public void close() {
        databaseManager.close();
    }
}
