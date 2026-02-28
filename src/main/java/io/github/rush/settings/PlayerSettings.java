package io.github.rush.settings;

import java.util.UUID;

public class PlayerSettings {

    private final UUID playerId;
    private boolean scoreboardEnabled;

    public PlayerSettings(UUID playerId) {
        this.playerId = playerId;
        this.scoreboardEnabled = true;
    }

    public PlayerSettings(UUID playerId, boolean scoreboardEnabled) {
        this.playerId = playerId;
        this.scoreboardEnabled = scoreboardEnabled;
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
}
