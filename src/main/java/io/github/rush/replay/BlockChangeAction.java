package io.github.rush.replay;

public record BlockChangeAction(long timestamp, int x, int y, int z, String worldName, String newMaterial, String oldMaterial)
        implements ReplayAction {}
