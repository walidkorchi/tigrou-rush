package io.github.rush.config;

import io.github.rush.Main;
import lombok.Getter;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {

    private final Main plugin;
    @Getter
    private FileConfiguration config;
    private final File configFile;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        loadConfig();
    }

    /**
     * Loads and creates if not present the config.yml file.
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.saveDefaultConfig();
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Saves the current configuration to disk.
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save config.yml: " + e.getMessage());
        }
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reloadConfig() {
        loadConfig();
    }

}
