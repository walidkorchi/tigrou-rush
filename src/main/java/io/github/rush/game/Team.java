package io.github.rush.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.EnderChest;
import org.bukkit.entity.Player;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Team {

    @Getter
    // TODO: unused for now, but we can use it to identify the team in the future
    private final String name;

    @Getter
    private final TeamColor color;

    @Getter
    private final int maxPlayers;

    private final List<Player> players = new ArrayList<>();
    private final Set<UUID> mannequinIds = ConcurrentHashMap.newKeySet();

    @Getter
    @Setter
    private Location spawnLocation;

    @Getter
    @Setter
    private Location bedLocation;

    @Getter
    private final List<Location> enderChestLocations = new ArrayList<>();

    @Setter
    private boolean bedDestroyed = false;

    public Team(String name, TeamColor color, int maxPlayers) {
        this.name = name;
        this.color = color;
        this.maxPlayers = maxPlayers;
    }

    public boolean addPlayer(Player player) {
        if (players.size() >= maxPlayers) {
            return false;
        }
        if (!players.contains(player)) {
            players.add(player);
        }
        return true;
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public boolean isInTeam(Player player) {
        return players.contains(player);
    }

    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    public int getPlayerCount() {
        return players.size() + mannequinIds.size();
    }

    public boolean addMannequin(UUID uuid) {
        int total = players.size() + mannequinIds.size();
        if (total >= maxPlayers) {
            return false;
        }
        mannequinIds.add(uuid);
        return true;
    }

    public void removeMannequin(UUID uuid) {
        mannequinIds.remove(uuid);
    }

    public boolean isMannequinInTeam(UUID uuid) {
        return mannequinIds.contains(uuid);
    }

    public Set<UUID> getMannequinIds() {
        return Set.copyOf(mannequinIds);
    }

    public void reset() {
        players.clear();
        mannequinIds.clear();
        bedDestroyed = false;
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

        int count = getResourceSpawnerCount();
        enderChestLocations.clear();

        final int[][] directions = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        final int[] dir = directions[islandIndex];
        final int perpX = dir[1];
        final int perpZ = -dir[0];

        final BlockFace facingTowardsCenter = switch (islandIndex) {
            case 0 -> BlockFace.EAST;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.SOUTH;
            case 3 -> BlockFace.NORTH;
            default -> BlockFace.NORTH;
        };

        final int speedOffset = 13;
        final int enderChestOffset = speedOffset - 1;

        final int[] spread = { 1, -1 };

        for (int i = 0; i < count; i++) {
            final int spreadIdx = i % 2;
            final int sign = spread[spreadIdx];

            int x = spawnLocation.getBlockX() + (dir[0] * enderChestOffset) + (perpX * sign);
            int z = spawnLocation.getBlockZ() + (dir[1] * enderChestOffset) + (perpZ * sign);
            int y = spawnLocation.getBlockY() - 2;

            Block block = spawnLocation.getWorld().getBlockAt(x, y, z);

            EnderChest enderChestData = (EnderChest) Material.ENDER_CHEST.createBlockData();
            enderChestData.setFacing(facingTowardsCenter);
            block.setBlockData(enderChestData);

            enderChestLocations.add(block.getLocation());
        }
    }

    public int getResourceSpawnerCount() {
        return Math.max(2, Math.min(4, players.size()));
    }

    public void placeBed(int islandIndex) {
        if (spawnLocation == null) {
            return;
        }

        Material bedMaterial = getBedMaterial();
        if (bedMaterial == null) {
            return;
        }

        int x = spawnLocation.getBlockX();
        int y = spawnLocation.getBlockY() - 2;
        int z = spawnLocation.getBlockZ();

        int bedOffset = -5;

        BlockFace bedFacing;
        switch (islandIndex) {
            case 0 -> {
                x += bedOffset;
                bedFacing = BlockFace.EAST;
            }
            case 1 -> {
                x -= bedOffset;
                bedFacing = BlockFace.WEST;
            }
            case 2 -> {
                z += bedOffset;
                bedFacing = BlockFace.SOUTH;
            }
            case 3 -> {
                z -= bedOffset;
                bedFacing = BlockFace.NORTH;
            }
            default -> {
                return;
            }
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

    private Material getBedMaterial() {
        return switch (color) {
            case RED -> Material.RED_BED;
            case BLUE -> Material.BLUE_BED;
            case GREEN -> Material.GREEN_BED;
            case YELLOW -> Material.YELLOW_BED;
            default -> Material.RED_BED;
        };
    }

    public boolean isBedDestroyed() {
        return bedDestroyed;
    }

    public boolean isDead(Game game) {
        return bedDestroyed;
    }
}
