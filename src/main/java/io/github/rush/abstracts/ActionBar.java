package io.github.rush.abstracts;

import io.github.rush.Main;
import io.github.rush.game.GameRoom;

public final class ActionBar {

    private ActionBar() {
    }

    public static void startTask(Main plugin) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, ActionBar::sendReadyToAll, 0L, 40L);
    }

    public static void sendReadyToAll() {
        for (GameRoom room : Main.getInstance().getGameManager().getAllGameRooms())
            GameRoom.sendReadyActionBar(room);
    }

}
