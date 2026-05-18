package io.github.rush.game;

public record GameRoomConfig(
        GameRoom.IslandType islandType,
        int maxTeams,
        GameRoom.TeamSize teamSize,
        MapType mapType,
        boolean extraHearts,
        boolean overtimeStart) {

    public GameRoomConfig {
        if (maxTeams < 2 || maxTeams > islandType.getCount()) {
            throw new IllegalArgumentException(
                    "maxTeams must be between 2 and " + islandType.getCount() + ", got " + maxTeams);
        }
    }
}
