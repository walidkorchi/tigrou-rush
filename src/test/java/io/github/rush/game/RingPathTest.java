package io.github.rush.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingPathTest {

    // 4-island layout in cyclic order: N(0,-40) → E(40,0) → S(0,40) → W(-40,0)
    private static final double[] FOUR_X = {  0, 40,  0, -40 };
    private static final double[] FOUR_Z = { -40,  0, 40,   0 };

    // ── Bridge midpoints are on the path ────────────────────────────────────

    @Test
    void midpointOfNorthEastBridgeIsOnPath() {
        // Midpoint of N(0,-40)→E(40,0) segment is (20,-20)
        assertTrue(RingPath.isOnPath(20, -20, FOUR_X, FOUR_Z));
    }

    @Test
    void midpointOfEveryAdjacentBridgeIsOnPath() {
        // N→E (20,-20), E→S (20,20), S→W (-20,20), W→N (-20,-20)
        assertTrue(RingPath.isOnPath( 20, -20, FOUR_X, FOUR_Z));
        assertTrue(RingPath.isOnPath( 20,  20, FOUR_X, FOUR_Z));
        assertTrue(RingPath.isOnPath(-20,  20, FOUR_X, FOUR_Z));
        assertTrue(RingPath.isOnPath(-20, -20, FOUR_X, FOUR_Z));
    }

    @Test
    void pointOffsetFromBridgeButWithinHalfWidthIsOnPath() {
        // (18,-18) is shifted ~2.83 blocks inward (perpendicular) from the
        // N→E bridge midpoint (20,-20), well within BRIDGE_HALF_WIDTH=4.
        // Projection t=0.5, closest=(20,-20), dist=sqrt(8)≈2.83 < 4.
        assertTrue(RingPath.isOnPath(18, -18, FOUR_X, FOUR_Z));
    }

    // ── Diamond interior is forbidden ────────────────────────────────────────

    @Test
    void mapCenterIsNotOnPath() {
        // (0,0) is the centre of the diamond — no bridge passes through it.
        assertFalse(RingPath.isOnPath(0, 0, FOUR_X, FOUR_Z));
    }

    @Test
    void diamondInteriorPointIsNotOnPath() {
        // (10,10) is well inside the diamond; closest bridge (E→S) is ~14.1 blocks away.
        assertFalse(RingPath.isOnPath(10, 10, FOUR_X, FOUR_Z));
    }

    // ── Cardinal axes toward islands are forbidden ───────────────────────────

    @Test
    void cardinalMidpointsBetweenOriginAndIslandsAreNotOnPath() {
        // Halfway between origin and each island: on the axial axis, ~14.1 blocks
        // from the nearest diagonal bridge → forbidden.
        assertFalse(RingPath.isOnPath(  0, -20, FOUR_X, FOUR_Z)); // toward N
        assertFalse(RingPath.isOnPath( 20,   0, FOUR_X, FOUR_Z)); // toward E
        assertFalse(RingPath.isOnPath(  0,  20, FOUR_X, FOUR_Z)); // toward S
        assertFalse(RingPath.isOnPath(-20,   0, FOUR_X, FOUR_Z)); // toward W
    }

    // ── No island-radius escape hatch ────────────────────────────────────────
    //
    // The previous angular model added a 15-block radius around each island
    // that unconditionally returned "buildable". That created the spurious
    // open wedges on the axial sides of each island in the debug visualisation.
    // The new model has no such special case.

    @Test
    void pointBetweenOriginAndIslandIsNotOnPath() {
        // (0,-25) is on the N axial axis, 15 blocks from N island and ~10.6 blocks
        // from the closest bridge. The old 15-radius hatch would have allowed it;
        // the new model correctly forbids it.
        assertFalse(RingPath.isOnPath(0, -25, FOUR_X, FOUR_Z));
    }

    @Test
    void pointBehindIslandIsNotOnPath() {
        // (0,-45) is 5 blocks north (void-side) of the N island centre.
        // The nearest bridge endpoint is N=(0,-40) at distance 5, which exceeds
        // BRIDGE_HALF_WIDTH=4, so it is forbidden.
        assertFalse(RingPath.isOnPath(0, -45, FOUR_X, FOUR_Z));
    }

    // ── Island centres are on the path (segment endpoints) ───────────────────

    @Test
    void islandCentresAreOnPath() {
        // Each island centre is the endpoint of two adjacent bridge segments;
        // perpendicular distance to those segments is 0.
        assertTrue(RingPath.isOnPath(  0, -40, FOUR_X, FOUR_Z)); // N
        assertTrue(RingPath.isOnPath( 40,   0, FOUR_X, FOUR_Z)); // E
        assertTrue(RingPath.isOnPath(  0,  40, FOUR_X, FOUR_Z)); // S
        assertTrue(RingPath.isOnPath(-40,   0, FOUR_X, FOUR_Z)); // W
    }

    // ── Outer corners are forbidden ──────────────────────────────────────────

    @Test
    void pointFarOutsideDiamondIsNotOnPath() {
        assertFalse(RingPath.isOnPath(50, 50, FOUR_X, FOUR_Z));
    }

    @Test
    void cornersBeyondIslandAreNotOnPath() {
        // Far NE, SE, SW, NW corners — well outside every bridge segment.
        assertFalse(RingPath.isOnPath( 50, -50, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath( 50,  50, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath(-50,  50, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath(-50, -50, FOUR_X, FOUR_Z));
    }

    // ── Degenerate input is permissive ───────────────────────────────────────

    @Test
    void degenerateInputIsPermissive() {
        // Fewer than 2 islands or mismatched arrays → always returns true.
        assertTrue(RingPath.isOnPath(0, 0, new double[]{0}, new double[]{0}));
        assertTrue(RingPath.isOnPath(0, 0, new double[]{0}, new double[]{}));
    }

    // ── Symmetry: opposite bridges behave identically ────────────────────────

    @Test
    void symmetricBridgeMidpointsAllOnPath() {
        // All four midpoints equidistant from origin, each on its own bridge.
        assertTrue(RingPath.isOnPath( 20, -20, FOUR_X, FOUR_Z));
        assertTrue(RingPath.isOnPath(-20,  20, FOUR_X, FOUR_Z));
        assertTrue(RingPath.isOnPath( 20,  20, FOUR_X, FOUR_Z));
        assertTrue(RingPath.isOnPath(-20, -20, FOUR_X, FOUR_Z));
    }

    @Test
    void symmetricForbiddenInteriorPoints() {
        // Points symmetric about origin, all in the diamond interior.
        assertFalse(RingPath.isOnPath( 15,  0, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath(-15,  0, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath( 0,  15, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath( 0, -15, FOUR_X, FOUR_Z));
    }
}
