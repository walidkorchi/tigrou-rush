package io.github.rush.replay;

public sealed interface ReplayAction
        permits MoveAction, BlockChangeAction, DeathAction, RespawnAction, PhaseAction, BedDestroyAction {
    long timestamp();
}
