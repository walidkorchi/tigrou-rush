package io.github.rush.replay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplaySeekTest {

    @Test
    void emptyFrameListReturnsNoRestores() {
        List<BlockRestore> restores = ReplaySeek.computeRestores(List.of(), 5000L, 0L);
        assertTrue(restores.isEmpty());
    }

    @Test
    void forwardSeekReturnsNoRestores() {
        List<ReplayAction> frames = List.of(new BlockChangeAction(1000L, 0, 64, 0, "world", "SANDSTONE", "AIR"));
        List<BlockRestore> restores = ReplaySeek.computeRestores(frames, 0L, 5000L);
        assertTrue(restores.isEmpty());
    }

    @Test
    void singleBlockPlacedAfterTargetIsReversed() {
        BlockChangeAction place = new BlockChangeAction(3000L, 5, 64, 5, "world", "SANDSTONE", "AIR");
        List<ReplayAction> frames = List.of(place);

        List<BlockRestore> restores = ReplaySeek.computeRestores(frames, 5000L, 1000L);

        assertEquals(1, restores.size());
        assertEquals(5, restores.get(0).x());
        assertEquals(64, restores.get(0).y());
        assertEquals(5, restores.get(0).z());
        assertEquals("AIR", restores.get(0).material());
    }

    @Test
    void multipleBlocksAreReturnedInReverseChronologicalOrder() {
        List<ReplayAction> frames = List.of(
                new BlockChangeAction(1000L, 1, 64, 0, "world", "SANDSTONE", "AIR"),
                new BlockChangeAction(2000L, 2, 64, 0, "world", "END_STONE", "AIR"),
                new BlockChangeAction(3000L, 3, 64, 0, "world", "SANDSTONE", "AIR")
        );

        List<BlockRestore> restores = ReplaySeek.computeRestores(frames, 5000L, 0L);

        assertEquals(3, restores.size());
        assertEquals(3, restores.get(0).x()); // latest first
        assertEquals(2, restores.get(1).x());
        assertEquals(1, restores.get(2).x());
    }

    @Test
    void nonBlockActionsAreIgnored() {
        List<ReplayAction> frames = List.of(
                new MoveAction(1000L, java.util.UUID.randomUUID(), 0, 64, 0, 0f, 0f),
                new BlockChangeAction(2000L, 5, 64, 5, "world", "SANDSTONE", "AIR"),
                new DeathAction(3000L, java.util.UUID.randomUUID())
        );

        List<BlockRestore> restores = ReplaySeek.computeRestores(frames, 5000L, 0L);

        assertEquals(1, restores.size());
        assertEquals(5, restores.get(0).x());
    }
}
