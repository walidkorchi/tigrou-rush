package io.github.rush.geometry;

import io.github.rush.objects.Island;

import java.util.List;

/**
 * Pre-overtime "patched" corridor between the two playing teams' islands.
 * Visualised as orange wool by the {@code /debugzones} command and lifted
 * once the game enters overtime.
 *
 * <h3>4-island / 8-island ring-adjacent teams</h3>
 * <p>
 * When the two teams are 1 hop apart in the ring (which covers the
 * 4-island layout and the default 8-island 2-team pairing), the corridor
 * is the single direct bridge between them, computed via
 * {@link RingPath#bridgeEndpoint}.
 *
 * <h3>8-island non-adjacent teams</h3>
 * <p>
 * When teams are more than 1 hop apart, the corridor is the set of
 * <em>neutral</em> ring bridges — those not directly touching either team's
 * island. With teams at angle-sorted indices {@code ia} and {@code ib},
 * bridges {@code ia-1→ia}, {@code ia→ia+1}, {@code ib-1→ib}, and
 * {@code ib→ib+1} belong to the teams and are exempt; the remaining bridges
 * form the forbidden belt that opens at overtime.
 */
public final class ForbiddenZone {

    private ForbiddenZone() {
    }

    /**
     * @param blockX     block X (world coordinate)
     * @param blockZ     block Z (world coordinate)
     * @param spawnAX    playing team A's spawn X
     * @param spawnAZ    playing team A's spawn Z
     * @param spawnBX    playing team B's spawn X
     * @param spawnBZ    playing team B's spawn Z
     * @param allIslands angle-sorted island list (from
     *                   {@code Game.getAllIslandPositions()})
     */
    public static boolean isBlocked(
            double blockX, double blockZ,
            double spawnAX, double spawnAZ,
            double spawnBX, double spawnBZ,
            List<Island> allIslands) {

        final int n = allIslands.size();
        final double spreadSq = RingPath.BRIDGE_SPREAD * RingPath.BRIDGE_SPREAD;

        // 4-island: teams are always ring-adjacent — check the direct bridge
        if (n <= 4)
            return checkDirectBridge(blockX, blockZ, spawnAX, spawnAZ, spawnBX, spawnBZ, spreadSq);

        // 8-island: determine ring distance between the two teams.
        final int ia = indexInRing(allIslands, spawnAX, spawnAZ);
        final int ib = indexInRing(allIslands, spawnBX, spawnBZ);

        if (ia < 0 || ib < 0)
            return false;

        final int absDiff = Math.abs(ia - ib);
        final int ringDist = Math.min(absDiff, n - absDiff);

        // adjacent teams: only the single direct bridge is forbidden.
        if (ringDist <= 1)
            return checkDirectBridge(blockX, blockZ, spawnAX, spawnAZ, spawnBX, spawnBZ, spreadSq);

        // non-adjacent teams: forbid the neutral bridges (those not adjacent to
        // either team's island).
        for (int i = 0; i < n; i++) {
            if (i == ia || i == (ia - 1 + n) % n)
                continue;
            if (i == ib || i == (ib - 1 + n) % n)
                continue;

            final int j = (i + 1) % n;
            final double[] ea = RingPath.bridgeEndpoint(allIslands.get(i), allIslands.get(j));
            final double[] eb = RingPath.bridgeEndpoint(allIslands.get(j), allIslands.get(i));

            if (RingPath.distanceSqToSegment(blockX, blockZ, ea[0], ea[1], eb[0], eb[1]) <= spreadSq)
                return true;
        }

        return false;
    }

    private static boolean checkDirectBridge(
            double blockX, double blockZ,
            double spawnAX, double spawnAZ,
            double spawnBX, double spawnBZ,
            double spreadSq) {
        final double[] ea = RingPath.bridgeEndpoint(spawnAX, spawnAZ, spawnBX, spawnBZ);
        final double[] eb = RingPath.bridgeEndpoint(spawnBX, spawnBZ, spawnAX, spawnAZ);
        return RingPath.distanceSqToSegment(blockX, blockZ, ea[0], ea[1], eb[0], eb[1]) <= spreadSq;
    }

    /**
     * Returns the index of the island whose center is closest to (spawnX, spawnZ).
     */
    private static int indexInRing(List<Island> islands, double spawnX, double spawnZ) {
        int best = -1;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i < islands.size(); i++) {
            final Island isl = islands.get(i);
            final double d = Math.pow(isl.getX() - spawnX, 2) + Math.pow(isl.getZ() - spawnZ, 2);

            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }

        return best;
    }
}
