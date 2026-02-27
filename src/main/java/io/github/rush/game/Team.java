package io.github.rush.game;

import io.github.rush.entities.Merchant;
import io.github.rush.entities.MerchantType;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.List;

public class Team {

    private final String name;
    private final TeamColor color;
    private final int maxPlayers;
    private final List<Player> players = new ArrayList<>();
    private Location spawnLocation;
    private Location bedLocation;
    private final List<Location> enderChestLocations = new ArrayList<>();
    private final List<Villager> regularVillagers = new ArrayList<>();
    private final List<Villager> speedVillagers = new ArrayList<>();
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
        return players.size();
    }

    public void reset() {
        players.clear();
        bedDestroyed = false;
        enderChestLocations.clear();
        removeVillagers();

        if (bedLocation != null && bedLocation.getWorld() != null) {
            Block bedBlock = bedLocation.getBlock();
            if (bedBlock != null && bedBlock.getType().name().endsWith("_BED")) {
                bedBlock.setType(Material.AIR);
            }
        }
        bedLocation = null;
        bedDestroyed = false;
    }

    public String getName() {
        return name;
    }

    public TeamColor getColor() {
        return color;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
    }

    public Location getBedLocation() {
        return bedLocation;
    }

    public void setBedLocation(Location bedLocation) {
        this.bedLocation = bedLocation;
    }

    public List<Location> getEnderChestLocations() {
        return enderChestLocations;
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

    public void placeEnderChests() {
        if (spawnLocation == null) {
            return;
        }

        int count = getResourceSpawnerCount();
        enderChestLocations.clear();

        double offset = 2.0;
        double angleStep = Math.PI / 2.0;

        for (int i = 0; i < count; i++) {
            double angle = i * angleStep;
            int x = (int) (spawnLocation.getX() + offset * Math.cos(angle));
            int z = (int) (spawnLocation.getZ() + offset * Math.sin(angle));
            int y = spawnLocation.getBlockY();

            Block block = spawnLocation.getWorld().getBlockAt(x, y, z);
            block.setType(Material.ENDER_CHEST);

            enderChestLocations.add(block.getLocation());
        }
    }

    public int getResourceSpawnerCount() {
        int playerCount = players.size();
        if (playerCount <= 1)
            return 2;
        if (playerCount == 2)
            return 2;
        if (playerCount == 3)
            return 3;
        return 4;
    }

    public void spawnVillagers() {
        if (spawnLocation == null) {
            return;
        }

        spawnRegularVillagers();
        spawnSpeedVillagers();
    }

    private void spawnRegularVillagers() {
        regularVillagers.clear();

        MerchantType[] types = {
                MerchantType.ARMORSMITH,
                MerchantType.WEAPONSMITH,
                MerchantType.ALCHEMIST,
                MerchantType.BUILDER
        };

        double offset = 12.0;
        double angleStep = Math.PI / 2.0;

        for (int i = 0; i < types.length; i++) {
            double angle = i * angleStep;
            int x = (int) (spawnLocation.getX() + offset * Math.cos(angle));
            int z = (int) (spawnLocation.getZ() + offset * Math.sin(angle));
            int y = spawnLocation.getBlockY() + 1;

            Location villagerLoc = new Location(spawnLocation.getWorld(), x + 0.5, y, z + 0.5);
            Villager villager = (Villager) spawnLocation.getWorld().spawnEntity(villagerLoc, EntityType.VILLAGER);

            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setCollidable(false);
            villager.setSilent(true);
            villager.customName(Component.text(color.getTextColor() + types[i].name()));
            villager.setCustomNameVisible(true);

            Merchant.apply(villager, types[i]);

            regularVillagers.add(villager);
        }
    }

    private void spawnSpeedVillagers() {
        speedVillagers.clear();

        int count = getResourceSpawnerCount();
        double offset = 13.0;
        double angleStep = Math.PI / 2.0;

        for (int i = 0; i < count; i++) {
            double angle = i * angleStep + (Math.PI / 4);
            int x = (int) (spawnLocation.getX() + offset * Math.cos(angle));
            int z = (int) (spawnLocation.getZ() + offset * Math.sin(angle));
            int y = spawnLocation.getBlockY() + 1;

            Location villagerLoc = new Location(spawnLocation.getWorld(), x + 0.5, y, z + 0.5);
            Villager villager = (Villager) spawnLocation.getWorld().spawnEntity(villagerLoc, EntityType.VILLAGER);

            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setCollidable(false);
            villager.setSilent(true);
            villager.customName(Component.text("§eSpeed Marchand"));
            villager.setCustomNameVisible(true);
            villager.setBaby();

            Merchant.apply(villager, MerchantType.SPEED);

            speedVillagers.add(villager);
        }
    }

    public void removeVillagers() {
        for (Villager villager : regularVillagers) {
            if (villager.isValid()) {
                villager.remove();
            }
        }
        regularVillagers.clear();

        for (Villager villager : speedVillagers) {
            if (villager.isValid()) {
                villager.remove();
            }
        }
        speedVillagers.clear();
    }

    public List<Villager> getSpeedVillagers() {
        return new ArrayList<>(speedVillagers);
    }

    public void placeBed() {
        if (spawnLocation == null) {
            return;
        }

        Material bedMaterial = getBedMaterial();
        if (bedMaterial == null) {
            return;
        }

        int x = spawnLocation.getBlockX();
        int y = spawnLocation.getBlockY();
        int z = spawnLocation.getBlockZ();

        Block bedFoot = spawnLocation.getWorld().getBlockAt(x, y, z);
        bedFoot.setType(bedMaterial);

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

    public void setBedDestroyed(boolean destroyed) {
        this.bedDestroyed = destroyed;
    }

    public boolean isDead(Game game) {
        return bedDestroyed;
    }
}
