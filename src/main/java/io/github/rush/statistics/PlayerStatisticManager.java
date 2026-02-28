package io.github.rush.statistics;

import io.github.rush.Main;
import io.github.rush.database.DatabaseManager;
import jakarta.persistence.EntityManager;
import lombok.Getter;

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

    public void close() {
        databaseManager.close();
    }

    public void storeStatistic(PlayerStatistic statistic) {
        saveStatistic(statistic);
    }
}
