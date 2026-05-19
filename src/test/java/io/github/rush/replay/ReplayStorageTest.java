package io.github.rush.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplayStorageTest {

    @TempDir
    Path tempDir;

    private ReplayFile sampleReplay(String sessionId) {
        UUID p = UUID.randomUUID();
        ReplayHeader header = new ReplayHeader(sessionId, "Host", 0L, 60_000L, "RED", List.of("Host"), "NORMAL", null);
        return new ReplayFile(header, Map.of(p, List.of(new MoveAction(0L, p, 0, 64, 0, 0f, 0f))));
    }

    @Test
    void savedReplayCanBeLoadedBack() {
        ReplayStorage storage = new ReplayStorage(tempDir);
        ReplayFile original = sampleReplay("abc123");

        storage.save(original);
        Optional<ReplayFile> loaded = storage.load("abc123");

        assertTrue(loaded.isPresent());
        assertEquals("abc123", loaded.get().header().sessionId());
        assertEquals("Host", loaded.get().header().hostName());
    }

    @Test
    void listReplaysReturnsHeadersWithoutActions() {
        ReplayStorage storage = new ReplayStorage(tempDir);
        storage.save(sampleReplay("session1"));
        storage.save(sampleReplay("session2"));

        List<ReplayHeader> headers = storage.listReplays();

        assertEquals(2, headers.size());
        assertTrue(headers.stream().anyMatch(h -> h.sessionId().equals("session1")));
        assertTrue(headers.stream().anyMatch(h -> h.sessionId().equals("session2")));
    }

    @Test
    void savingTwentyFirstReplayDeletesOldest() throws Exception {
        ReplayStorage storage = new ReplayStorage(tempDir);

        for (int i = 0; i < 20; i++) {
            storage.save(sampleReplay("session" + i));
            Thread.sleep(5); // ensure distinct last-modified times
        }
        storage.save(sampleReplay("session20"));

        List<ReplayHeader> headers = storage.listReplays();
        assertEquals(20, headers.size());
        assertTrue(headers.stream().noneMatch(h -> h.sessionId().equals("session0")));
        assertTrue(headers.stream().anyMatch(h -> h.sessionId().equals("session20")));
    }

    @Test
    void loadUnknownSessionIdReturnsEmpty() {
        ReplayStorage storage = new ReplayStorage(tempDir);
        assertTrue(storage.load("nonexistent").isEmpty());
    }
}
