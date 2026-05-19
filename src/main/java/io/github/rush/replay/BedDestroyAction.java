package io.github.rush.replay;

import java.util.UUID;

public record BedDestroyAction(long timestamp, String teamColorName, UUID destroyerUuid) implements ReplayAction {}
