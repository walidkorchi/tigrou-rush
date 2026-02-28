package io.github.rush.scoreboard;

import fr.mrmicky.fastboard.FastBoard;
import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.game.Team;
import io.github.rush.game.TeamColor;
import io.github.rush.statistics.PlayerStatistic;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardManager {

    private final Main plugin;
    private final List<Player> lobbyPlayers = new ArrayList<>();

    public ScoreboardManager(Main plugin) {
        this.plugin = plugin;
    }

    public void updateLobbyScoreboard(Player player) {
        FastBoard board = getOrCreateBoard(player);

        board.updateTitle("&#B8291BT&#C0301Ci&#C8361Eg&#D03D1Fr&#D84320o&#DF4A22u&#E75023R&#EF5724u&#F75D26s&#FF6427h");

        final PlayerStatistic stat = plugin.getPlayerStatisticManager().loadStatistic(player.getUniqueId());

        final int gamesWon = stat != null ? stat.getWins() : 0;
        final int gamesLost = stat != null ? stat.getLoses() : 0;

        final int totalKills = stat != null ? stat.getKills() : 0;
        final int totalDeaths = stat != null ? stat.getDeaths() : 0;
        final int totalAssists = stat != null ? stat.getAssists() : 0;

        final double ratio = calculateRatio(totalKills, totalDeaths, totalAssists);

        final List<String> lines = new ArrayList<>();

        lines.add("&8&m+                           +");
        lines.add("");
        lines.add("&f✪ &7Niveau: &x&2&0&f&b&a&c%alonsolevels_level% &8[%alonsolevels_progress_format%&8]");
        lines.add("&8[%alonsolevels_progress_bar%&8]");
        lines.add("");
        lines.add("&f⋄ &7Statistiques: &f" + totalDeaths + " &c☠&f " + totalKills + "&c🗡️&f " + totalAssists + "&c⚔️");
        lines.add("&eRatio: &f" + String.format("%.1f", ratio));
        lines.add("");
        lines.add("&8&m+                           +");

        board.updateLines(lines);
    }

    public void updateGameScoreboard(Player player, Game game) {
        final FastBoard board = getOrCreateBoard(player);
        final Team playerTeam = game.getPlayerTeam(player);
        final List<String> lines = new ArrayList<>();

        lines.add("");

        if (playerTeam != null) {
            String bedStatus = !playerTeam.isBedDestroyed() ? "✅" : "❌";
            lines.add("§eLit: §f" + bedStatus);

            int islandNum = playerTeam.getColor().getIslandNumber();
            lines.add("§eÎle: §f" + islandNum);

            lines.add("");
            lines.add("§e§nÉquipes§r");

            for (Team team : game.getTeams().values()) {
                if (team.getPlayers().isEmpty())
                    continue;

                String teamLetter = getTeamLetter(team.getColor());
                int playerCount = team.getPlayers().size();
                String bedEmoji = !team.isBedDestroyed() ? "✅" : "❌";

                lines.add(teamLetter + ": §f" + playerCount + " " + bedEmoji);
            }
        } else {
            lines.add("§cPas d'équipe!");
        }

        board.updateLines(lines);
    }

    private String getTeamLetter(TeamColor color) {
        return switch (color) {
            case RED -> "§cR";
            case BLUE -> "§9B";
            case GREEN -> "§aV";
            case YELLOW -> "§eJ";
            default -> "§7" + color.name().substring(0, 1);
        };
    }

    private double calculateRatio(int kills, int deaths, int assists) {
        if (deaths == 0) {
            return kills * 2 + assists;
        }
        return Math.round((kills * 2.0 + assists - deaths) * 10.0) / 10.0;
    }

    public void removeScoreboard(Player player) {
        lobbyPlayers.remove(player);
        FastBoard board = plugin.getFastBoard(player);
        if (board != null) {
            board.delete();
        }
    }

    public void removeAllScoreboards() {
        lobbyPlayers.clear();
    }

    public void addLobbyPlayer(Player player) {
        if (!lobbyPlayers.contains(player)) {
            lobbyPlayers.add(player);
        }
    }

    public void updateAll() {
        String gameWorld = plugin.getGameWorld();
        if (gameWorld == null)
            return;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.getWorld().getName().equals(gameWorld)) {
                removeScoreboard(player);
                continue;
            }

            Game game = plugin.getGameManager().getCurrentGame();
            if (game != null && game.getState() == GameState.RUNNING) {
                if (game.isSpectator(player)) {
                    updateSpectatorScoreboard(player, game);
                } else {
                    updateGameScoreboard(player, game);
                }
            } else {
                updateLobbyScoreboard(player);
            }
        }
    }

    public void updateSpectatorScoreboard(Player player, Game game) {
        FastBoard board = getOrCreateBoard(player);

        board.updateTitle("§6§lRush - Spectateur");

        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("§e§nÉquipes§r");

        for (Team team : game.getTeams().values()) {
            if (team.getPlayers().isEmpty() && !game.getSpectators().isEmpty())
                continue;

            String teamLetter = getTeamLetter(team.getColor());
            int playerCount = team.getPlayers().size();
            String bedEmoji = !team.isBedDestroyed() ? "✅" : "❌";

            lines.add(teamLetter + ": §f" + playerCount + " " + bedEmoji);
        }

        lines.add("");
        long spectatorCount = game.getSpectators().size();
        lines.add("§7Spectateurs: §f" + spectatorCount);

        board.updateLines(lines);
    }

    public FastBoard getOrCreateBoard(Player player) {
        FastBoard board = plugin.getFastBoard(player);
        if (board == null) {
            board = new FastBoard(player);
            plugin.setFastBoard(player, board);
        }
        return board;
    }
}
