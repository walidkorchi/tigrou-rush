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
        Map<String, String> teamColorsByPlayerUuid) {}
