package io.github.rush;

import fr.mrmicky.fastboard.adventure.FastBoard;
import io.github.rush.game.Game;
import io.github.rush.entities.GamePlayer;
import io.github.rush.game.GameState;
import io.github.rush.abstracts.Team;
import io.github.rush.storage.PlayerLevelManager.PlayerLevel;
import io.github.rush.storage.PlayerStatisticManager.PlayerStatistic;
import io.github.rush.utils.TextUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardManager {

    private final Main plugin;
    private final List<Player> lobbyPlayers = new ArrayList<>();
    private double animationFrame = 0.0;

    private static final char[] ISLAND_CIRCLES = { '①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧' };

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

    private String getTeamLetter(Team.Color color) {
        return color.getSectionColor() + color.name().charAt(0);
    }

    private Component getAnimatedSeparator() {
        int frameIndex = (int) animationFrame;
        if (frameIndex >= SEPARATOR_FRAMES.length) {
            frameIndex = 0;
        }
        return LegacyComponentSerializer.legacySection().deserialize(SEPARATOR_FRAMES[frameIndex]);
    }

    public void updateLobbyScoreboard(Player player) {
        final FastBoard board = getOrCreateBoard(player);
        // TODO: refactor this ugly workaround
        final String title = TextUtils.convertHexToLegacy(
                "&#B8291BT&#C0301Ci&#C8361Eg&#D03D1Fr&#D84320o&#DF4A22u&#E75023R&#EF5724u&#F75D26s&#FF6427h");

        board.updateTitle(LegacyComponentSerializer.legacySection().deserialize(title));

        final PlayerStatistic stat = plugin.getPlayerStatisticManager().loadStatistic(player.getUniqueId());
        final PlayerLevel playerLevel = plugin.getPlayerLevelManager().loadPlayerLevel(player.getUniqueId());

        final List<Component> lines = new ArrayList<>();

        lines.add(getAnimatedSeparator());
        lines.add(Component.empty());

        if (playerLevel.getRankIndex() >= 0) {
            long progress = playerLevel.getProgressInRank();
            long range = playerLevel.getXPForCurrentRange();
            Component rankImage = MiniMessage.miniMessage().deserialize(playerLevel.getFormattedRank());
            Component progressPart = LegacyComponentSerializer.legacySection()
                    .deserialize(" §8[" + progress + "/" + range + "§8]");
            lines.add(rankImage.append(progressPart));
        } else {
            lines.add(LegacyComponentSerializer.legacySection().deserialize("§8Non classé"));
        }

        lines.add(LegacyComponentSerializer.legacySection()
                .deserialize("§8[" + generateProgressBar(playerLevel) + "§8]"));
        lines.add(Component.empty());
        lines.add(LegacyComponentSerializer.legacySection().deserialize("§f☆ §7Statistiques:"));
        lines.add(MiniMessage.miniMessage().deserialize(
                "<white>" + stat.getKills() + "<image:tland:skull> <white>" + stat.getAssists()
                        + " <red>⚔ <white>" + stat.getDeaths() + " <red>☠ <dark_gray>("
                        + String.format("%.1f", stat.getWeightedScore()) + ")"));
        lines.add(Component.empty());
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
        final List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());
        if (game.isOvertime()) {
            lines.add(LegacyComponentSerializer.legacySection()
                    .deserialize("§c§lOVERTIME §f" + game.getFormattedTime()));
        } else {
            lines.add(LegacyComponentSerializer.legacySection()
                    .deserialize("§eTemps: §f" + game.getFormattedTime()));
        }
        lines.add(Component.empty());

        if (playerTeam != null) {
            final String bedStatus = !playerTeam.isBedDestroyed() ? "✅" : "❌";

            lines.add(LegacyComponentSerializer.legacySection()
                    .deserialize("§eLit: §f" + bedStatus));

            final int totalIslands = game.getIslands().isEmpty() ? 4 : game.getIslands().size();
            lines.add(LegacyComponentSerializer.legacySection()
                    .deserialize("§eÎle: " + islandProgressBar(
                            getRelativeIslandNumber(player, game, playerTeam), totalIslands)));
            lines.add(Component.empty());
            lines.add(LegacyComponentSerializer.legacySection().deserialize("§e§nÉquipes§r"));

            for (Team team : game.getTeams().values()) {
                if (team.getPlayers().isEmpty())
                    continue;

                final String teamLetter = getTeamLetter(team.getColor());
                final int playerCount = team.getPlayers().size();
                final String bedEmoji = !team.isBedDestroyed() ? "✅" : "❌";

                lines.add(LegacyComponentSerializer.legacySection()
                        .deserialize(teamLetter + ": §f" + playerCount + " " + bedEmoji));
            }
        } else {
            lines.add(LegacyComponentSerializer.legacySection().deserialize("§cPas d'équipe!"));
        }

        board.updateLines(lines);
    }

    /**
     * Builds e.g. "§a① ②§c ③ ④" for a 4-island game where the player is at island
     * 2.
     */
    private static String islandProgressBar(int currentIsland, int totalIslands) {
        final int capped = Math.min(totalIslands, ISLAND_CIRCLES.length);
        final StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= capped; i++) {
            if (i > 1)
                sb.append(' ');
            sb.append(i <= currentIsland ? "§a" : "§c");
            sb.append(ISLAND_CIRCLES[i - 1]);
        }
        return sb.toString();
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
            } else if (Hub.isAtHub(player)) {
                updateLobbyScoreboard(player);
            } else {
                removeScoreboard(player);
            }
        }
    }

    public void updateSpectatorScoreboard(Player player, Game game) {
        final FastBoard board = getOrCreateBoard(player);

        board.updateTitle(LegacyComponentSerializer.legacySection().deserialize("§6§lRush - Spectateur"));

        final List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());
        if (game.isOvertime()) {
            lines.add(LegacyComponentSerializer.legacySection()
                    .deserialize("§c§lOVERTIME §f" + game.getFormattedTime()));
        } else {
            lines.add(LegacyComponentSerializer.legacySection()
                    .deserialize("§eTemps: §f" + game.getFormattedTime()));
        }
        lines.add(Component.empty());
        lines.add(LegacyComponentSerializer.legacySection().deserialize("§e§nÉquipes§r"));

        for (Team team : game.getTeams().values()) {
            if (team.getPlayers().isEmpty() && !game.getSpectators().isEmpty())
                continue;

            final String teamLetter = getTeamLetter(team.getColor());
            final int playerCount = team.getPlayers().size();
            final String bedEmoji = !team.isBedDestroyed() ? "✅" : "❌";

            lines.add(LegacyComponentSerializer.legacySection()
                    .deserialize(teamLetter + ": §f" + playerCount + " " + bedEmoji));
        }

        lines.add(Component.empty());

        final long spectatorCount = game.getSpectators().size();

        lines.add(LegacyComponentSerializer.legacySection().deserialize("§7Spectateurs: §f" + spectatorCount));
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
