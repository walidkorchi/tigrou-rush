package io.github.rush.game;

import org.bukkit.Location;
import org.bukkit.Material;
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
        if (spawnLocation == null) {
            return;
        }

        Material bedMaterial = Team.bedMaterialFor(color);
        if (bedMaterial == null) {
            return;
        }

        int x = spawnLocation.getBlockX();
        int y = spawnLocation.getBlockY() - 2;
        int z = spawnLocation.getBlockZ();

        int bedOffset = -6;

        // Bed foot placed outward; bedFacing points from foot toward head (inward =
        // toward center)
        BlockFace bedFacing = Team.facingTowardsCenter(islandIndex);
        switch (islandIndex) {
            case 0 -> z += bedOffset; // N: foot at z-6, head south
            case 1 -> x -= bedOffset; // E: foot at x+6, head west
            case 2 -> z -= bedOffset; // S: foot at z+6, head north
            case 3 -> x += bedOffset; // W: foot at x-6, head east
        }

        Block bedFoot = spawnLocation.getWorld().getBlockAt(x, y, z);

        Bed footBedData = (Bed) bedMaterial.createBlockData();
        footBedData.setPart(Bed.Part.FOOT);
        footBedData.setFacing(bedFacing);
        bedFoot.setBlockData(footBedData);

        Block bedHead = bedFoot.getRelative(bedFacing);

        Bed headBedData = (Bed) bedMaterial.createBlockData();
        headBedData.setPart(Bed.Part.HEAD);
        headBedData.setFacing(bedFacing);
        bedHead.setBlockData(headBedData);

        bedLocation = bedFoot.getLocation();
    }

    public static Material bedMaterialFor(TeamColor color) {
        Material mat = Material.getMaterial(color.name() + "_BED");
        return mat != null ? mat : Material.WHITE_BED;
    }

}
