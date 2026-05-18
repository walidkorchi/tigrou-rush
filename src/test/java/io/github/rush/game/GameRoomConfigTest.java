package io.github.rush.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameRoomConfigTest {

    @Test
    void maxTeamsBelowTwoIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new GameRoomConfig(GameRoom.IslandType.FOUR_ISLANDS, 1,
                        GameRoom.TeamSize.VS2, MapType.NORMAL, false, false));
    }

    @Test
    void maxTeamsExceedingIslandCapIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new GameRoomConfig(GameRoom.IslandType.FOUR_ISLANDS, 5,
                        GameRoom.TeamSize.VS2, MapType.NORMAL, false, false));
    }

    @Test
    void maxTeamsAtIslandCapIsAccepted() {
        assertDoesNotThrow(() ->
                new GameRoomConfig(GameRoom.IslandType.FOUR_ISLANDS, 4,
                        GameRoom.TeamSize.VS4, MapType.NORMAL, false, false));
    }

    @Test
    void eightIslandCapAllowsUpToEightTeams() {
        assertDoesNotThrow(() ->
                new GameRoomConfig(GameRoom.IslandType.EIGHT_ISLANDS, 8,
                        GameRoom.TeamSize.VS2, MapType.NORMAL, false, false));
    }

    @Test
    void fieldsAreAccessibleAfterConstruction() {
        GameRoomConfig config = new GameRoomConfig(
                GameRoom.IslandType.FOUR_ISLANDS, 2,
                GameRoom.TeamSize.VS2, MapType.NORMAL,
                false, false);

        assertEquals(GameRoom.IslandType.FOUR_ISLANDS, config.islandType());
        assertEquals(2, config.maxTeams());
        assertEquals(GameRoom.TeamSize.VS2, config.teamSize());
        assertEquals(MapType.NORMAL, config.mapType());
        assertFalse(config.extraHearts());
        assertFalse(config.overtimeStart());
    }
}
