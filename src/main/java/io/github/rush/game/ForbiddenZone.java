package io.github.rush.game;

/**
 * Pre-overtime "patched" corridor between the two playing teams' islands.
 * Visualised as orange wool by the {@code /debugzones} command and lifted
 * once the game enters overtime.
 *
 * <p>The corridor follows the same bridge geometry the ring path uses (see
 * {@link RingPath}): it runs from the bridge endpoint anchored on team A's
 * island to the one anchored on team B's, padded by
 * {@link RingPath#BRIDGE_SPREAD}. The orange corridor therefore overlays
 * the matching gray bridge segment exactly — same width, same length, same
 * alignment.
 */
public final class ForbiddenZone {

    private ForbiddenZone() {}

    /**
     * @param blockX      block X (world coordinate)
     * @param blockZ      block Z (world coordinate)
     * @param spawnAX     playing team A's spawn X (island A center X)
     * @param spawnAZ     playing team A's spawn Z
     * @param spawnBX     playing team B's spawn X
     * @param spawnBZ     playing team B's spawn Z
     * @param islandCount currently unused; kept for backward compatibility
     *                    and reserved for the eventual 8-island geometry.
     */
    public static boolean isBlocked(
            double blockX, double blockZ,
            double spawnAX, double spawnAZ,
            double spawnBX, double spawnBZ,
            int islandCount) {

        final double[] ea = RingPath.bridgeEndpoint(spawnAX, spawnAZ, spawnBX, spawnBZ);
        final double[] eb = RingPath.bridgeEndpoint(spawnBX, spawnBZ, spawnAX, spawnAZ);
        final double spreadSq = RingPath.BRIDGE_SPREAD * RingPath.BRIDGE_SPREAD;

        return RingPath.distanceSqToSegment(blockX, blockZ, ea[0], ea[1], eb[0], eb[1])
                <= spreadSq;
    }
}
