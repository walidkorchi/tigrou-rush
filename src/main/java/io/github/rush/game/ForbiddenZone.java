package io.github.rush.game;

public final class ForbiddenZone {

    private ForbiddenZone() {}

    public static boolean isBlocked(
            double blockX, double blockZ,
            double spawnAX, double spawnAZ,
            double spawnBX, double spawnBZ,
            int islandCount) {
        double midX = (spawnAX + spawnBX) / 2.0;
        double midZ = (spawnAZ + spawnBZ) / 2.0;

        double midlineAngle = Math.atan2(midZ, midX);
        double blockAngle   = Math.atan2(blockZ, blockX);

        double diff = blockAngle - midlineAngle;
        while (diff >  Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;

        double halfAngle = Math.PI / (2 * islandCount);
        return Math.abs(diff) < halfAngle;
    }
}
