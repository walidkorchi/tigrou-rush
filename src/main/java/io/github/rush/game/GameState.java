package io.github.rush.game;

import dz.jtsgen.annotations.TypeScript;

/**
 * NOTE: ARCHIVED is not included within the lifecycle as a state
 * of a GameRoom instance since it is scheduled for removal and world cleanup
 */
@TypeScript
public enum GameState {
    CREATING, WAITING, RUNNING, STOPPED
}
