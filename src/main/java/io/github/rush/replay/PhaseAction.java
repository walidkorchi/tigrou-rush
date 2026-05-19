package io.github.rush.replay;

public record PhaseAction(long timestamp, String phaseName) implements ReplayAction {}
