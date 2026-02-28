package io.github.rush.utils;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.model.Song;
import dev.geco.gmusic.service.RadioService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MusicManager {

    private static final String LOBBY_SONG_ID = "rush_lobby";

    private final JavaPlugin plugin;
    private RadioService radioService;
    private Song lobbySong;
    private boolean initialized = false;

    public MusicManager(JavaPlugin plugin) {
        this.plugin = plugin;

        if (!plugin.getServer().getPluginManager().isPluginEnabled("GMusic")) {
            plugin.getLogger().warning("GMusic is not installed. Music disabled.");
            return;
        }

        initialize();
    }

    private void initialize() {
        try {
            final GMusicMain gMusic = GMusicMain.getInstance();

            radioService = gMusic.getRadioService();
            lobbySong = gMusic.getSongService().getSongById(LOBBY_SONG_ID);

            if (lobbySong != null) {
                initialized = true;
                plugin.getLogger().info("Lobby music loaded: " + LOBBY_SONG_ID);
            } else {
                plugin.getLogger().warning("Lobby song not found in GMusic: " + LOBBY_SONG_ID);
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize GMusic: " + e.getMessage());
        }
    }

    public void playForPlayer(Player player) {
        if (initialized && radioService != null && lobbySong != null) {
            radioService.playSong(lobbySong);
            radioService.addRadioPlayer(player);
        }
    }

    public void stopForPlayer(Player player) {
        if (initialized && radioService != null) {
            radioService.removeRadioPlayer(player);
        }
    }
}
