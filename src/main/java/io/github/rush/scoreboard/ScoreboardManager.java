package io.github.rush.scoreboard;

import fr.mrmicky.fastboard.FastBoard;
import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.game.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ScoreboardManager {

    private final Main plugin;
    private final Map<Player, FastBoard> playerBoards = new HashMap<>();

    public ScoreboardManager(Main plugin) {
        this.plugin = plugin;
    }

    public void updateLobbyScoreboard(Player player) {
        FastBoard board = getOrCreateBoard(player);
        
        board.updateTitle(Component.text("Rush").color(NamedTextColor.GOLD));
        
        List<String> lines = List.of(
            "",
            Component.text("Joueurs: " + getPlayerCount()).color(NamedTextColor.WHITE).toString(),
            "",
            Component.text("En attente...").color(NamedTextColor.YELLOW).toString()
        );
        
        board.updateLines(lines.stream().map(Component::text).collect(Collectors.toList()));
    }

    public void updateGameScoreboard(Player player, Game game) {
        FastBoard board = getOrCreateBoard(player);
        
        Team playerTeam = game.getPlayerTeam(player);
        
        board.updateTitle(Component.text("Rush - " + game.getName()).color(NamedTextColor.GOLD));
        
        String bedStatus = (playerTeam != null && !playerTeam.isDead(game)) 
            ? "✅" 
            : "❌";
        
        String teamInfo = "";
        if (playerTeam != null) {
            teamInfo = playerTeam.getColor().toString() + playerTeam.getName() + ": " 
                + playerTeam.getPlayers().size() + " " + bedStatus;
        }
        
        List<String> lines = List.of(
            "",
            Component.text("Lit: " + bedStatus).color(NamedTextColor.WHITE).toString(),
            "",
            teamInfo,
            ""
        );
        
        board.updateLines(lines.stream().map(Component::text).collect(Collectors.toList()));
    }

    public void removeScoreboard(Player player) {
        FastBoard board = playerBoards.remove(player);
        if (board != null) {
            board.delete();
        }
    }

    public void removeAllScoreboards() {
        for (FastBoard board : playerBoards.values()) {
            board.delete();
        }
        playerBoards.clear();
    }

    private FastBoard getOrCreateBoard(Player player) {
        FastBoard board = playerBoards.get(player);
        if (board == null) {
            board = new FastBoard(plugin, player);
            playerBoards.put(player, board);
        }
        return board;
    }

    private int getPlayerCount() {
        String gameWorld = plugin.getGameWorld();
        if (gameWorld == null) return 0;
        
        int count = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().getName().equals(gameWorld)) {
                count++;
            }
        }
        return count;
    }

    public Object getNewScoreboard() {
        return null;
    }
}
