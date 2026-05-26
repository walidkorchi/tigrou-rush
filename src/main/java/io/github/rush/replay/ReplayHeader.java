package io.github.rush.replay;

import java.util.List;
import java.util.Map;

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
        Map<String, String> teamColorsByPlayerUuid) {
}
