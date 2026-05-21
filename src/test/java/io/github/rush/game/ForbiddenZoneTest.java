package io.github.rush.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForbiddenZoneTest {

    // ── 4-island, two playing teams on adjacent islands ─────────────────────
    //
    // PREFERRED_ISLAND_ORDER places the two playing teams on adjacent islands
    // (S and E for the 2-team case). The forbidden corridor follows the same
    // offset-endpoint geometry the ring uses:
    // - endpoint on S (toward E): (+6, +38)
    // - endpoint on E (toward S): (+38, +6)
    // Half-width = RingPath.BRIDGE_SPREAD (5 blocks).

    private static final double SX = 0, SZ = 40; // South (team A)
    private static final double EX = 40, EZ = 0; // East (team B)

    @Test
    void midpointOfOffsetCorridorIsBlocked() {
        // Midpoint of (6,38)→(38,6) is (22,22)
        assertTrue(ForbiddenZone.isBlocked(22, 22, SX, SZ, EX, EZ, 4));
    }

    @Test
    void bridgeEndpointsAreBlocked() {
        assertTrue(ForbiddenZone.isBlocked(6, 38, SX, SZ, EX, EZ, 4));
        assertTrue(ForbiddenZone.isBlocked(38, 6, SX, SZ, EX, EZ, 4));
    }

    @Test
    void islandCenterItselfIsNotBlocked() {
        // The corridor stops ~6.3 blocks from each island center — outside the
        // 4-block spread.
        assertFalse(ForbiddenZone.isBlocked(SX, SZ, SX, SZ, EX, EZ, 4));
        assertFalse(ForbiddenZone.isBlocked(EX, EZ, SX, SZ, EX, EZ, 4));
    }

    @Test
    void pointWellInsideDiamondIsNotBlocked() {
        // (15, 15) is well inside the diamond; offset bridge line x+z=44,
        // perpendicular distance ≈ 9.9 > 4.
        assertFalse(ForbiddenZone.isBlocked(15, 15, SX, SZ, EX, EZ, 4));
    }

    @Test
    void pointOnOppositeBridgeIsNotBlocked() {
        // Midpoint of the N→W offset bridge is around (-22, -22)
        assertFalse(ForbiddenZone.isBlocked(-22, -22, SX, SZ, EX, EZ, 4));
    }

    @Test
    void mapCenterIsNotBlocked() {
        assertFalse(ForbiddenZone.isBlocked(0, 0, SX, SZ, EX, EZ, 4));
    }

    @Test
    void pointFarPerpendicularToCorridorIsNotBlocked() {
        // (0, 30): distance to corridor endpoint (6,38) is √(36+64) = 10 > 4.
        assertFalse(ForbiddenZone.isBlocked(0, 30, SX, SZ, EX, EZ, 4));
    }
}
