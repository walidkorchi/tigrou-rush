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
    @Getter
    private final int dirX, dirZ;
    @Getter
    private final float merchantYaw;
    @Getter
    private final BlockFace facing;

    public Island(int x, int z, int rotation, int dirX, int dirZ, float merchantYaw, BlockFace facing) {
        this.x = x;
        this.z = z;
        this.rotation = rotation;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.merchantYaw = merchantYaw;
        this.facing = facing;
    }

    public static final class Type {
        private static volatile List<Type> LOADED = List.of();

        private final String name;
        private final String filename;

        private Type(String name, String filename) {
            this.name = name;
            this.filename = filename;
        }

        public String name() {
            return name;
        }

        public String schematicName() {
            return "islands/" + filename;
        }

        public String displayName() {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('_', ' ');
        }

        /** Scans the given directory for .schem files and populates the cache. */
        public static void reload(File islandsDir) {
            final File[] files = islandsDir.listFiles((dir, n) -> n.endsWith(".schem"));

            if (!islandsDir.exists() || !islandsDir.isDirectory() || files == null) {
                LOADED.clear();
                return;
            } else {
                LOADED = Arrays.stream(files)
                        .map(f -> new Type(f.getName().replaceAll("\\.schem$", ""), f.getName()))
                        .sorted(Comparator.comparing(t -> t.name))
                        .toList();
            }
        }

        public static List<Type> all() {
            return LOADED;
        }

        public static Optional<Type> byName(String name) {
            if (name == null)
                return Optional.empty();
            return LOADED.stream().filter(t -> t.name.equals(name)).findFirst();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Type other && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public static final class Layout {

        private Layout() {
        }

        /** Merchant spread distances (blocks from center) for the ±1 offset pair. */
        public static final List<Integer> MERCHANT_SPREADS = List.of(5, 7);

        // 4-island: S→E→N→W so 2-team forbidden zone covers the SE corridor.
        private static final int[] ORDER_4 = { 2, 1, 0, 3 };
        // 8-island assignment order.
        // 2-team → {N-L(0), W-T(7)}: ring-adjacent inter-pair pair, mirrors 4-island
        // 1-hop behaviour.
        // 4-team → add {E-B(3), S-R(4)}: opposite-side adjacent pair, four islands at
        // ring positions {0,1,4,5}.
        // 6-team → add {N-R(1), S-L(5)}: fills one island from each remaining
        // inter-pair diagonal.
        // 8-team → add {E-T(2), W-B(6)}: all islands.
        private static final int[] ORDER_8 = { 0, 7, 3, 4, 1, 5, 2, 6 };

        public record IslandPosition(int x, int z, int rotation, int dirX, int dirZ, float merchantYaw,
                BlockFace facing) {
        }

        public static List<IslandPosition> positionsFor(GameRoom.IslandType type, int offset) {
            return switch (type) {
                case FOUR_ISLANDS -> List.of(
                        new IslandPosition(0, -offset, -90, 0, -1, 0f, BlockFace.SOUTH), // N
                        new IslandPosition(offset, 0, 180, 1, 0, 90f, BlockFace.WEST), // E
                        new IslandPosition(0, offset, 90, 0, 1, 180f, BlockFace.NORTH), // S
                        new IslandPosition(-offset, 0, 0, -1, 0, -90f, BlockFace.EAST) // W
                    );
                case EIGHT_ISLANDS -> {
                    // TODO: find correct distance to achieve symmety instead of hardcoding offset
                    // Each cardinal angle hosts a pair of islands offset ±30 blocks
                    // perpendicular to its axis, giving 60 blocks total inter-island gap.
                    // The radial distance is doubled vs. 4-island to preserve playable spacing.
                    // Layout has 4-fold rotational symmetry: (x,z) → (z,−x) maps N→W→S→E→N.
                    final int d = offset * 2;
                    final int s = 30;
                    yield List.of(
                            new IslandPosition(-s, -d, -90, 0, -1, 0f, BlockFace.SOUTH), // N-L
                            new IslandPosition(+s, -d, -90, 0, -1, 0f, BlockFace.SOUTH), // N-R
                            new IslandPosition(d, -s, 180, 1, 0, 90f, BlockFace.WEST), // E-T
                            new IslandPosition(d, +s, 180, 1, 0, 90f, BlockFace.WEST), // E-B
                            new IslandPosition(+s, d, 90, 0, 1, 180f, BlockFace.NORTH), // S-R
                            new IslandPosition(-s, d, 90, 0, 1, 180f, BlockFace.NORTH), // S-L
                            new IslandPosition(-d, +s, 0, -1, 0, -90f, BlockFace.EAST), // W-B
                            new IslandPosition(-d, -s, 0, -1, 0, -90f, BlockFace.EAST) // W-T
                    );
                }
            };
        }

        public static int computeIslandY(int maxHeight, int distanceHeightLimit) {
            return maxHeight - distanceHeightLimit;
        }

        public static int[][] speedMerchantPositions(Island island, int[] dir, int perpX, int perpZ,
                int speedOffset) {
            final int[][] positions = new int[2][2];

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
            final int[] order = islandCount == 8 ? ORDER_8 : ORDER_4;
            final List<Integer> result = new ArrayList<>();

            for (int s : order) {
                if (s < islandCount)
                    result.add(s);
            }

            return result;
        }
    }
}
