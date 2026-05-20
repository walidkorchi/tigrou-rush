package io.github.rush.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForbiddenZoneTest {

    // ── 4-island (half-angle = π/8 = 22.5°) ─────────────────────────────────

    // Teams at E (40,0) and S (0,40) → midpoint angle = 45°
    private static final double A4X = 40, A4Z = 0;
    private static final double B4X = 0,  B4Z = 40;

    @Test
    void blockAtMidpointAngleIsBlocked() {
        assertTrue(ForbiddenZone.isBlocked(10, 10, A4X, A4Z, B4X, B4Z, 4));
    }

    @Test
    void blockAtBoundaryIsNotBlocked() {
        // Block at (10,0) is exactly 45° from 45° midline → not strictly inside
        assertFalse(ForbiddenZone.isBlocked(10, 0, A4X, A4Z, B4X, B4Z, 4));
    }

    @Test
    void blockJustInsideBoundaryIsBlocked() {
        // (10,7) ≈ 35° — clearly inside ±45° wedge around 45° midline
        assertTrue(ForbiddenZone.isBlocked(10, 7, A4X, A4Z, B4X, B4Z, 4));
    }

    @Test
    void blockClearlyOutsideWedgeIsNotBlocked() {
        // (-10,0) = 180° — 135° from 45° midline
        assertFalse(ForbiddenZone.isBlocked(-10, 0, A4X, A4Z, B4X, B4Z, 4));
    }

    @Test
    void angleWraparoundNearPiIsHandledCorrectly() {
        // Teams near ±180°: NW (-40,1) and SW (-40,-1), midline = 180°
        assertTrue(ForbiddenZone.isBlocked(-10, 2, -40, 1, -40, -1, 4));
        assertFalse(ForbiddenZone.isBlocked(10, 0, -40, 1, -40, -1, 4));
    }

    // ── 8-island (half-angle = π/8 = 22.5°) ──────────────────────────────────

    // Same spawn positions, but narrower wedge
    private static final double A8X = 40, A8Z = 0;
    private static final double B8X = 0,  B8Z = 40;

    @Test
    void eightIslandBlockAtMidpointIsBlocked() {
        // (10,10) sits on 45° midline — still inside ±22.5° wedge
        assertTrue(ForbiddenZone.isBlocked(10, 10, A8X, A8Z, B8X, B8Z, 8));
    }

    @Test
    void eightIslandBlockJustOutsideNarrowerWedgeIsNotBlocked() {
        // (2,10): atan2(10,2) ≈ 79° → 34° from midline
        // 4-island: 34° > 22.5° → NOT blocked; 8-island: 34° > 11.25° → NOT blocked
        assertFalse(ForbiddenZone.isBlocked(2, 10, A8X, A8Z, B8X, B8Z, 4));
        assertFalse(ForbiddenZone.isBlocked(2, 10, A8X, A8Z, B8X, B8Z, 8));
    }

    @Test
    void eightIslandBlockTightlyOnMidlineIsBlocked() {
        // (10,11): atan2(11,10) ≈ 47.7° → 2.7° from 45° midline → inside ±22.5°
        assertTrue(ForbiddenZone.isBlocked(10, 11, A8X, A8Z, B8X, B8Z, 8));
    }

    @Test
    void eightIslandBlockClearlyOutsideIsNotBlocked() {
        // (-10,0) = 180° — 135° from midline, well outside ±22.5°
        assertFalse(ForbiddenZone.isBlocked(-10, 0, A8X, A8Z, B8X, B8Z, 8));
    }
}
