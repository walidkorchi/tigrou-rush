package io.github.rush.game;

import java.util.List;

public final class IslandLayout {

    private IslandLayout() {
    }

    /** Outward direction vectors per island index: N→-z, E→+x, S→+z, W→-x */
    public static final int[][] ISLAND_DIRECTIONS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

    /**
     * Preferred island assignment order — adjacent pair first (S+E) so 2-team
     * forbidden zone covers the SE corridor.
     */
    public static final int[] PREFERRED_ISLAND_ORDER = { 2, 1, 0, 3 };

    /** Merchant spread distances (blocks from center) for the ±1 offset pair. */
    public static final List<Integer> MERCHANT_SPREADS = List.of(5, 7);

    public record IslandPosition(int x, int z, int rotation) {
    }

    public static List<IslandPosition> positionsFor(GameRoom.IslandType type, int offset) {
        return switch (type) {
            case FOUR_ISLANDS -> List.of(
                    new IslandPosition(0, -offset, -90), // N
                    new IslandPosition(offset, 0, 180), // E
                    new IslandPosition(0, offset, 90), // S
                    new IslandPosition(-offset, 0, 0) // W
                );
            case EIGHT_ISLANDS -> {
                int diag = (int) Math.round(offset * Math.cos(Math.PI / 4));

                yield List.of(
                        new IslandPosition(0, -offset, -90), // N
                        new IslandPosition(diag, -diag, -135), // NE
                        new IslandPosition(offset, 0, 180), // E
                        new IslandPosition(diag, diag, 135), // SE
                        new IslandPosition(0, offset, 90), // S
                        new IslandPosition(-diag, diag, 45), // SW
                        new IslandPosition(-offset, 0, 0), // W
                        new IslandPosition(-diag, -diag, -45) // NW
                );
            }
        };
    }
}
