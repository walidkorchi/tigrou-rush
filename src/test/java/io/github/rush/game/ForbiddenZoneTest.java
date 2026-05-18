package io.github.rush.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForbiddenZoneTest {

    // Teams at E (40,0) and S (0,40) → midpoint angle = 45°, half-angle = 45° for 4 islands
    private static final double SPAWN_A_X = 40, SPAWN_A_Z = 0;  // East
    private static final double SPAWN_B_X = 0,  SPAWN_B_Z = 40; // South

    @Test
    void blockAtMidpointAngleIsBlocked() {
        // Block at (10,10) sits exactly on the 45° midpoint angle
        assertTrue(ForbiddenZone.isBlocked(10, 10, SPAWN_A_X, SPAWN_A_Z, SPAWN_B_X, SPAWN_B_Z, 4));
    }

    @Test
    void blockNinetyDegreesAwayIsNotBlocked() {
        // Block at (10,0) is at 0° — 45° from the 45° midline, at the boundary: not strictly inside
        assertFalse(ForbiddenZone.isBlocked(10, 0, SPAWN_A_X, SPAWN_A_Z, SPAWN_B_X, SPAWN_B_Z, 4));
    }

    @Test
    void blockJustInsideBoundaryIsBlocked() {
        // Block at (10,7) ≈ 35° — clearly inside the ±45° wedge around 45° midline
        assertTrue(ForbiddenZone.isBlocked(10, 7, SPAWN_A_X, SPAWN_A_Z, SPAWN_B_X, SPAWN_B_Z, 4));
    }

    @Test
    void blockClearlyOutsideWedgeIsNotBlocked() {
        // Block at (-10,0) is at 180° — 135° from the 45° midline, well outside
        assertFalse(ForbiddenZone.isBlocked(-10, 0, SPAWN_A_X, SPAWN_A_Z, SPAWN_B_X, SPAWN_B_Z, 4));
    }

    @Test
    void islandCountNarrowsWedgeForEightIslandMap() {
        // Teams at E and S → midline 45°. Block at ~80° is 35° from midline.
        // Block at (2,10): atan2(10,2) ≈ 79° → 34° from the 45° midline
        // 4-island half-angle = 45° → 34° < 45° → blocked
        assertTrue(ForbiddenZone.isBlocked(2, 10, SPAWN_A_X, SPAWN_A_Z, SPAWN_B_X, SPAWN_B_Z, 4));
        // 8-island half-angle = 22.5° → 34° > 22.5° → NOT blocked
        assertFalse(ForbiddenZone.isBlocked(2, 10, SPAWN_A_X, SPAWN_A_Z, SPAWN_B_X, SPAWN_B_Z, 8));
    }

    @Test
    void angleWraparoundNearPiIsHandledCorrectly() {
        // Teams near the ±180° boundary: NW (-40,1) and SW (-40,-1)
        // Midpoint (-40,0) → midline angle = 180°
        // Block at (-10,2) ≈ 169° → 11° from midline → inside ±45° wedge → blocked
        assertTrue(ForbiddenZone.isBlocked(-10, 2, -40, 1, -40, -1, 4));
        // Block at (10,0) = 0° → 180° from midline → outside → not blocked
        assertFalse(ForbiddenZone.isBlocked(10, 0, -40, 1, -40, -1, 4));
    }
}
