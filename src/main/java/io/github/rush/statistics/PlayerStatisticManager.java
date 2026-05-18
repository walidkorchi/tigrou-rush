package io.github.rush.statistics;

import io.github.rush.Main;
import io.github.rush.database.DatabaseManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public class PlayerStatisticManager {

    @Getter
    private final DatabaseManager databaseManager;

    public PlayerStatisticManager(Main plugin) {
        this.databaseManager = new DatabaseManager(plugin);
    }

    public PlayerStatistic loadStatistic(UUID uuid) {
        final EntityManager em = databaseManager.getEntityManager();

        try {
            PlayerStatistic stat = em.find(PlayerStatistic.class, uuid);

            if (stat == null) {
                stat = new PlayerStatistic(uuid);
            }

            return stat;
        } finally {
            em.close();
        }
    }

    public PlayerStatistic getStatistic(Player player) {
        return loadStatistic(player.getUniqueId());
    }

    public void saveStatistic(PlayerStatistic statistic) {
        EntityManager em = databaseManager.getEntityManager();

        try {
            em.getTransaction().begin();
            statistic.addCurrentValues();
            em.merge(statistic);
            em.getTransaction().commit();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("Failed to save statistic: " + e.getMessage());

            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
        } finally {
            em.close();
        }
    }

    public List<Map.Entry<String, Integer>> getTop10ByKills() {
        return queryTop10("kills");
    }

    public List<Map.Entry<String, Integer>> getTop10ByWins() {
        return queryTop10("wins");
    }

    public List<Map.Entry<String, Integer>> getTop10ByWinStreak() {
        return queryTop10("winStreak");
    }

    private List<Map.Entry<String, Integer>> queryTop10(String fieldName) {
        EntityManager em = databaseManager.getEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
            Root<PlayerStatistic> root = query.from(PlayerStatistic.class);

            query.multiselect(root.get("name"), root.<Integer>get(fieldName))
                 .where(cb.and(
                     cb.isNotNull(root.get("name")),
                     cb.notEqual(root.<String>get("name"), "")
                 ))
                 .orderBy(cb.desc(root.get(fieldName)));

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

    public void storeStatistic(PlayerStatistic statistic) {
        saveStatistic(statistic);
    }
}
