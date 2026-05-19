package io.github.rush.replay;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReplayFile(ReplayHeader header, Map<UUID, List<ReplayAction>> actions) {}
