package io.github.rush.statistics;

import io.github.rush.Main;
import io.github.rush.database.DatabaseManager;
import jakarta.persistence.EntityManager;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerLevelManager {

    private static final int MAX_LEVEL = 150;
    private static final int XP_PER_LEVEL = 500;

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
        return Math.min(MAX_LEVEL, totalXP / XP_PER_LEVEL);
    }

    public void recalculateLevelFromStats(UUID uuid) {
        final PlayerStatistic stat = plugin.getPlayerStatisticManager().loadStatistic(uuid);
        final PlayerLevel playerLevel = loadPlayerLevel(uuid);

        final int totalXP = calculateTotalXP(stat);
        final int level = calculateLevel(totalXP);
        final int xpForCurrentLevel = (totalXP / XP_PER_LEVEL) * XP_PER_LEVEL;
        final int currentXP = totalXP - xpForCurrentLevel;

        playerLevel.setTotalXP(totalXP);
        playerLevel.setLevel(level);
        playerLevel.setCurrentXP(currentXP);

        savePlayerLevel(playerLevel);
    }

    public void addXP(UUID uuid, int xp) {
        final PlayerLevel playerLevel = loadPlayerLevel(uuid);

        playerLevel.addXP(xp);
        playerLevel.setLevel(calculateLevel(playerLevel.getTotalXP()));
        savePlayerLevel(playerLevel);
    }

    public void removeXP(UUID uuid, int xp) {
        final PlayerLevel playerLevel = loadPlayerLevel(uuid);

        playerLevel.removeXP(xp);
        playerLevel.setLevel(calculateLevel(playerLevel.getTotalXP()));
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
