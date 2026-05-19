package io.github.rush.replay;

import java.util.UUID;

public record RespawnAction(long timestamp, UUID uuid, double x, double y, double z) implements ReplayAction {}
