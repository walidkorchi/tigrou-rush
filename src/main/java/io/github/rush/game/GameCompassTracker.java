package io.github.rush.game;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class GameCompassTracker {

    private GameCompassTracker() {}

    public record Candidate(UUID teamId, double x, double y, double z) {}

    public static @Nullable Candidate findNearestEnemy(
            double holderX, double holderY, double holderZ,
            UUID holderTeamId,
            List<Candidate> candidates) {
        Candidate nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Candidate c : candidates) {
            if (c.teamId().equals(holderTeamId)) continue;

            final double dx = c.x() - holderX;
            final double dy = c.y() - holderY;
            final double dz = c.z() - holderZ;
            final double dist = dx * dx + dy * dy + dz * dz;

            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = c;
            }
        }

        return nearest;
    }
}
