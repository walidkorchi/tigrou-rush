package io.github.rush.game;

/**
 * NOTE: ARCHIVED is not included within the lifecycle as a state
 * of a GameRoom instance since it is scheduled for removal and world cleanup
 */
public enum GameState {
    CREATING, WAITING, RUNNING, STOPPED
}
