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
    private final Map<UUID, PlayerSettings> settingsCache = new HashMap<>();

    public PlayerSettingsManager(Main plugin) {
        this.plugin = plugin;
        this.settingsFile = new File(plugin.getDataFolder(), "player_settings.yml");
        if (!settingsFile.exists()) {
            plugin.saveResource("player_settings.yml", false);
        }
    }

    public PlayerSettings loadSettings(UUID playerId) {
        if (settingsCache.containsKey(playerId)) {
            return settingsCache.get(playerId);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(settingsFile);
        String path = playerId.toString();

        boolean scoreboardEnabled = config.getBoolean(path + ".scoreboard", true);

        PlayerSettings settings = new PlayerSettings(playerId, scoreboardEnabled);
        settingsCache.put(playerId, settings);

        return settings;
    }

    public void saveSettings(PlayerSettings settings) {
        settingsCache.put(settings.getPlayerId(), settings);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(settingsFile);
        String path = settings.getPlayerId().toString();

        config.set(path + ".scoreboard", settings.isScoreboardEnabled());

        try {
            config.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player settings: " + e.getMessage());
        }
    }

    public boolean isScoreboardEnabled(UUID playerId) {
        PlayerSettings settings = loadSettings(playerId);
        return settings.isScoreboardEnabled();
    }

    public void setScoreboardEnabled(UUID playerId, boolean enabled) {
        PlayerSettings settings = loadSettings(playerId);
        settings.setScoreboardEnabled(enabled);
        saveSettings(settings);
    }
}
