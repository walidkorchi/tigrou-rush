package io.github.rush.game;

import io.github.rush.entities.GamePlayer;

import io.github.rush.Main;
import io.github.rush.objects.Island;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import dz.jtsgen.annotations.TypeScript;

/**
 * Bridge between GameManager and Game instance.
 * Players join a room, select teams, and the game starts within that room.
 */
public class GameRoom {

    @Getter
    private final String id;

    @Getter
    @Setter
    private String hostName;

    @Getter
    @Setter
    private UUID hostUUID;

    @Getter
    private final List<UUID> joinOrder = new ArrayList<>();

    @Getter
    private final World world;

    @Getter
    private final Game game;

    @Getter
    private final GameRoomConfig config;

    @Getter
    private final IslandType islandType;

    @Getter
    private final TeamSize teamSize;

    @Getter
    private final Location lobbyLocation;

    @Getter
    private final List<Island> islands;

    private int islandY = 0;

    @TypeScript
    public enum IslandType {
        FOUR_ISLANDS(4, "4 Îles"),
        EIGHT_ISLANDS(8, "8 Îles (À venir)");

        @Getter
        private final int count;
        @Getter
        private final String displayName;

        IslandType(int count, String displayName) {
            this.count = count;
            this.displayName = displayName;
        }

    }

    @TypeScript
    public enum TeamSize {
        VS1(1, "1 vs 1"),
        VS2(2, "2 vs 2"),
        VS3(3, "3 vs 3"),
        VS4(4, "4 vs 4");

        @Getter
        private final int playersPerTeam;
        @Getter
        private final String displayName;

        TeamSize(int playersPerTeam, String displayName) {
            this.playersPerTeam = playersPerTeam;
            this.displayName = displayName;
        }

    }

    public GameRoom(String hostName, UUID hostUUID, World world, GameRoomConfig config, Location lobbyLocation) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.hostName = hostName;
        this.hostUUID = hostUUID;
        this.joinOrder.add(hostUUID);
        this.world = world;
        this.config = config;
        this.islandType = config.islandType();
        this.teamSize = config.teamSize();
        this.lobbyLocation = lobbyLocation;
        this.game = new Game(id, world.getName(), lobbyLocation, config.islandType().getCount(), config.maxTeams(),
                config.teamSize().getPlayersPerTeam());
        this.game.setGameRoom(this);
        this.game.setCoefficient(config.getCoefficient());
        this.islands = createIslands();
    }

    private List<Island> createIslands() {
        final int islandOffset = Main.getInstance().getConfig().getInt("islandOffset");
        return Island.Layout.positionsFor(config.islandType(), islandOffset)
                .stream()
                .map(p -> new Island(p.x(), p.z(), p.rotation()))
                .toList();
    }

    public int getIslandY() {
        if (islandY == 0)
            islandY = world.getMaxHeight() - Main.getInstance().getConfig().getInt("distance-height-limit");
        return islandY;
    }

    public Component getDisplayName() {
        return Component.translatable("rush.room_display_name",
                Component.text(islandType.getDisplayName()),
                Component.text(config.formatDisplayName()),
                Component.text(hostName));
    }

    public int getPlayerCount() {
        return game.getTotalPlayerCount();
    }

    public int getMaxPlayers() {
        return config.maxTeams() * teamSize.getPlayersPerTeam();
    }

    public boolean isFull() {
        return getPlayerCount() >= getMaxPlayers();
    }

    public boolean isWaiting() {
        return game.getState() == GameState.WAITING;
    }

    public boolean isRunning() {
        return game.getState() == GameState.RUNNING;
    }

    public void removePlayer(Player player) {
        game.removePlayer(new GamePlayer(player));
    }

    public void sendReadyActionBar() {
        sendReadyActionBar(this);
    }

    public static void sendReadyActionBar(GameRoom room) {
        Game game = room.getGame();
        if (game.getState() != GameState.WAITING)
            return;

        long readyCount = game.getPlayersReadyCount();
        int maxPlayers = room.getMaxPlayers();
        NamedTextColor color = readyCount >= maxPlayers ? NamedTextColor.GREEN : NamedTextColor.RED;
        Component message = Component.translatable("rush.ready_players",
                Component.text(readyCount + "/" + maxPlayers).color(color));

        for (Player player : room.getWorld().getPlayers()) {
            player.sendActionBar(message);
        }
    }

    public static UUID nextHost(List<UUID> joinOrder, UUID disconnected, Predicate<UUID> isOnline) {
        for (UUID candidate : joinOrder) {
            if (!candidate.equals(disconnected) && isOnline.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
