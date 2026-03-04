package io.github.rush.settings;

import io.github.rush.Main;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSettingsManager {

    private final Main plugin;
    private final File settingsFile;
    private final Map<UUID, PlayerSettings> settingsCache;

    public PlayerSettingsManager(Main plugin) {
        this.plugin = plugin;
        this.settingsFile = new File(plugin.getDataFolder(), "player_settings.yml");
        this.settingsCache = new HashMap<>();

        ensureFileExists();
    }

    private void ensureFileExists() {
        if (!settingsFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                settingsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create player_settings.yml: " + e.getMessage());
            }
        }
    }

    public PlayerSettings loadSettings(UUID playerId) {
        if (settingsCache.containsKey(playerId)) {
            return settingsCache.get(playerId);
        }

        final YamlConfiguration config = YamlConfiguration.loadConfiguration(settingsFile);
        final String path = playerId.toString();

        final boolean scoreboardEnabled = config.getBoolean(path + ".scoreboard", true);
        final boolean musicEnabled = config.getBoolean(path + ".music", true);

        final PlayerSettings settings = new PlayerSettings(playerId, scoreboardEnabled, musicEnabled);

        settingsCache.put(playerId, settings);

        return settings;
    }

    public void saveSettings(PlayerSettings settings) {
        settingsCache.put(settings.getPlayerId(), settings);

        final YamlConfiguration config = YamlConfiguration.loadConfiguration(settingsFile);
        final String path = settings.getPlayerId().toString();

        config.set(path + ".scoreboard", settings.isScoreboardEnabled());
        config.set(path + ".music", settings.isMusicEnabled());

        try {
            config.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player settings: " + e.getMessage());
        }
    }

    public boolean isScoreboardEnabled(UUID playerId) {
        return loadSettings(playerId).isScoreboardEnabled();
    }

    public void setScoreboardEnabled(UUID playerId, boolean enabled) {
        final PlayerSettings settings = loadSettings(playerId);

        settings.setScoreboardEnabled(enabled);
        saveSettings(settings);
    }

    public boolean isMusicEnabled(UUID playerId) {
        return loadSettings(playerId).isMusicEnabled();
    }

    public void setMusicEnabled(UUID playerId, boolean enabled) {
        final PlayerSettings settings = loadSettings(playerId);

        settings.setMusicEnabled(enabled);
        saveSettings(settings);
    }

    /**
     * Removes a player from the settings cache to prevent memory leaks.
     * Should be called when a player quits.
     */
    public void removePlayer(UUID playerId) {
        settingsCache.remove(playerId);
    }

}
