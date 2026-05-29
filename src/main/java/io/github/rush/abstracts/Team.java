package io.github.rush.abstracts;

import dz.jtsgen.annotations.TypeScript;
import io.github.rush.game.GameManager;
import io.github.rush.objects.Island;
import io.github.rush.entities.GameCombatant;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;

import java.util.ArrayList;
import java.util.List;

public class Team {

    @TypeScript
    public enum Color {
        // Order matches Minecraft's DyeColor enum.
        // Color is derived from DyeColor.getFireworkColor(), which holds the exact wool
        // block colour.
        WHITE(NamedTextColor.WHITE, DyeColor.WHITE, 1),
        ORANGE(NamedTextColor.GOLD, DyeColor.ORANGE, 2),
        MAGENTA(NamedTextColor.LIGHT_PURPLE, DyeColor.MAGENTA, 3),
        LIGHT_BLUE(NamedTextColor.AQUA, DyeColor.LIGHT_BLUE, 4),
        YELLOW(NamedTextColor.YELLOW, DyeColor.YELLOW, 5),
        LIME(NamedTextColor.GREEN, DyeColor.LIME, 6),
        PINK(NamedTextColor.LIGHT_PURPLE, DyeColor.PINK, 7),
        GRAY(NamedTextColor.GRAY, DyeColor.GRAY, 8),
        LIGHT_GRAY(NamedTextColor.GRAY, DyeColor.LIGHT_GRAY, 9),
        CYAN(NamedTextColor.DARK_AQUA, DyeColor.CYAN, 10),
        PURPLE(NamedTextColor.DARK_PURPLE, DyeColor.PURPLE, 11),
        BLUE(NamedTextColor.DARK_BLUE, DyeColor.BLUE, 12),
        BROWN(NamedTextColor.DARK_RED, DyeColor.BROWN, 13),
        GREEN(NamedTextColor.DARK_GREEN, DyeColor.GREEN, 14),
        RED(NamedTextColor.RED, DyeColor.RED, 15),
        BLACK(NamedTextColor.BLACK, DyeColor.BLACK, 16);

        @Getter
        private final NamedTextColor textColor;
        @Getter
        private final org.bukkit.Color color;
        @Getter
        private final DyeColor dyeColor;
        @Getter
        private final int islandNumber;
        @Getter
        private final String sectionColor;

        Color(NamedTextColor textColor, DyeColor dye, int islandNumber) {
            this.textColor = textColor;
            this.dyeColor = dye;
            this.color = dye.getFireworkColor();
            this.islandNumber = islandNumber;
            int index = List.copyOf(NamedTextColor.NAMES.values()).indexOf(textColor);
            this.sectionColor = "§" + (index >= 0 ? Integer.toHexString(index) : "f");
        }

        public static Color[] firstN(int n) {
            Color[] values = Color.values();
            int length = Math.min(n, values.length);
            Color[] result = new Color[length];
            System.arraycopy(values, 0, result, 0, length);
            return result;
        }

        public String getChatColor() {
            return this.textColor.toString();
        }

        public DyeColor getContrastDyeColor() {
            final org.bukkit.Color c = this.dyeColor.getColor();
            final double brightness = c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114;
            return brightness > 128 ? DyeColor.BLACK : DyeColor.WHITE;
        }
    }

    @Getter
    private final String name;

    @Getter
    private final Color color;

    @Getter
    private final int maxPlayers;

    private final List<GameCombatant> players = new ArrayList<>();

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

    public Team(String name, Color color, int maxPlayers) {
        this.name = name;
        this.color = color;
        this.maxPlayers = maxPlayers;
    }

    public boolean addPlayer(GameCombatant player) {
        if (players.size() >= maxPlayers)
            return false;

        if (!players.contains(player))
            players.add(player);

        return true;
    }

    public void removePlayer(GameCombatant participant) {
        players.remove(participant);
    }

    public boolean isInTeam(GameCombatant participant) {
        return players.contains(participant);
    }

    public List<GameCombatant> getPlayers() {
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
        if (spawnLocation == null)
            return;
        enderChestLocations.clear();
        final int[] dir = Island.Layout.ISLAND_DIRECTIONS[islandIndex];
        final int perpX = dir[1];
        final int speedOffset = 13;
        final int enderChestOffset = speedOffset - 1;
        enderChestLocations.addAll(GameManager.placeIslandEnderChests(
                spawnLocation.getWorld(),
                spawnLocation.getBlockX(),
                spawnLocation.getBlockZ(),
                spawnLocation.getBlockY() - 2,
                dir, perpX, enderChestOffset,
                Island.Layout.facingTowardsCenter(islandIndex),
                getGeneratorCount()));
    }

    public int getGeneratorCount() {
        return Math.max(2, Math.min(4, players.size()));
    }

    public void placeBed(int islandIndex) {
        if (spawnLocation == null)
            return;

        final Material bedMaterial = Team.bedMaterialFor(color);

        if (bedMaterial == null)
            return;

        final int[] coords = bedCoords(spawnLocation.getBlockX(), spawnLocation.getBlockZ(),
                spawnLocation.getBlockY() - 2, islandIndex);

        placeBedAt(spawnLocation.getWorld(), coords[0], coords[1], coords[2],
                Island.Layout.facingTowardsCenter(islandIndex), bedMaterial);

        bedLocation = new Location(spawnLocation.getWorld(), coords[0], coords[1], coords[2]);
    }

    public static int[] bedCoords(int spawnX, int spawnZ, int bedY, int islandIndex) {
        final int[] dir = Island.Layout.ISLAND_DIRECTIONS[islandIndex];
        return new int[] { spawnX + dir[0] * 6, bedY, spawnZ + dir[1] * 6 };
    }

    public static void placeIslandBed(World world, Island island, int islandIndex, int bedY, Color color) {
        final int[] coords = bedCoords(island.getX(), island.getZ(), bedY, islandIndex);
        placeBedAt(world, coords[0], coords[1], coords[2], Island.Layout.facingTowardsCenter(islandIndex),
                bedMaterialFor(color));
    }

    public static void placeBedAt(World world, int x, int y, int z, BlockFace facing, Material bedMaterial) {
        final Block bedFoot = world.getBlockAt(x, y, z);
        final Bed footData = (Bed) bedMaterial.createBlockData();

        footData.setPart(Bed.Part.FOOT);
        footData.setFacing(facing);
        bedFoot.setBlockData(footData);

        final Block bedHead = bedFoot.getRelative(facing);
        final Bed headData = (Bed) bedMaterial.createBlockData();

        headData.setPart(Bed.Part.HEAD);
        headData.setFacing(facing);
        bedHead.setBlockData(headData);
    }

    public static Material bedMaterialFor(Color color) {
        return Material.valueOf(color.name() + "_BED");
    }

}
