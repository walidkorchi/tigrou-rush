package io.github.rush.game;

import io.github.rush.Main;
import org.bukkit.entity.Player;

import java.util.*;

public class GameManager {

    private final Main plugin;
    private final Map<String, Game> games = new HashMap<>();
    private final Map<Player, Game> playerGameMap = new HashMap<>();

    public GameManager(Main plugin) {
        this.plugin = plugin;
    }

    public Game createGame(String name) {
        if (games.containsKey(name)) {
            return null;
        }
        Game game = new Game(name);
        games.put(name, game);
        return game;
    }

    // TODO: not used for now, but kept as reference for future usage
    public Game getGame(String name) {
        return games.get(name);
    }

    // TODO: not used for now, but kept as reference for future usage
    public void addPlayerToGame(Player player, Game game) {
        playerGameMap.put(player, game);
    }

    // TODO: not used for now, but kept as reference for future usage
    public void removePlayerFromGame(Player player) {
        playerGameMap.remove(player);
    }

    public Game getGameOfPlayer(Player player) {
        return playerGameMap.get(player);
    }

    // TODO: not used for now, but kept as reference for future usage
    public Collection<Game> getGames() {
        return games.values();
    }

    // TODO: not used for now, but kept as reference for future usage
    public void removeGame(String name) {
        games.remove(name);
    }

    public Game getCurrentGame() {
        for (Game game : games.values()) {
            if (game.getState() == GameState.RUNNING || game.getState() == GameState.WAITING) {
                return game;
            }
        }
        return null;
    }
}
