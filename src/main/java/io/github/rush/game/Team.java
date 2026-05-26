package io.github.rush.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class Team {

    public static BlockFace facingTowardsCenter(int islandIndex) {
        return switch (islandIndex) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            case 3 -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }

    @Getter
    // TODO: unused for now, but we can use it to identify the team in the future
    private final String name;

    @Getter
    private final TeamColor color;

    @Getter
    private final int maxPlayers;

    private final List<GameParticipant> players = new ArrayList<>();

    @Getter
    @Setter
    private Location spawnLocation;

    @Getter
    @Setter
    private Location bedLocation;

    @Getter
    private final List<Location> enderChestLocations = new ArrayList<>();

    @Getter
    @Setter
    private boolean bedDestroyed = false;

    @Getter
    @Setter
    private int bedsDestroyed = 0;

    public Team(String name, TeamColor color, int maxPlayers) {
        this.name = name;
        this.color = color;
        this.maxPlayers = maxPlayers;
    }

    public boolean addPlayer(GameParticipant player) {
        if (players.size() >= maxPlayers) {
            return false;
        }
        if (!players.contains(player)) {
            players.add(player);
        }
        return true;
    }

    public void removePlayer(GameParticipant participant) {
        players.remove(participant);
    }

    public boolean isInTeam(GameParticipant participant) {
        return players.contains(participant);
    }

    public List<GameParticipant> getPlayers() {
        return new ArrayList<>(players);
    }

    public void reset() {
        players.clear();
        bedDestroyed = false;
        bedsDestroyed = 0;
        enderChestLocations.clear();

        if (bedLocation != null && bedLocation.getWorld() != null) {
            Block bedBlock = bedLocation.getBlock();
            if (bedBlock != null && bedBlock.getType().name().endsWith("_BED")) {
                Bed bedData = (Bed) bedBlock.getBlockData();
                if (bedData != null) {
                    Block headBlock = bedBlock.getRelative(bedData.getFacing());
                    if (headBlock != null && headBlock.getType().name().endsWith("_BED")) {
                        headBlock.setType(Material.AIR);
                    }
                }
                bedBlock.setType(Material.AIR);
            }
        }

        bedLocation = null;
        bedDestroyed = false;
    }

    public void addEnderChestLocation(Location location) {
        this.enderChestLocations.add(location);
    }

    public void clearEnderChestLocations() {
        this.enderChestLocations.clear();
    }

    public int getEnderChestCount() {
        return enderChestLocations.size();
    }

    public void placeEnderChests(int islandIndex) {
        if (spawnLocation == null) {
            return;
        }

        enderChestLocations.clear();

        final int[] dir = IslandLayout.ISLAND_DIRECTIONS[islandIndex];
        final int perpX = dir[1];
        // final int perpZ = -dir[0];

        final int speedOffset = 13;
        final int enderChestOffset = speedOffset - 1;

        enderChestLocations.addAll(GameManager.placeIslandEnderChests(
                spawnLocation.getWorld(),
                spawnLocation.getBlockX(),
                spawnLocation.getBlockZ(),
                spawnLocation.getBlockY() - 2,
                dir, perpX, enderChestOffset,
                Team.facingTowardsCenter(islandIndex),
                getResourceSpawnerCount()));
    }

    public int getResourceSpawnerCount() {
        return Math.max(2, Math.min(4, players.size()));
    }

    public void placeBed(int islandIndex) {
        if (spawnLocation == null) return;
        Material bedMaterial = Team.bedMaterialFor(color);
        if (bedMaterial == null) return;

        int[] coords = bedCoords(spawnLocation.getBlockX(), spawnLocation.getBlockZ(),
                spawnLocation.getBlockY() - 2, islandIndex);
        placeBedAt(spawnLocation.getWorld(), coords[0], coords[1], coords[2],
                facingTowardsCenter(islandIndex), bedMaterial);

        bedLocation = new Location(spawnLocation.getWorld(), coords[0], coords[1], coords[2]);
    }

    public static int[] bedCoords(int spawnX, int spawnZ, int bedY, int islandIndex) {
        int[] dir = IslandLayout.ISLAND_DIRECTIONS[islandIndex];
        return new int[] { spawnX + dir[0] * 6, bedY, spawnZ + dir[1] * 6 };
    }

    public static void placeBedAt(World world, int x, int y, int z, BlockFace facing, Material bedMaterial) {
        Block bedFoot = world.getBlockAt(x, y, z);
        Bed footData = (Bed) bedMaterial.createBlockData();
        footData.setPart(Bed.Part.FOOT);
        footData.setFacing(facing);
        bedFoot.setBlockData(footData);

        Block bedHead = bedFoot.getRelative(facing);
        Bed headData = (Bed) bedMaterial.createBlockData();
        headData.setPart(Bed.Part.HEAD);
        headData.setFacing(facing);
        bedHead.setBlockData(headData);
    }

    public static Material bedMaterialFor(TeamColor color) {
        Material mat = Material.getMaterial(color.name() + "_BED");
        return mat != null ? mat : Material.WHITE_BED;
    }

}
