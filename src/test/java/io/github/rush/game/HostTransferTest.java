package io.github.rush.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HostTransferTest {

    @Test
    void nextOnlinePlayerInJoinOrderBecomesHost() {
        UUID host = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        List<UUID> joinOrder = List.of(host, next);

        UUID result = HostTransfer.nextHost(joinOrder, host, uuid -> uuid.equals(next));

        assertEquals(next, result);
    }

    @Test
    void onlyPlayerDisconnects_returnsNull() {
        UUID host = UUID.randomUUID();
        List<UUID> joinOrder = List.of(host);

        UUID result = HostTransfer.nextHost(joinOrder, host, uuid -> false);

        assertNull(result);
    }

    @Test
    void allRemainingPlayersOffline_returnsNull() {
        UUID host = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<UUID> joinOrder = List.of(host, a, b);

        UUID result = HostTransfer.nextHost(joinOrder, host, uuid -> false);

        assertNull(result);
    }

    @Test
    void disconnectedInMiddle_picksEarliestOnlinePlayer() {
        UUID first = UUID.randomUUID();
        UUID leaving = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        List<UUID> joinOrder = List.of(first, leaving, third);

        UUID result = HostTransfer.nextHost(joinOrder, leaving, uuid -> true);

        assertEquals(first, result);
    }

    @Test
    void earliestPlayerOffline_skipsToNextOnline() {
        UUID host = UUID.randomUUID();
        UUID offline = UUID.randomUUID();
        UUID online = UUID.randomUUID();
        List<UUID> joinOrder = List.of(host, offline, online);

        UUID result = HostTransfer.nextHost(joinOrder, host, uuid -> uuid.equals(online));

        assertEquals(online, result);
    }
}
