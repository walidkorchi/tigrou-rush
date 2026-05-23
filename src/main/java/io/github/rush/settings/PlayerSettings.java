package io.github.rush.settings;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

public class PlayerSettings {

    @Getter
    private final UUID playerId;
    @Getter @Setter
    private boolean scoreboardEnabled;
    @Getter @Setter
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

}
