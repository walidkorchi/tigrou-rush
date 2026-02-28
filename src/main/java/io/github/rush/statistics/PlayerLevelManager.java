package io.github.rush.statistics;

import io.github.rush.Main;
import io.github.rush.database.DatabaseManager;
import jakarta.persistence.EntityManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerLevelManager {

    private final DatabaseManager databaseManager;
    private final List<PlayerLevel> playerLevels = new ArrayList<>();

    public PlayerLevelManager(Main plugin) {
        this.databaseManager = new DatabaseManager(plugin);
    }

    public PlayerLevel loadPlayerLevel(UUID uuid) {
        EntityManager em = databaseManager.getEntityManager();
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
        EntityManager em = databaseManager.getEntityManager();
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

    public void addXP(UUID uuid, int xp) {
        PlayerLevel playerLevel = loadPlayerLevel(uuid);
        // TODO: add this logic
        playerLevel.addXP(xp);
        savePlayerLevel(playerLevel);
    }

    public void removeXP(UUID uuid, int xp) {
        PlayerLevel playerLevel = loadPlayerLevel(uuid);
        // TODO: add this logic
        playerLevel.removeXP(xp);
        savePlayerLevel(playerLevel);
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public void close() {
        databaseManager.close();
    }
}
