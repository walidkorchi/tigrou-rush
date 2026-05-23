package io.github.rush.game;

import java.util.List;

/**
 * Geometry of the "ring path" — the diagonal corridors players are allowed to
 * build on, connecting one island to the next around the cyclic layout.
 *
 * <p>
 * <b>Bridge endpoints, not island centers.</b> A bridge between two
 * cyclically-adjacent islands does NOT run from one island's center to the
 * other's. It runs between two offset endpoints, one anchored on each
 * island. Anchored at island {@code A}, the endpoint sits:
 * <ul>
 * <li>{@link #AXIAL_OFFSET} blocks toward map-origin along A's radial
 * direction — just inside the island's perimeter on the map-facing side,</li>
 * <li>{@link #TRANSVERSE_OFFSET} blocks toward the destination island B
 * along A's tangential direction — so the two bridges leaving A (one to
 * each cyclic neighbour) sit symmetrically on either side of A's radial
 * axis.</li>
 * </ul>
 * For the 4-island cardinal layout this places, e.g., South's two bridge
 * endpoints at {@code (±6, +38)} and produces clean diagonal corridors of
 * slope ±1 between adjacent islands.
 *
 * <p>
 * A position is on the ring iff its perpendicular distance to any one of
 * those offset bridge segments is at most {@link #BRIDGE_SPREAD}.
 *
 * <p>
 * This geometry is also what {@link ForbiddenZone} uses for the pre-overtime
 * corridor, so both shapes are identical.
 */
public final class RingPath {

    /**
     * Distance from an island's center toward map-origin (radial axis) at
     * which a bridge endpoint is anchored.
     */
    public static final double AXIAL_OFFSET = -2.0;

    /**
     * Distance from an island's radial axis (tangential axis) at which a
     * bridge endpoint is anchored. Each island has two bridges, one at
     * {@code +TRANSVERSE_OFFSET} and one at {@code -TRANSVERSE_OFFSET}.
     */
    public static final double TRANSVERSE_OFFSET = 6.0;

    /**
     * Half-width of a bridge corridor, in blocks. Total bridge width is
     * {@code 2 * BRIDGE_SPREAD}.
     */
    public static final double BRIDGE_SPREAD = 5.0;

    private RingPath() {
    }

    /**
     * @return true if the block is within {@link #BRIDGE_SPREAD} of any
     *         bridge corridor between two cyclically-adjacent islands.
     */
    public static boolean isOnPath(double blockX, double blockZ,
            double[] islandsX, double[] islandsZ) {
        if (islandsX.length != islandsZ.length || islandsX.length < 2) {
            return true;
        }

        final int n = islandsX.length;
        final double spreadSq = Math.pow(BRIDGE_SPREAD, 2);

        for (int i = 0; i < n; i++) {
            final int j = (i + 1) % n;
            final double[] ea = bridgeEndpoint(islandsX[i], islandsZ[i], islandsX[j], islandsZ[j]);
            final double[] eb = bridgeEndpoint(islandsX[j], islandsZ[j], islandsX[i], islandsZ[i]);

            if (distanceSqToSegment(blockX, blockZ, ea[0], ea[1], eb[0], eb[1]) <= spreadSq) {
                return true;
            }
        }

        return false;
    }

    /**
     * Convenience overload accepting a list of
     * {@link io.github.rush.objects.Island}
     * positions in their cyclic layout order.
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

    /**
     * Bridge endpoint anchored at island A, on the side of A facing B.
     * Pulled {@link #AXIAL_OFFSET} blocks toward origin and
     * {@link #TRANSVERSE_OFFSET} blocks toward B along A's tangential axis.
     */
    public static double[] bridgeEndpoint(double ax, double az, double bx, double bz) {
        final double aLen = Math.sqrt(ax * ax + az * az);

        if (aLen == 0.0) {
            return new double[] { ax, az };
        }

        final double axialX = -ax / aLen;
        final double axialZ = -az / aLen;
        // Tangential axis: rotate axial 90° clockwise.
        final double perpX = axialZ;
        final double perpZ = -axialX;
        // Sign that points toward B along the tangential axis.
        final double dot = (bx - ax) * perpX + (bz - az) * perpZ;
        final double sign = dot >= 0.0 ? 1.0 : -1.0;

        return new double[] {
                ax + AXIAL_OFFSET * axialX + TRANSVERSE_OFFSET * sign * perpX,
                az + AXIAL_OFFSET * axialZ + TRANSVERSE_OFFSET * sign * perpZ
        };
    }

    /** Squared perpendicular distance from point P to segment A→B. */
    static double distanceSqToSegment(
            double px, double pz,
            double ax, double az,
            double bx, double bz) {

        final double abx = bx - ax;
        final double abz = bz - az;
        final double ab2 = abx * abx + abz * abz;

        if (ab2 == 0.0) {
            final double dpx = px - ax;
            final double dpz = pz - az;

            return dpx * dpx + dpz * dpz;
        }

        final double apx = px - ax;
        final double apz = pz - az;
        double t = (apx * abx + apz * abz) / ab2;

        if (t < 0.0)
            t = 0.0;
        else if (t > 1.0)
            t = 1.0;

        final double cx = ax + t * abx;
        final double cz = az + t * abz;
        final double dx = px - cx;
        final double dz = pz - cz;

        return dx * dx + dz * dz;
    }
}
