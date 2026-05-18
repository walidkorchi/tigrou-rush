package io.github.rush.game;

import io.github.rush.Main;
import io.github.rush.objects.Island;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Bridge between GameManager and Game instance.
 * Players join a room, select teams, and the game starts within that room.
 */
public class GameRoom {

    @Getter
    private final String id;

    @Getter
    private final String hostName;

    @Getter
    private final World world;

    @Getter
    private final Game game;

    @Getter
    private final IslandType islandType;

    @Getter
    private final TeamSize teamSize;

    @Getter
    private final Location lobbyLocation;

    @Getter
    private final List<Island> islands;

    @Getter
    private boolean islandsLoaded = false;

    @Getter
    @Setter
    private GameRoomConfig config;

    private int islandY = 0;
    private static final int DISTANCE_HEIGHT_LIMIT = 12;

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

    public enum TeamSize {
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

    public GameRoom(String hostName, World world, IslandType islandType, TeamSize teamSize, Location lobbyLocation) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.hostName = hostName;
        this.world = world;
        this.islandType = islandType;
        this.teamSize = teamSize;
        this.lobbyLocation = lobbyLocation;
        this.game = new Game(id, world.getName(), lobbyLocation, islandType.getCount(), teamSize.getPlayersPerTeam());
        this.game.setGameRoom(this);
        this.islands = createIslands();
    }

    private List<Island> createIslands() {
        final int islandOffset = Main.getInstance().getConfig().getInt("islandOffset");

        return List.of(
                new Island(-islandOffset, 0, -90),
                new Island(islandOffset, 0, 90),
                new Island(0, -islandOffset, 180),
                new Island(0, islandOffset, 0));
    }

    public int getIslandY() {
        if (islandY == 0) {
            islandY = world.getMaxHeight() - DISTANCE_HEIGHT_LIMIT;
        }

        return islandY;
    }

    public void setIslandsLoaded(boolean loaded) {
        this.islandsLoaded = loaded;
    }

    public String getDisplayName() {
        return "§7[" + islandType.getDisplayName() + "§7] §f" + teamSize.getDisplayName() + " §7- §e" + hostName;
    }

    public int getPlayerCount() {
        return game.getTotalPlayerCount();
    }

    public int getMaxPlayers() {
        return islandType.getCount() * teamSize.getPlayersPerTeam();
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
        game.removePlayer(player);
    }
}
