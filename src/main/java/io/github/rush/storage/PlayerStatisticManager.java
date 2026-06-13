package io.github.rush.storage;

import io.github.rush.Main;
import io.github.rush.utils.i18n;
import io.github.rush.utils.RushLogger;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerStatisticManager {

    @Entity
    @Table(name = "player_statistics")
    @Getter
    @NoArgsConstructor
    public static class PlayerStatistic {

        @Id
        @Getter
        @Setter
        private UUID uuid;

        @Setter
        @Getter
        private int currentDeaths;
        @Setter
        private int currentDestroyedBeds;
        @Setter
        @Getter
        private int currentKills;
        @Setter
        private int currentLoses;
        @Setter
        @Getter
        private int currentScore;
        @Setter
        private int currentWins;
        @Setter
        private int currentAssists;
        @Setter
        @Getter
        private int deaths;
        @Setter
        private int destroyedBeds;
        @Setter
        @Getter
        private int kills;
        @Setter
        private int loses;
        @Setter
        private int assists;

        private String name = "";

        @Setter
        private int score;
        @Setter
        private int wins;
        @Setter
        @Getter
        private int winStreak;

        public PlayerStatistic(UUID uuid) {
            this.uuid = uuid;
        }

        public PlayerStatistic(OfflinePlayer player) {
            this.uuid = player.getUniqueId();
            this.name = player.getName();
        }

        public void addCurrentValues() {
            this.deaths += this.currentDeaths;
            this.currentDeaths = 0;
            this.destroyedBeds += this.currentDestroyedBeds;
            this.currentDestroyedBeds = 0;
            this.kills += this.currentKills;
            this.currentKills = 0;
            this.loses += this.currentLoses;
            this.currentLoses = 0;
            this.assists += this.currentAssists;
            this.currentAssists = 0;
            this.score += this.currentScore;
            this.currentScore = 0;
            this.wins += this.currentWins;
            this.currentWins = 0;
        }

        public double getWeightedScore() {
            if (getDeaths() == 0)
                return (double) getKills() * 2 + getAssists();
            else
                return Math.round((getKills() * 2.0 + getAssists() - getDeaths()) * 10.0) / 10.0;
        }
    }

    @Getter
    private final DatabaseManager databaseManager;

    public PlayerStatisticManager(Main plugin) {
        this.databaseManager = new DatabaseManager(plugin);
    }

    public PlayerStatistic loadStatistic(UUID uuid) {
        final EntityManager em = databaseManager.getEntityManager();

        try {
            PlayerStatistic stat = em.find(PlayerStatistic.class, uuid);

            if (stat == null)
                stat = new PlayerStatistic(uuid);

            return stat;
        } finally {
            em.close();
        }
    }

    public PlayerStatistic getStatistic(Player player) {
        return loadStatistic(player.getUniqueId());
    }

    public void saveStatistic(PlayerStatistic statistic) {
        final EntityManager em = databaseManager.getEntityManager();

        try {
            em.getTransaction().begin();
            statistic.addCurrentValues();
            em.merge(statistic);
            em.getTransaction().commit();
        } catch (Exception e) {
            RushLogger.error(i18n.log("internal.storage.player_statistic.save_failed", e.getMessage()));
            if (em.getTransaction().isActive())
                em.getTransaction().rollback();
        } finally {
            em.close();
        }
    }

    public List<Map.Entry<String, Integer>> queryTop10(String fieldName) {
        final EntityManager em = databaseManager.getEntityManager();

        try {
            final CriteriaBuilder cb = em.getCriteriaBuilder();
            final CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
            final Root<PlayerStatistic> root = query.from(PlayerStatistic.class);

            query.multiselect(root.get("name"), root.<Integer>get(fieldName))
                    .where(cb.and(
                            cb.isNotNull(root.get("name")),
                            cb.notEqual(root.<String>get("name"), "")))
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

}
