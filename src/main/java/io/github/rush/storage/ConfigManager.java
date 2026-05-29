package io.github.rush.storage;

import io.github.rush.Main;
import io.github.rush.utils.i18n;
import io.github.rush.utils.RushLogger;
import lombok.Getter;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;

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

        final InputStream defaultStream = plugin.getResource("config.yml");
        if (defaultStream != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
        }
    }

    /**
     * Saves the current configuration to disk.
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            RushLogger.warn(i18n.log("internal.storage.config.save_failed", e.getMessage()));
        }
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reloadConfig() {
        loadConfig();
    }

    /**
     * Returns the schematic {@link File} for {@code filename} under the shared
     * FastAsyncWorldEdit schematics directory, or {@code null} if it does not
     * exist.
     */
    public File getSchematicFile(String filename) {
        final File schematicFile = new File(plugin.getDataFolder().getParentFile(),
                "FastAsyncWorldEdit/schematics/" + filename);

        if (!schematicFile.exists()) {
            RushLogger.warn(i18n.log("internal.game_manager.schematic_not_found", schematicFile.getPath()));
            return null;
        }

        return schematicFile;
    }

    /**
     * Reads the duration in milliseconds of an OGG entry inside a ZIP archive.
     * Returns {@code -1} if the file or entry is missing, or on any read error.
     */
    public static long readOggDurationFromZip(File zipFile, String entryPath) {
        if (!zipFile.exists())
            return -1;
        File temp = null;
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null)
                return -1;
            temp = File.createTempFile("rush_ogg_", ".ogg");
            try (InputStream in = zip.getInputStream(entry);
                    FileOutputStream out = new FileOutputStream(temp)) {
                in.transferTo(out);
            }
            AudioHeader header = AudioFileIO.read(temp).getAudioHeader();
            return (long) (header.getPreciseTrackLength() * 1000);
        } catch (Exception e) {
            return -1;
        } finally {
            if (temp != null)
                temp.delete();
        }
    }

    /** Recursively deletes a directory and all its contents. */
    public static void deleteDirectory(File directory) {
        if (!directory.exists())
            return;
        final File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

}
