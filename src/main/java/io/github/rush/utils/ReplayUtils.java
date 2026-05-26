package io.github.rush.utils;

import io.github.rush.replay.ReplayAction;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReplayUtils {

    private ReplayUtils() {}

    public record ReplayHeader(
            String sessionId,
            String hostName,
            long startTimestamp,
            long durationMs,
            String winnerTeamColorName,
            List<String> participantNames,
            String mapTypeName,
            String islandTypeName,
            int maxTeams,
            boolean extraHearts,
            Map<String, String> teamColorsByPlayerUuid) {}

    public record ReplayFile(ReplayHeader header, Map<UUID, List<ReplayAction>> actions) {}

    public record BlockRestore(int x, int y, int z, String worldName, String material) {}
}
