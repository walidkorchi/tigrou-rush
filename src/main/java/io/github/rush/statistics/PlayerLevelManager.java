package io.github.rush.statistics;

import io.github.rush.Main;
import io.github.rush.database.DatabaseManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerLevelManager {

    private final Main plugin;
    @Getter
    private final DatabaseManager databaseManager;

    @Getter
    private static int maxLevel;
    @Getter
    private static int xpWins;
    @Getter
    private static int xpLosses;
    @Getter
    private static int xpKills;
    @Getter
    private static int xpAssists;
    @Getter
    private static int xpDeaths;

    public PlayerLevelManager(Main plugin) {
        this.plugin = plugin;
        this.databaseManager = new DatabaseManager(plugin);

        loadConfig();
    }

    private void loadConfig() {
        maxLevel = plugin.getConfig().getInt("maxLevel");
        xpWins = plugin.getConfig().getInt("xpMultipliers.wins");
        xpLosses = plugin.getConfig().getInt("xpMultipliers.losses");
        xpKills = plugin.getConfig().getInt("xpMultipliers.kills");
        xpAssists = plugin.getConfig().getInt("xpMultipliers.assists");
        xpDeaths = plugin.getConfig().getInt("xpMultipliers.deaths");
    }

    public static void setMaxLevel(int level) {
        maxLevel = Math.max(1, level);
    }

    public int calculateTotalXP(PlayerStatistic stat) {
        return (stat.getWins() * xpWins) +
                (stat.getLoses() * xpLosses) +
                (stat.getKills() * xpKills) +
                (stat.getAssists() * xpAssists) -
                (stat.getDeaths() * xpDeaths);
    }

    public int calculateLevel(int totalXP) {
        int lvl = 0;
        int remaining = totalXP;

        while (lvl < maxLevel && remaining >= PlayerLevel.getXPForLevel(lvl + 1)) {
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

    public List<Map.Entry<String, Integer>> getTop10ByLevel() {
        EntityManager em = databaseManager.getEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
            Root<PlayerLevel> levelRoot = query.from(PlayerLevel.class);
            Root<PlayerStatistic> statRoot = query.from(PlayerStatistic.class);

            query.multiselect(statRoot.get("name"), levelRoot.<Integer>get("level"))
                 .where(cb.and(
                     cb.equal(levelRoot.get("uuid"), statRoot.get("uuid")),
                     cb.isNotNull(statRoot.get("name")),
                     cb.notEqual(statRoot.<String>get("name"), "")
                 ))
                 .orderBy(cb.desc(levelRoot.get("level")));

            return em.createQuery(query)
                    .setMaxResults(10)
                    .getResultList()
                    .stream()
                    .map(row -> Map.entry((String) row[0], (Integer) row[1]))
                    .toList();
        } finally {
            em.close();
        }
    }

    public void close() {
        databaseManager.close();
    }

}
