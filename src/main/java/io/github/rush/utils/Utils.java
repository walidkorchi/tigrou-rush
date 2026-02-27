package io.github.rush.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class Utils {
    public static int randInt(int min, int max) {
        return new Random().nextInt((max - min) + 1) + min;
    }

    public static Block getBedNeighbor(Block head) {
        if (isBedBlock(head.getRelative(BlockFace.EAST))) {
            return head.getRelative(BlockFace.EAST);
        } else if (isBedBlock(head.getRelative(BlockFace.WEST))) {
            return head.getRelative(BlockFace.WEST);
        } else if (isBedBlock(head.getRelative(BlockFace.SOUTH))) {
            return head.getRelative(BlockFace.SOUTH);
        } else {
            return head.getRelative(BlockFace.NORTH);
        }
    }

    public static boolean isBedBlock(Block block) {
        if (block == null) return false;
        return block.getType().name().contains("BED");
    }

    public static Map<String, Object> locationSerialize(Location location) {
        if (location == null) return null;
        Map<String, Object> data = new HashMap<>();
        data.put("world", location.getWorld().getName());
        data.put("x", location.getX());
        data.put("y", location.getY());
        data.put("z", location.getZ());
        data.put("yaw", location.getYaw());
        data.put("pitch", location.getPitch());
        return data;
    }

    public static Location locationDeserialize(Object object) {
        if (object == null) return null;
        
        Map<String, Object> data;
        if (object instanceof Map) {
            data = (Map<String, Object>) object;
        } else {
            return null;
        }

        String worldName = data.get("world") != null ? data.get("world").toString() : null;
        if (worldName == null) return null;

        double x = ((Number) data.get("x")).doubleValue();
        double y = ((Number) data.get("y")).doubleValue();
        double z = ((Number) data.get("z")).doubleValue();
        float yaw = data.get("yaw") != null ? ((Number) data.get("yaw")).floatValue() : 0;
        float pitch = data.get("pitch") != null ? ((Number) data.get("pitch")).floatValue() : 0;

        return new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
    }
}
