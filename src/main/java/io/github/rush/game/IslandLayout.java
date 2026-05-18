package io.github.rush.game;

import java.util.List;

public final class IslandLayout {

    private IslandLayout() {}

    public record IslandPosition(int x, int z, int rotation) {}

    public static List<IslandPosition> positionsFor(GameRoom.IslandType type, int offset) {
        return switch (type) {
            case FOUR_ISLANDS -> List.of(
                    new IslandPosition(0, -offset, 180),   // N
                    new IslandPosition(offset, 0, 90),     // E
                    new IslandPosition(0, offset, 0),      // S
                    new IslandPosition(-offset, 0, -90)    // W
            );
            case EIGHT_ISLANDS -> {
                int diag = (int) Math.round(offset * Math.cos(Math.PI / 4));
                yield List.of(
                        new IslandPosition(0, -offset, 180),    // N
                        new IslandPosition(diag, -diag, 135),   // NE
                        new IslandPosition(offset, 0, 90),      // E
                        new IslandPosition(diag, diag, 45),     // SE
                        new IslandPosition(0, offset, 0),       // S
                        new IslandPosition(-diag, diag, -45),   // SW
                        new IslandPosition(-offset, 0, -90),    // W
                        new IslandPosition(-diag, -diag, -135)  // NW
                );
            }
        };
    }
}
