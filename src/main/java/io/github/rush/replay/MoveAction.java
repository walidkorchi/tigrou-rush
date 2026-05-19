package io.github.rush.replay;

import java.util.UUID;

public record MoveAction(long timestamp, UUID uuid, double x, double y, double z, float yaw, float pitch)
        implements ReplayAction {}
