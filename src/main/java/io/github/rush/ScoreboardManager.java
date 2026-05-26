package io.github.rush;

import fr.mrmicky.fastboard.FastBoard;
import io.github.rush.game.Game;
import io.github.rush.entities.GamePlayer;
import io.github.rush.game.GameState;
import io.github.rush.abstracts.Team;
import io.github.rush.storage.PlayerLevelManager.PlayerLevel;
import io.github.rush.storage.PlayerStatisticManager.PlayerStatistic;
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

        final List<String> lines = new ArrayList<>();

        lines.add(getAnimatedSeparator());
        lines.add("");
        if (playerLevel.getRankIndex() >= 0) {
            long progress = playerLevel.getProgressInRank();
            long range = playerLevel.getXPForCurrentRange();
            lines.add(playerLevel.getFormattedRank() + " §8[" + progress + "/" + range + "§8]");
        } else {
            lines.add("§8Non classé");
        }
        lines.add("§8[" + generateProgressBar(playerLevel) + "§8]");
        lines.add("");
        lines.add("§f☆ §7Statistiques:");
        lines.add(stat.getKills() + " §c\uD83D\uDDE1 §f" + stat.getAssists() + " §c\u2694 §f" + stat.getDeaths()
                + " §c☠ §8("
                + String.format("%.1f", stat.getWeightedScore()) + ")");
        lines.add("");
        lines.add(getAnimatedSeparator());

        board.updateLines(lines);
    }

    private String generateProgressBar(PlayerLevel playerLevel) {
        final long currentXP = playerLevel.getProgressInRank();
        final long nextLevelXP = playerLevel.getXPForCurrentRange();

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
        final Team playerTeam = game.getPlayerTeam(new GamePlayer(player));
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

            lines.add("§eÎle: §f" + getRelativeIslandNumber(player, game, playerTeam));
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

    private int getRelativeIslandNumber(Player player, Game game, Team playerTeam) {
        final List<Team> assignment = game.getIslandAssignment();
        final List<io.github.rush.objects.Island> islands = game.getIslands();

        if (assignment == null || islands == null || islands.isEmpty())
            return 1;

        final int homeSlot = assignment.indexOf(playerTeam);
        if (homeSlot < 0)
            return 1;

        final int nearestSlot = getNearestIslandSlot(player.getLocation(), islands);
        final int islandCount = islands.size();

        // Go in the direction where the nearest other team is FARTHEST — i.e. take the
        // long
        // way around. In a 2-team game the two teams sit on adjacent slots (S+E), so
        // both
        // teams must count away from each other to make the opponent appear at rank 4.
        if (isClockwisePreferred(assignment, homeSlot, islandCount)) {
            return (nearestSlot - homeSlot + islandCount) % islandCount + 1;
        } else {
            return (homeSlot - nearestSlot + islandCount) % islandCount + 1;
        }
    }

    private boolean isClockwisePreferred(List<Team> assignment, int homeSlot, int islandCount) {
        int nearestCW = islandCount;
        int nearestCCW = islandCount;

        for (int d = 1; d < islandCount; d++) {
            if (nearestCW == islandCount) {
                final int slot = (homeSlot + d) % islandCount;
                if (slot < assignment.size() && assignment.get(slot) != null)
                    nearestCW = d;
            }
            if (nearestCCW == islandCount) {
                final int slot = (homeSlot - d + islandCount) % islandCount;
                if (slot < assignment.size() && assignment.get(slot) != null)
                    nearestCCW = d;
            }
            if (nearestCW < islandCount && nearestCCW < islandCount)
                break;
        }

        // Prefer the direction in which the nearest team is farther away (take the long
        // path).
        // When distances are equal (symmetric 4-team ring) fall back to clockwise.
        return nearestCW >= nearestCCW;
    }

    private int getNearestIslandSlot(org.bukkit.Location loc, List<io.github.rush.objects.Island> islands) {
        int nearest = 0;
        double minDistSq = Double.MAX_VALUE;
        for (int i = 0; i < islands.size(); i++) {
            final io.github.rush.objects.Island island = islands.get(i);
            final double dx = loc.getX() - island.getX();
            final double dz = loc.getZ() - island.getZ();
            final double distSq = dx * dx + dz * dz;
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearest = i;
            }
        }
        return nearest;
    }

    private String getTeamLetter(Team.Color color) {
        return switch (color) {
            case RED -> "§cR";
            case BLUE -> "§9B";
            case GREEN -> "§aV";
            case YELLOW -> "§eJ";
            default -> "§7" + color.name().substring(0, 1);
        };
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

        if (animationFrame >= (200 + SEPARATOR_FRAMES.length)) {
            animationFrame = 0;
        }

        final String hubWorld = plugin.getHubWorld();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.getPlayerSettingsManager().isScoreboardEnabled(player.getUniqueId())) {
                removeScoreboard(player);
                continue;
            }

            final Game game = plugin.getGameManager().getGameForPlayer(player);

            if (game != null && game.getState() == GameState.RUNNING) {
                if (game.isSpectator(new GamePlayer(player))) {
                    updateSpectatorScoreboard(player, game);
                } else {
                    updateGameScoreboard(player, game);
                }
            } else if (hubWorld != null && player.getWorld().getName().equals(hubWorld)) {
                updateLobbyScoreboard(player);
            } else {
                removeScoreboard(player);
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
