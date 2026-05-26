package io.github.rush.storage;

import io.github.rush.Main;
import io.github.rush.utils.i18n;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSettingsManager {

    @Entity
    @Table(name = "player_settings")
    @Getter
    @NoArgsConstructor
    public static class PlayerSettings {

        @Id
        private UUID playerId;

        @Setter
        @Column(name = "scoreboard_enabled")
        private boolean scoreboardEnabled = true;

        @Setter
        @Column(name = "music_enabled")
        private boolean musicEnabled = true;

        public PlayerSettings(UUID playerId) {
            this.playerId = playerId;
            this.scoreboardEnabled = true;
            this.musicEnabled = true;
        }
    }

    @Getter
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerSettings> settingsCache = new HashMap<>();

    public PlayerSettingsManager(Main plugin) {
        this.databaseManager = new DatabaseManager(plugin);
    }

    public PlayerSettings loadSettings(UUID playerId) {
        if (settingsCache.containsKey(playerId)) {
            return settingsCache.get(playerId);
        }

        EntityManager em = databaseManager.getEntityManager();
        try {
            PlayerSettings settings = em.find(PlayerSettings.class, playerId);
            if (settings == null) {
                settings = new PlayerSettings(playerId);
            }
            settingsCache.put(playerId, settings);
            return settings;
        } finally {
            em.close();
        }
    }

    public void saveSettings(PlayerSettings settings) {
        settingsCache.put(settings.getPlayerId(), settings);

        EntityManager em = databaseManager.getEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(settings);
            em.getTransaction().commit();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe(i18n.log("internal.storage.player_settings.save_failed", e.getMessage()));
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
        } finally {
            em.close();
        }
    }

    public boolean isScoreboardEnabled(UUID playerId) {
        return loadSettings(playerId).isScoreboardEnabled();
    }

    public void setScoreboardEnabled(UUID playerId, boolean enabled) {
        PlayerSettings settings = loadSettings(playerId);
        settings.setScoreboardEnabled(enabled);
        saveSettings(settings);
    }

    public boolean isMusicEnabled(UUID playerId) {
        return loadSettings(playerId).isMusicEnabled();
    }

    public void setMusicEnabled(UUID playerId, boolean enabled) {
        PlayerSettings settings = loadSettings(playerId);
        settings.setMusicEnabled(enabled);
        saveSettings(settings);
    }

    public void removePlayer(UUID playerId) {
        settingsCache.remove(playerId);
    }

    public void close() {
        databaseManager.close();
    }
}
