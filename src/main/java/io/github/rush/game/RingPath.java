package io.github.rush.game;

import java.util.List;

/**
 * Determines whether a world position falls inside the ring path — the bridge
 * corridors connecting each island to its adjacent neighbour in the cyclic
 * layout order (N → E → S → W → N for the 4-island map).
 *
 * A position is on the path iff its perpendicular distance to any one of
 * those bridge segments is at most {@link #BRIDGE_HALF_WIDTH}. The segment
 * endpoints ARE the island centres, so islands are covered automatically
 * without a separate radius escape hatch.
 */
public final class RingPath {

    /**
     * Half-width of each bridge corridor in blocks. Total corridor width is
     * {@code 2 × BRIDGE_HALF_WIDTH}. Increase this value if the corridors
     * feel too narrow to build on comfortably; decrease it for tighter
     * visual separation between the four bridges.
     */
    public static final double BRIDGE_HALF_WIDTH = 4.0;

    private RingPath() {}

    /**
     * @param blockX   block X (world coordinate)
     * @param blockZ   block Z (world coordinate)
     * @param islandsX island centre X coordinates in cyclic layout order
     * @param islandsZ island centre Z coordinates, same length and order
     * @return true if the block is on a bridge corridor
     */
    public static boolean isOnPath(double blockX, double blockZ,
                                   double[] islandsX, double[] islandsZ) {
        if (islandsX.length != islandsZ.length || islandsX.length < 2) {
            return true;
        }

        final int n = islandsX.length;
        final double halfWidthSq = BRIDGE_HALF_WIDTH * BRIDGE_HALF_WIDTH;

        for (int i = 0; i < n; i++) {
            final int j = (i + 1) % n;
            if (distanceSqToSegment(blockX, blockZ,
                    islandsX[i], islandsZ[i],
                    islandsX[j], islandsZ[j]) <= halfWidthSq) {
                return true;
            }
        }
        return false;
    }

    /**
     * Overload accepting {@link io.github.rush.objects.Island} positions
     * already in cyclic layout order.
     */
    public static boolean isOnPath(double blockX, double blockZ,
                                   List<io.github.rush.objects.Island> islands) {
        final int n = islands.size();
        final double[] xs = new double[n];
        final double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = islands.get(i).getX();
            zs[i] = islands.get(i).getZ();
        }
        return isOnPath(blockX, blockZ, xs, zs);
    }

    /** Squared perpendicular distance from point P to segment A→B. */
    private static double distanceSqToSegment(
            double px, double pz,
            double ax, double az,
            double bx, double bz) {

        final double abx = bx - ax;
        final double abz = bz - az;
        final double ab2 = abx * abx + abz * abz;

        if (ab2 == 0.0) {
            final double dx = px - ax;
            final double dz = pz - az;
            return dx * dx + dz * dz;
        }

        final double apx = px - ax;
        final double apz = pz - az;
        double t = (apx * abx + apz * abz) / ab2;
        if (t < 0.0) t = 0.0;
        else if (t > 1.0) t = 1.0;

        final double cx = ax + t * abx;
        final double cz = az + t * abz;
        final double dx = px - cx;
        final double dz = pz - cz;
        return dx * dx + dz * dz;
    }
}
