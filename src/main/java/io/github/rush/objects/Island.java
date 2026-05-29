package io.github.rush.objects;

import io.github.rush.game.GameRoom;
import lombok.Getter;
import org.bukkit.block.BlockFace;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Island {
    @Getter
    private final int x, z;
    @Getter
    private final int rotation;

    public Island(int x, int z, int rotation) {
        this.x = x;
        this.z = z;
        this.rotation = rotation;
    }

    public static final class Type {
        private static volatile List<Type> LOADED = List.of();

        private final String name;
        private final String filename;

        private Type(String name, String filename) {
            this.name = name;
            this.filename = filename;
        }

        public String name() { return name; }

        public String schematicName() { return "islands/" + filename; }

        public String displayName() {
            if (name.isEmpty()) return "";
            return Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('_', ' ');
        }

        /** Scans the given directory for .schem files and populates the cache. */
        public static void reload(File islandsDir) {
            if (!islandsDir.exists() || !islandsDir.isDirectory()) {
                LOADED = List.of();
                return;
            }
            File[] files = islandsDir.listFiles((dir, n) -> n.endsWith(".schem"));
            if (files == null) {
                LOADED = List.of();
                return;
            }
            LOADED = Arrays.stream(files)
                    .map(f -> new Type(f.getName().replaceAll("\\.schem$", ""), f.getName()))
                    .sorted(Comparator.comparing(t -> t.name))
                    .toList();
        }

        public static List<Type> all() { return LOADED; }

        public static Optional<Type> byName(String name) {
            if (name == null) return Optional.empty();
            return LOADED.stream().filter(t -> t.name.equals(name)).findFirst();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Type other && name.equals(other.name);
        }

        @Override
        public int hashCode() { return name.hashCode(); }
    }

    public static final class Layout {

        private Layout() {}

        /** Outward direction from center vectors per island index: N→-z, E→+x, S→+z, W→-x */
        public static final int[][] ISLAND_DIRECTIONS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

        /**
         * Preferred island assignment order — adjacent pair first (S+E) so 2-team
         * forbidden zone covers the SE corridor.
         */
        public static final int[] PREFERRED_ISLAND_ORDER = { 2, 1, 0, 3 };

        /** Merchant spread distances (blocks from center) for the ±1 offset pair. */
        public static final List<Integer> MERCHANT_SPREADS = List.of(5, 7);

        // Yaw values facing inward (toward center): N→South=0°, E→West=90°, S→North=180°, W→East=-90°
        public static final float[] YAW_VALUES = { 0f, 90f, 180f, -90f };

        public record IslandPosition(int x, int z, int rotation) {}

        public static List<IslandPosition> positionsFor(GameRoom.IslandType type, int offset) {
            return switch (type) {
                case FOUR_ISLANDS -> List.of(
                        new IslandPosition(0, -offset, -90), // N
                        new IslandPosition(offset, 0, 180),  // E
                        new IslandPosition(0, offset, 90),   // S
                        new IslandPosition(-offset, 0, 0)    // W
                    );
                case EIGHT_ISLANDS -> {
                    int diag = (int) Math.round(offset * Math.cos(Math.PI / 4));
                    yield List.of(
                            new IslandPosition(0, -offset, -90),   // N
                            new IslandPosition(diag, -diag, -135), // NE
                            new IslandPosition(offset, 0, 180),    // E
                            new IslandPosition(diag, diag, 135),   // SE
                            new IslandPosition(0, offset, 90),     // S
                            new IslandPosition(-diag, diag, 45),   // SW
                            new IslandPosition(-offset, 0, 0),     // W
                            new IslandPosition(-diag, -diag, -45)  // NW
                    );
                }
            };
        }

        public static int computeIslandY(int maxHeight, int distanceHeightLimit) {
            return maxHeight - distanceHeightLimit;
        }

        public static int[][] speedMerchantPositions(Island island, int[] dir, int perpX, int perpZ,
                int speedOffset) {
            int[][] positions = new int[2][2];
            for (int i = 0; i < 2; i++) {
                int sign = (i == 0) ? 1 : -1;
                positions[i][0] = island.getX() + dir[0] * speedOffset + perpX * sign;
                positions[i][1] = island.getZ() + dir[1] * speedOffset + perpZ * sign;
            }
            return positions;
        }

        public static int[] regularMerchantPos(int i, int islandX, int islandZ, int[] dir, int perpX, int perpZ,
                int offset) {
            final int sign = (i < 2) ? 1 : -1;
            final int idx = (i % 2 == 0) ? 0 : 1;
            return new int[] {
                    islandX + dir[0] * offset + perpX * MERCHANT_SPREADS.get(idx) * sign,
                    islandZ + dir[1] * offset + perpZ * MERCHANT_SPREADS.get(idx) * sign
            };
        }

        public static List<Integer> islandSlotOrder(int islandCount) {
            List<Integer> order = new ArrayList<>();
            for (int s : PREFERRED_ISLAND_ORDER) {
                if (s < islandCount)
                    order.add(s);
            }
            return order;
        }

        public static BlockFace facingTowardsCenter(int islandIndex) {
            return switch (islandIndex) {
                case 0 -> BlockFace.SOUTH;
                case 1 -> BlockFace.WEST;
                case 2 -> BlockFace.NORTH;
                case 3 -> BlockFace.EAST;
                default -> BlockFace.NORTH;
            };
        }
    }
}
