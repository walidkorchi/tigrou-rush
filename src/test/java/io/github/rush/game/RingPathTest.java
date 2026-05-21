package io.github.rush.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingPathTest {

    // 4-island layout: N (0,-40) → E (40,0) → S (0,40) → W (-40,0), cyclic.
    private static final double[] FOUR_X = { 0, 40, 0, -40 };
    private static final double[] FOUR_Z = { -40, 0, 40, 0 };

    // ── Bridge endpoint computation ─────────────────────────────────────────
    //
    // For S (0, +40): axial toward origin = (0, -1), tangential axes ±(1, 0)
    // - endpoint toward E (40, 0): (+6, +38)
    // - endpoint toward W (-40, 0): (-6, +38)
    // Symmetrically for E (+40, 0):
    // - endpoint toward S: (+38, +6)
    // - endpoint toward N: (+38, -6)

    @Test
    void southBridgeEndpointTowardEastIsAtPositiveSixThirtyEight() {
        double[] ep = RingPath.bridgeEndpoint(0, 40, 40, 0);
        assertEquals(6.0, ep[0], 1e-9);
        assertEquals(38.0, ep[1], 1e-9);
    }

    @Test
    void southBridgeEndpointTowardWestIsAtNegativeSixThirtyEight() {
        double[] ep = RingPath.bridgeEndpoint(0, 40, -40, 0);
        assertEquals(-6.0, ep[0], 1e-9);
        assertEquals(38.0, ep[1], 1e-9);
    }

    @Test
    void eastBridgeEndpointTowardSouthIsAtThirtyEightPositiveSix() {
        double[] ep = RingPath.bridgeEndpoint(40, 0, 0, 40);
        assertEquals(38.0, ep[0], 1e-9);
        assertEquals(6.0, ep[1], 1e-9);
    }

    @Test
    void northBridgeEndpointsAreMirrorOfSouth() {
        double[] toE = RingPath.bridgeEndpoint(0, -40, 40, 0);
        assertEquals(6.0, toE[0], 1e-9);
        assertEquals(-38.0, toE[1], 1e-9);

        double[] toW = RingPath.bridgeEndpoint(0, -40, -40, 0);
        assertEquals(-6.0, toW[0], 1e-9);
        assertEquals(-38.0, toW[1], 1e-9);
    }

    // ── On-path geometry ────────────────────────────────────────────────────

    @Test
    void midpointOfEachAdjacentPairBridgeIsOnPath() {
        // Midpoint of N→E segment = midpoint of (6,-38)→(38,-6) = (22,-22)
        assertTrue(RingPath.isOnPath(22, -22, FOUR_X, FOUR_Z));
        assertTrue(RingPath.isOnPath(22, 22, FOUR_X, FOUR_Z));
        assertTrue(RingPath.isOnPath(-22, 22, FOUR_X, FOUR_Z));
        assertTrue(RingPath.isOnPath(-22, -22, FOUR_X, FOUR_Z));
    }

    @Test
    void bridgeEndpointItselfIsOnPath() {
        // (6, -38) is an endpoint of the N→E bridge
        assertTrue(RingPath.isOnPath(6, -38, FOUR_X, FOUR_Z));
        // (38, -6) is the other endpoint
        assertTrue(RingPath.isOnPath(38, -6, FOUR_X, FOUR_Z));
    }

    @Test
    void pointNearMidpointWithinSpreadIsOnPath() {
        // (20,-22): ~2 blocks from the midpoint (22,-22) of the N→E bridge.
        assertTrue(RingPath.isOnPath(20, -22, FOUR_X, FOUR_Z));
    }

    // ── Triangular gap near each island is forbidden ─────────────────────────
    //
    // The two bridges leaving each island exit through offset endpoints.
    // The triangular region between them on the island's map-facing side is
    // air at island Y and must be forbidden.

    @Test
    void triangularGapBetweenBridgesNearIslandIsForbidden() {
        // Right inside N's map-facing side: (0, -36) sits between the two
        // bridge exits at (+6,-38) and (-6,-38). Distance to either segment
        // is ≈ 6 blocks, outside BRIDGE_SPREAD = 5.
        assertFalse(RingPath.isOnPath(0, -36, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath(0, 36, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath(36, 0, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath(-36, 0, FOUR_X, FOUR_Z));
    }

    // ── Diamond interior and exterior are forbidden ─────────────────────────

    @Test
    void mapCenterIsNotOnPath() {
        assertFalse(RingPath.isOnPath(0, 0, FOUR_X, FOUR_Z));
    }

    @Test
    void diamondInteriorPointIsNotOnPath() {
        // (10, 10): offset bridge line S↔E is x+z=44, so distance ≈ 17 blocks.
        assertFalse(RingPath.isOnPath(10, 10, FOUR_X, FOUR_Z));
    }

    @Test
    void cardinalAxesInsideDiamondAreNotOnPath() {
        assertFalse(RingPath.isOnPath(0, -20, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath(20, 0, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath(0, 20, FOUR_X, FOUR_Z));
        assertFalse(RingPath.isOnPath(-20, 0, FOUR_X, FOUR_Z));
    }

    @Test
    void pointFarOutsideDiamondIsNotOnPath() {
        assertFalse(RingPath.isOnPath(50, 50, FOUR_X, FOUR_Z));
    }

    @Test
    void pointBehindIslandIsNotOnPath() {
        // Beyond the N island, away from origin — no bridge endpoint is within spread.
        assertFalse(RingPath.isOnPath(0, -50, FOUR_X, FOUR_Z));
    }

    // ── Degenerate input is permissive ───────────────────────────────────────

    @Test
    void degenerateInputIsPermissive() {
        assertTrue(RingPath.isOnPath(0, 0, new double[] { 0 }, new double[] { 0 }));
        assertTrue(RingPath.isOnPath(0, 0, new double[] { 0 }, new double[] {}));
    }

    // ── Mirror symmetry ─────────────────────────────────────────────────────

    @Test
    void bridgesAreMirrorSymmetricAcrossCardinalAxes() {
        for (int x = -40; x <= 40; x += 5) {
            for (int z = -40; z <= 40; z += 5) {
                boolean p = RingPath.isOnPath(x, z, FOUR_X, FOUR_Z);
                assertEquals(p, RingPath.isOnPath(-x, z, FOUR_X, FOUR_Z),
                        "mirror across X failed at (" + x + "," + z + ")");
                assertEquals(p, RingPath.isOnPath(x, -z, FOUR_X, FOUR_Z),
                        "mirror across Z failed at (" + x + "," + z + ")");
            }
        }
    }
}
