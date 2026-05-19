package io.github.rush.replay;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplayFileSerializationTest {

    private final Gson gson = ReplayActionSerializer.createGson();

    @Test
    void blockChangeActionRoundTripsWithBothMaterials() {
        BlockChangeAction original = new BlockChangeAction(500L, 10, 64, -5, "world", "SANDSTONE", "AIR");

        String json = gson.toJson(original, ReplayAction.class);
        ReplayAction restored = gson.fromJson(json, ReplayAction.class);

        assertInstanceOf(BlockChangeAction.class, restored);
        BlockChangeAction block = (BlockChangeAction) restored;
        assertEquals(original.timestamp(), block.timestamp());
        assertEquals(original.x(), block.x());
        assertEquals(original.y(), block.y());
        assertEquals(original.z(), block.z());
        assertEquals(original.worldName(), block.worldName());
        assertEquals(original.newMaterial(), block.newMaterial());
        assertEquals(original.oldMaterial(), block.oldMaterial());
    }

    @Test
    void remainingSubtypesRoundTrip() {
        UUID uuid = UUID.randomUUID();
        List<ReplayAction> actions = List.of(
                new DeathAction(100L, uuid),
                new RespawnAction(200L, uuid, 1.0, 64.0, 2.0),
                new PhaseAction(300L, "OVERTIME"),
                new BedDestroyAction(400L, "RED", uuid)
        );

        for (ReplayAction original : actions) {
            String json = gson.toJson(original, ReplayAction.class);
            ReplayAction restored = gson.fromJson(json, ReplayAction.class);
            assertEquals(original.getClass(), restored.getClass());
            assertEquals(original.timestamp(), restored.timestamp());
        }
    }

    @Test
    void nullWinnerSurvivesRoundTripInReplayFile() {
        UUID participant = UUID.randomUUID();
        ReplayHeader header = new ReplayHeader("abc123", "HostPlayer", 1_000_000L, 120_000L, null, List.of("HostPlayer"), null, null);
        ReplayFile original = new ReplayFile(header, Map.of(participant, List.of(new DeathAction(50L, participant))));

        String json = gson.toJson(original);
        ReplayFile restored = gson.fromJson(json, ReplayFile.class);

        assertNull(restored.header().winnerTeamColorName());
        assertEquals("abc123", restored.header().sessionId());
    }

    @Test
    void fullReplayFileWithMixedActionsRoundTrips() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        ReplayHeader header = new ReplayHeader("xyz789", "Alice", 2_000_000L, 300_000L, "BLUE", List.of("Alice", "Bob"), null, null);
        ReplayFile original = new ReplayFile(header, Map.of(
                p1, List.of(new MoveAction(0L, p1, 0, 64, 0, 0f, 0f), new DeathAction(60_000L, p1)),
                p2, List.of(new BlockChangeAction(30_000L, 5, 64, 5, "world", "END_STONE", "AIR"))
        ));

        String json = gson.toJson(original);
        ReplayFile restored = gson.fromJson(json, ReplayFile.class);

        assertEquals("BLUE", restored.header().winnerTeamColorName());
        assertEquals(2, restored.actions().size());
    }

    @Test
    void moveActionRoundTrips() {
        UUID uuid = UUID.randomUUID();
        MoveAction original = new MoveAction(1000L, uuid, 10.5, 64.0, -20.3, 90f, 0f);

        String json = gson.toJson(original, ReplayAction.class);
        ReplayAction restored = gson.fromJson(json, ReplayAction.class);

        assertInstanceOf(MoveAction.class, restored);
        MoveAction move = (MoveAction) restored;
        assertEquals(original.timestamp(), move.timestamp());
        assertEquals(original.uuid(), move.uuid());
        assertEquals(original.x(), move.x());
        assertEquals(original.y(), move.y());
        assertEquals(original.z(), move.z());
        assertEquals(original.yaw(), move.yaw());
        assertEquals(original.pitch(), move.pitch());
    }
}
