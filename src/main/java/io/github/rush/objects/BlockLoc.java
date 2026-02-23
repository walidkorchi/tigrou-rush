package io.github.rush.objects;

import java.util.Objects;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;

public class BlockLoc {

    private final String world;
    private final int x;
    private final int y;
    private final int z;

    public BlockLoc(String world, int x, int y, int z) {
        this.world = Objects.requireNonNull(world, "world");
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockLoc(Location location) {
        this(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public BlockLoc(Block block) {
        this(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public boolean isInChunk(Chunk chunk) {
        return x >> 4 == chunk.getX() && z >> 4 == chunk.getZ() && world.equals(chunk.getWorld().getName());
    }

    public boolean blockEquals(Block block) {
        return x == block.getX() && y == block.getY() && z == block.getZ() && world.equals(block.getWorld().getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BlockLoc that = (BlockLoc) o;
        return x == that.x && y == that.y && z == that.z && world.equals(that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }

    @Override
    public String toString() {
        return "BlockLocation{world='" + world + "', x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
