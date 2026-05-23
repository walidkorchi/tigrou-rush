package io.github.rush.game;

import java.util.List;

public final class IslandLayout {

    private IslandLayout() {}

    public record IslandPosition(int x, int z, int rotation) {}

    public static List<IslandPosition> positionsFor(GameRoom.IslandType type, int offset) {
        return switch (type) {
            case FOUR_ISLANDS -> List.of(
                    new IslandPosition(0, -offset, -90),  // N
                    new IslandPosition(offset, 0, 180),   // E
                    new IslandPosition(0, offset, 90),    // S
                    new IslandPosition(-offset, 0, 0)     // W
            );
            case EIGHT_ISLANDS -> {
                int diag = (int) Math.round(offset * Math.cos(Math.PI / 4));

                yield List.of(
                        new IslandPosition(0, -offset, -90),     // N
                        new IslandPosition(diag, -diag, -135),   // NE
                        new IslandPosition(offset, 0, 180),      // E
                        new IslandPosition(diag, diag, 135),     // SE
                        new IslandPosition(0, offset, 90),       // S
                        new IslandPosition(-diag, diag, 45),     // SW
                        new IslandPosition(-offset, 0, 0),       // W
                        new IslandPosition(-diag, -diag, -45)    // NW
                );
            }
        };
    }
}
