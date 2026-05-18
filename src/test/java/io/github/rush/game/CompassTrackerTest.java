package io.github.rush.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompassTrackerTest {

    private static final UUID TEAM_RED  = UUID.randomUUID();
    private static final UUID TEAM_BLUE = UUID.randomUUID();

    @Test
    void singleEnemyIsReturned() {
        var enemy = new CompassTracker.Candidate(TEAM_BLUE, 10, 0, 0);
        var result = CompassTracker.findNearestEnemy(0, 0, 0, TEAM_RED, List.of(enemy));
        assertEquals(enemy, result);
    }

    @Test
    void nearestOfTwoEnemiesIsReturned() {
        var close = new CompassTracker.Candidate(TEAM_BLUE, 5,  0, 0);
        var far   = new CompassTracker.Candidate(TEAM_BLUE, 50, 0, 0);
        var result = CompassTracker.findNearestEnemy(0, 0, 0, TEAM_RED, List.of(far, close));
        assertEquals(close, result);
    }

    @Test
    void teammateIsIgnoredEvenIfCloser() {
        var teammate = new CompassTracker.Candidate(TEAM_RED,  2,  0, 0);
        var enemy    = new CompassTracker.Candidate(TEAM_BLUE, 20, 0, 0);
        var result = CompassTracker.findNearestEnemy(0, 0, 0, TEAM_RED, List.of(teammate, enemy));
        assertEquals(enemy, result);
    }

    @Test
    void emptyListReturnsNull() {
        assertNull(CompassTracker.findNearestEnemy(0, 0, 0, TEAM_RED, List.of()));
    }

    @Test
    void allTeammatesReturnsNull() {
        var a = new CompassTracker.Candidate(TEAM_RED, 5,  0, 0);
        var b = new CompassTracker.Candidate(TEAM_RED, 10, 0, 0);
        assertNull(CompassTracker.findNearestEnemy(0, 0, 0, TEAM_RED, List.of(a, b)));
    }

    @Test
    void distanceIsThreeDimensional() {
        // far in X but same horizontal plane
        var horizontalFar  = new CompassTracker.Candidate(TEAM_BLUE, 100, 0,   0);
        // close in X but far above (Y=50) → actually farther in 3D
        var verticalFar    = new CompassTracker.Candidate(TEAM_BLUE, 0,   50,  0);
        // nearest in 3D
        var nearest        = new CompassTracker.Candidate(TEAM_BLUE, 3,   4,   0); // dist=5
        var result = CompassTracker.findNearestEnemy(0, 0, 0, TEAM_RED,
                List.of(horizontalFar, verticalFar, nearest));
        assertEquals(nearest, result);
    }
}
