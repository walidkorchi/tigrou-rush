package io.github.rush.game;

import lombok.Getter;
import lombok.experimental.Accessors;

public record GameRoomConfig(
        GameRoom.IslandType islandType,
        int maxTeams,
        GameRoom.TeamSize teamSize,
        MapType mapType,
        boolean extraHearts,
        boolean overtimeStart,
        int overtimeDuration) {

    public GameRoomConfig {
        if (maxTeams < 2 || maxTeams > islandType.getCount()) {
            throw new IllegalArgumentException(
                    "maxTeams must be between 2 and " + islandType.getCount() + ", got " + maxTeams);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Getter
    @Accessors(fluent = true)
    public static final class Builder {
        private GameRoom.IslandType islandType = GameRoom.IslandType.FOUR_ISLANDS;
        private int maxTeams = 2;
        private GameRoom.TeamSize teamSize = GameRoom.TeamSize.VS2;
        private MapType mapType = MapType.NORMAL;
        private boolean extraHearts = false;
        private boolean overtimeStart = false;
        private int overtimeDuration = io.github.rush.Main.getInstance().getConfig().getInt("overtime-duration", 30);

        public Builder islandType(GameRoom.IslandType islandType) {
            this.islandType = islandType;
            if (maxTeams > islandType.getCount())
                maxTeams = islandType.getCount();
            return this;
        }

        public Builder maxTeams(int delta) {
            int next = maxTeams + delta;
            maxTeams = Math.max(2, Math.min(next, islandType.getCount()));
            return this;
        }

        public Builder teamSize(GameRoom.TeamSize teamSize) {
            this.teamSize = teamSize;
            return this;
        }

        public Builder cycleMapType() {
            MapType[] values = MapType.values();
            mapType = values[(mapType.ordinal() + 1) % values.length];
            return this;
        }

        public Builder extraHearts(boolean extraHearts) {
            this.extraHearts = extraHearts;
            return this;
        }

        public Builder overtimeStart(boolean overtimeStart) {
            this.overtimeStart = overtimeStart;
            return this;
        }

        public Builder overtimeDuration(int delta) {
            overtimeDuration = Math.max(5, Math.min(120, overtimeDuration + delta));
            return this;
        }

        public GameRoomConfig build() {
            return new GameRoomConfig(islandType, maxTeams, teamSize, mapType, extraHearts, overtimeStart,
                    overtimeDuration);
        }
    }

    public double getCoefficient() {
        int t = maxTeams;
        int p = teamSize.getPlayersPerTeam();
        if (t == 2)
            return switch (p) {
                case 1 -> 1.00;
                case 2 -> 1.15;
                case 3 -> 1.25;
                case 4 -> 1.35;
                default -> 1.0;
            };
        if (t == 3)
            return switch (p) {
                case 1 -> 1.05;
                case 2 -> 1.20;
                case 3 -> 1.30;
                case 4 -> 1.40;
                default -> 1.0;
            };
        if (t == 4)
            return switch (p) {
                case 1 -> 1.10;
                case 2 -> 1.25;
                case 3 -> 1.35;
                case 4 -> 1.45;
                default -> 1.0;
            };
        return 1.0;
    }

    public String formatDisplayName() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxTeams; i++) {
            if (i > 0) sb.append("vs");
            sb.append(teamSize.getPlayersPerTeam());
        }
        return sb.toString();
    }
}
