package io.github.rush.geometry;

import java.util.List;

import io.github.rush.objects.Island;

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

    /**
     * Transverse distance (blocks from island centre toward the pair neighbour) for
     * intra-pair (straight-line) bridge endpoints.
     */
    public static final double INTRA_PAIR_TRANSVERSE = 8.0;

    /**
     * Axial pull toward the map centre applied to intra-pair bridge endpoints, in
     * addition to the transverse offset. Pulls the plate slightly inward so it sits
     * visually inside the island edge rather than flush with it.
     */
    public static final double INTRA_PAIR_AXIAL_PULL = 7.0;

    private RingPath() {
    }

    /**
     * Accepts a list of {@link Island} positions in their cyclic layout order. Uses
     * the Island-aware {@link #bridgeEndpoint(Island, Island)} so that intra-pair
     * (straight) bridges get the correct endpoint.
     *
     * @return true if the block is within {@link #BRIDGE_SPREAD} of any bridge
     *         corridor between two cyclically-adjacent islands.
     */
    public static boolean isOnPath(double blockX, double blockZ, List<Island> islands) {
        final int n = islands.size();
        if (n < 2)
            return true;

        final double spreadSq = Math.pow(BRIDGE_SPREAD, 2);

        for (int i = 0; i < n; i++) {
            final int j = (i + 1) % n;
            final double[] ea = bridgeEndpoint(islands.get(i), islands.get(j));
            final double[] eb = bridgeEndpoint(islands.get(j), islands.get(i));

            if (distanceSqToSegment(blockX, blockZ, ea[0], ea[1], eb[0], eb[1]) <= spreadSq)
                return true;
        }

        return false;
    }

    /**
     * Island-aware overload: uses {@link #straightBridgeEndpoint} for intra-pair
     * (straight-line) bridges — same-{@code dirX}/{@code dirZ} island pairs in the
     * 8-island layout — and the standard diagonal formula otherwise.
     */
    public static double[] bridgeEndpoint(Island a, Island b) {
        if (a.getDirX() == b.getDirX() && a.getDirZ() == b.getDirZ())
            return straightBridgeEndpoint(a.getX(), a.getZ(), b.getX(), b.getZ(),
                    a.getDirX(), a.getDirZ());
        else
            return bridgeEndpoint(a.getX(), a.getZ(), b.getX(), b.getZ());
    }

    /**
     * Endpoint for a straight (intra-pair) bridge:
     * <ul>
     * <li>{@link #INTRA_PAIR_TRANSVERSE} (8) blocks from A's centre toward B,
     * and</li>
     * <li>{@link #INTRA_PAIR_AXIAL_PULL} (2) blocks pulled toward the map origin
     * along A's inward radial direction ({@code −dirX, −dirZ}).</li>
     * </ul>
     */
    private static double[] straightBridgeEndpoint(
            double ax, double az, double bx, double bz, int dirX, int dirZ) {
        final double dx = bx - ax;
        final double dz = bz - az;
        final double len = Math.sqrt(Math.pow(dx, 2) + Math.pow(dz, 2));

        if (len == 0.0)
            return new double[] { ax, az };

        // Axial pull uses +dir (outward / into the island) so the endpoint sits
        // 2 blocks inside the island's inner face rather than 2 blocks outside it.
        return new double[] {
                ax + INTRA_PAIR_TRANSVERSE * dx / len + INTRA_PAIR_AXIAL_PULL * dirX,
                az + INTRA_PAIR_TRANSVERSE * dz / len + INTRA_PAIR_AXIAL_PULL * dirZ
        };
    }

    /**
     * Bridge endpoint anchored at island A, on the side of A facing B.
     * Pulled {@link #AXIAL_OFFSET} blocks toward origin and
     * {@link #TRANSVERSE_OFFSET} blocks toward B along A's tangential axis.
     */
    public static double[] bridgeEndpoint(double ax, double az, double bx, double bz) {
        final double aLen = Math.sqrt(Math.pow(ax, 2) + Math.pow(az, 2));

        if (aLen == 0.0)
            return new double[] { ax, az };

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
        final double ab2 = Math.pow(abx, 2) + Math.pow(abz, 2);

        if (ab2 == 0.0)
            return Math.pow(px - ax, 2) + Math.pow(pz - az, 2);

        double t = (Math.pow(px - ax, 2) + Math.pow(pz - az, 2)) / ab2;

        if (t < 0.0)
            t = 0.0;
        else if (t > 1.0)
            t = 1.0;

        return Math.pow(px - (ax + t * abx), 2) + Math.pow(pz - (az + t * abz), 2);
    }
}
