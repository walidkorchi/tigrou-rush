package io.github.rush.settings;

import java.util.UUID;

public class PlayerSettings {

    private final UUID playerId;
    private boolean scoreboardEnabled;
    private boolean musicEnabled;

    public PlayerSettings(UUID playerId) {
        this.playerId = playerId;
        this.scoreboardEnabled = true;
        this.musicEnabled = true;
    }

    public PlayerSettings(UUID playerId, boolean scoreboardEnabled, boolean musicEnabled) {
        this.playerId = playerId;
        this.scoreboardEnabled = scoreboardEnabled;
        this.musicEnabled = musicEnabled;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public void setScoreboardEnabled(boolean scoreboardEnabled) {
        this.scoreboardEnabled = scoreboardEnabled;
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public void setMusicEnabled(boolean musicEnabled) {
        this.musicEnabled = musicEnabled;
    }
}
