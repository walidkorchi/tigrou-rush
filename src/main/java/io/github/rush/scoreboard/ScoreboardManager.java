package io.github.rush.scoreboard;

import fr.mrmicky.fastboard.FastBoard;
import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.game.Team;
import io.github.rush.game.TeamColor;
import io.github.rush.statistics.PlayerLevel;
import io.github.rush.statistics.PlayerStatistic;
import io.github.rush.utils.TextUtils;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardManager {

    private final Main plugin;
    private final List<Player> lobbyPlayers = new ArrayList<>();
    private double animationFrame = 0.0;

    private static final String[] SEPARATOR_FRAMES = {
            "§8§m=                         =",
            "§8§m=§8§m                         =",
            "§8§m= §8§m                        =",
            "§8§m=  §8§m                       =",
            "§8§m=§7§m   §8§m                      =",
            "§8§m= §7§m   §8§m                     =",
            "§8§m=  §7§m   §8§m                    =",
            "§8§m=   §7§m   §8§m                   =",
            "§8§m=    §7§m   §8§m                  =",
            "§8§m=     §7§m   §8§m                 =",
            "§8§m=      §7§m   §8§m                =",
            "§8§m=       §7§m   §8§m               =",
            "§8§m=        §7§m   §8§m              =",
            "§8§m=         §7§m   §8§m             =",
            "§8§m=          §7§m   §8§m            =",
            "§8§m=           §7§m   §8§m           =",
            "§8§m=            §7§m   §8§m          =",
            "§8§m=             §7§m   §8§m         =",
            "§8§m=              §7§m   §8§m        =",
            "§8§m=               §7§m   §8§m       =",
            "§8§m=                §7§m   §8§m      =",
            "§8§m=                 §7§m   §8§m     =",
            "§8§m=                  §7§m   §8§m    =",
            "§8§m=                   §7§m   §8§m   =",
            "§8§m=                    §7§m   §8§m  =",
            "§8§m=                     §7§m   §8§m =",
            "§8§m=                      §7§m   §8§m=",
            "§8§m=                      §7§m   =",
            "§8§m=                       §7§m  =",
            "§8§m=                        §7§m =",
            "§8§m=                         §7§m="
    };

    public ScoreboardManager(Main plugin) {
        this.plugin = plugin;
    }

    private String getAnimatedSeparator() {
        int frameIndex = (int) animationFrame;

        if (frameIndex >= SEPARATOR_FRAMES.length) {
            return SEPARATOR_FRAMES[0];
        }

        return SEPARATOR_FRAMES[frameIndex];
    }

    public void updateLobbyScoreboard(Player player) {
        final FastBoard board = getOrCreateBoard(player);
        final String title = TextUtils.convertHexToLegacy(
                "&#B8291BT&#C0301Ci&#C8361Eg&#D03D1Fr&#D84320o&#DF4A22u&#E75023R&#EF5724u&#F75D26s&#FF6427h");

        board.updateTitle(title);

        final PlayerStatistic stat = plugin.getPlayerStatisticManager().loadStatistic(player.getUniqueId());
        final PlayerLevel playerLevel = plugin.getPlayerLevelManager().loadPlayerLevel(player.getUniqueId());

        // final int gamesWon = stat != null ? stat.getWins() : 0;
        // final int gamesLost = stat != null ? stat.getLoses() : 0;

        final int totalKills = stat != null ? stat.getKills() : 0;
        final int totalDeaths = stat != null ? stat.getDeaths() : 0;
        final int totalAssists = stat != null ? stat.getAssists() : 0;

        final double ratio = calculateRatio(totalKills, totalDeaths, totalAssists);

        final String progressBar = generateProgressBar(playerLevel);
        final String tierColor = playerLevel.getTierColor();
        final int level = playerLevel.getLevel();
        final int currentXP = playerLevel.getCurrentXP();
        final int nextLevelXP = playerLevel.getXPForNextLevel();

        final List<String> lines = new ArrayList<>();

        lines.add(getAnimatedSeparator());
        lines.add("");
        lines.add("§7✪ Niveau: " + tierColor + level + " §8[" + currentXP + "/" + nextLevelXP + "§8]");
        lines.add("§8[" + progressBar + "§8]");
        lines.add("");
        lines.add("§f☆ §7Statistiques:");
        lines.add(totalKills + " §c\uD83D\uDDE1 §f" + totalAssists + " §c\u2694 §f" + totalDeaths + " §c☠ §8("
                + String.format("%.1f", ratio) + ")");
        lines.add("");
        lines.add(getAnimatedSeparator());

        board.updateLines(lines);
    }

    private String generateProgressBar(PlayerLevel playerLevel) {
        final int currentXP = playerLevel.getCurrentXP();
        final int nextLevelXP = playerLevel.getXPForNextLevel();

        double progress = nextLevelXP > 0 ? (double) currentXP / nextLevelXP : 0.0;

        progress = Math.min(1.0, Math.max(0.0, progress));

        final int totalBars = 15;
        final int filledBars = (int) (totalBars * progress);
        final StringBuilder bar = new StringBuilder();

        for (int i = 0; i < filledBars; i++) {
            bar.append("§6■");
        }

        for (int i = filledBars; i < totalBars; i++) {
            bar.append("§8■");
        }

        return bar.toString();
    }

    public void updateGameScoreboard(Player player, Game game) {
        final FastBoard board = getOrCreateBoard(player);
        final Team playerTeam = game.getPlayerTeam(player);
        final List<String> lines = new ArrayList<>();

        lines.add("");
        if (game.isOvertime()) {
            lines.add("§c§lOVERTIME §f" + game.getFormattedTime());
        } else {
            lines.add("§eTemps: §f" + game.getFormattedTime());
        }
        lines.add("");

        if (playerTeam != null) {
            final String bedStatus = !playerTeam.isBedDestroyed() ? "✅" : "❌";

            lines.add("§eLit: §f" + bedStatus);

            final int islandNum = playerTeam.getColor().getIslandNumber();

            lines.add("§eÎle: §f" + islandNum);
            lines.add("");
            lines.add("§e§nÉquipes§r");

            for (Team team : game.getTeams().values()) {
                if (team.getPlayers().isEmpty())
                    continue;

                final String teamLetter = getTeamLetter(team.getColor());
                final int playerCount = team.getPlayers().size();
                final String bedEmoji = !team.isBedDestroyed() ? "✅" : "❌";

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
        } else {
            return Math.round((kills * 2.0 + assists - deaths) * 10.0) / 10.0;
        }
    }

    public void removeScoreboard(Player player) {
        lobbyPlayers.remove(player);

        final FastBoard board = plugin.getFastBoard(player);

        if (board != null && !board.isDeleted()) {
            try {
                board.delete();
            } catch (IllegalStateException ignored) {
            }
        }

        plugin.setFastBoard(player, null);
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
        animationFrame += 1;

        // 10 seconds + n ticks based on animation length
        if (animationFrame >= (200 + SEPARATOR_FRAMES.length)) {
            animationFrame = 0;
        }

        final String gameWorld = plugin.getGameWorld();

        if (gameWorld == null)
            return;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.getWorld().getName().equals(gameWorld)) {
                removeScoreboard(player);
                continue;
            }

            if (!plugin.getPlayerSettingsManager().isScoreboardEnabled(player.getUniqueId())) {
                removeScoreboard(player);
                continue;
            }

            final Game game = plugin.getGameManager().getCurrentGame();

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
        final FastBoard board = getOrCreateBoard(player);

        board.updateTitle("§6§lRush - Spectateur");

        final List<String> lines = new ArrayList<>();

        lines.add("");
        if (game.isOvertime()) {
            lines.add("§c§lOVERTIME §f" + game.getFormattedTime());
        } else {
            lines.add("§eTemps: §f" + game.getFormattedTime());
        }
        lines.add("");
        lines.add("§e§nÉquipes§r");

        for (Team team : game.getTeams().values()) {
            if (team.getPlayers().isEmpty() && !game.getSpectators().isEmpty())
                continue;

            final String teamLetter = getTeamLetter(team.getColor());
            final int playerCount = team.getPlayers().size();
            final String bedEmoji = !team.isBedDestroyed() ? "✅" : "❌";

            lines.add(teamLetter + ": §f" + playerCount + " " + bedEmoji);
        }

        lines.add("");

        final long spectatorCount = game.getSpectators().size();

        lines.add("§7Spectateurs: §f" + spectatorCount);
        board.updateLines(lines);
    }

    public FastBoard getOrCreateBoard(Player player) {
        FastBoard board = plugin.getFastBoard(player);

        if (board == null || board.isDeleted()) {
            board = new FastBoard(player);
            plugin.setFastBoard(player, board);
        }

        return board;
    }
}
