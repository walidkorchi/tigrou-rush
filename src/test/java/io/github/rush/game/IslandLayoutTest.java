package io.github.rush.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IslandLayoutTest {

    private static final int OFFSET = 40;
    private static final double DIAG = Math.round(OFFSET * Math.cos(Math.PI / 4));

    // ── Four-island (cardinal) ────────────────────────────────────────────────

    @Test
    void fourIslandProducesFourPositions() {
        assertEquals(4, IslandLayout.positionsFor(GameRoom.IslandType.FOUR_ISLANDS, OFFSET).size());
    }

    @Test
    void fourIslandNorthIsAtNegativeZ() {
        IslandLayout.IslandPosition north = IslandLayout.positionsFor(GameRoom.IslandType.FOUR_ISLANDS, OFFSET).get(0);
        assertEquals(0, north.x());
        assertEquals(-OFFSET, north.z());
    }

    @Test
    void fourIslandEastIsAtPositiveX() {
        IslandLayout.IslandPosition east = IslandLayout.positionsFor(GameRoom.IslandType.FOUR_ISLANDS, OFFSET).get(1);
        assertEquals(OFFSET, east.x());
        assertEquals(0, east.z());
    }

    @Test
    void fourIslandSouthIsAtPositiveZ() {
        IslandLayout.IslandPosition south = IslandLayout.positionsFor(GameRoom.IslandType.FOUR_ISLANDS, OFFSET).get(2);
        assertEquals(0, south.x());
        assertEquals(OFFSET, south.z());
    }

    @Test
    void fourIslandWestIsAtNegativeX() {
        IslandLayout.IslandPosition west = IslandLayout.positionsFor(GameRoom.IslandType.FOUR_ISLANDS, OFFSET).get(3);
        assertEquals(-OFFSET, west.x());
        assertEquals(0, west.z());
    }

    // ── Eight-island (cardinal + diagonal) ───────────────────────────────────

    @Test
    void eightIslandProducesEightPositions() {
        assertEquals(8, IslandLayout.positionsFor(GameRoom.IslandType.EIGHT_ISLANDS, OFFSET).size());
    }

    @Test
    void eightIslandCardinalPositionsMatchFourIsland() {
        List<IslandLayout.IslandPosition> eight = IslandLayout.positionsFor(GameRoom.IslandType.EIGHT_ISLANDS, OFFSET);
        // Cardinals are at indices 0 (N), 2 (E), 4 (S), 6 (W)
        assertEquals(0,       eight.get(0).x()); assertEquals(-OFFSET, eight.get(0).z()); // N
        assertEquals(OFFSET,  eight.get(2).x()); assertEquals(0,       eight.get(2).z()); // E
        assertEquals(0,       eight.get(4).x()); assertEquals(OFFSET,  eight.get(4).z()); // S
        assertEquals(-OFFSET, eight.get(6).x()); assertEquals(0,       eight.get(6).z()); // W
    }

    @Test
    void eightIslandDiagonalsAreAtFortyFiveDegrees() {
        List<IslandLayout.IslandPosition> eight = IslandLayout.positionsFor(GameRoom.IslandType.EIGHT_ISLANDS, OFFSET);
        int d = (int) DIAG;
        // NE (index 1): +x, -z
        assertEquals(d,  eight.get(1).x()); assertEquals(-d, eight.get(1).z());
        // SE (index 3): +x, +z
        assertEquals(d,  eight.get(3).x()); assertEquals(d,  eight.get(3).z());
        // SW (index 5): -x, +z
        assertEquals(-d, eight.get(5).x()); assertEquals(d,  eight.get(5).z());
        // NW (index 7): -x, -z
        assertEquals(-d, eight.get(7).x()); assertEquals(-d, eight.get(7).z());
    }

    @Test
    void eightIslandDiagonalsAreAtSameDistanceFromCenterAsCardinals() {
        List<IslandLayout.IslandPosition> eight = IslandLayout.positionsFor(GameRoom.IslandType.EIGHT_ISLANDS, OFFSET);
        for (IslandLayout.IslandPosition pos : eight) {
            double dist = Math.sqrt((double) pos.x() * pos.x() + (double) pos.z() * pos.z());
            assertEquals(OFFSET, dist, 1.0, "Position (" + pos.x() + "," + pos.z() + ") should be ~40 from center");
        }
    }
}
