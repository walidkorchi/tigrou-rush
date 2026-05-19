package io.github.rush.game;

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
            if (maxTeams > islandType.getCount()) maxTeams = islandType.getCount();
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

        public GameRoom.IslandType islandType() { return islandType; }
        public int maxTeams() { return maxTeams; }
        public GameRoom.TeamSize teamSize() { return teamSize; }
        public MapType mapType() { return mapType; }
        public boolean extraHearts() { return extraHearts; }
        public boolean overtimeStart() { return overtimeStart; }
        public int overtimeDuration() { return overtimeDuration; }

        public GameRoomConfig build() {
            return new GameRoomConfig(islandType, maxTeams, teamSize, mapType, extraHearts, overtimeStart, overtimeDuration);
        }
    }
}
