package io.github.rush.replay;

import java.util.UUID;

public record DeathAction(long timestamp, UUID uuid) implements ReplayAction {}
