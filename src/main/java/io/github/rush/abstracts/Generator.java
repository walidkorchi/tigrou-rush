package io.github.rush.abstracts;

import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Generator implements Runnable {

    public static final class Type {

        private static final Map<String, Type> REGISTRY = new LinkedHashMap<>();

        @Getter private final String configKey;
        @Getter private final int spawnInterval;
        @Getter private final List<ResourceDrop> drops;
        @Getter private final double spread;

        private Type(String configKey, int spawnInterval, List<ResourceDrop> drops, double spread) {
            this.configKey = configKey;
            this.spawnInterval = spawnInterval;
            this.drops = drops;
            this.spread = spread;
        }

        public static void loadFromConfig(FileConfiguration config) {
            REGISTRY.clear();
            final ConfigurationSection resourceSection = config.getConfigurationSection("resource");
            if (resourceSection == null) return;
            for (String key : resourceSection.getKeys(false)) {
                final ConfigurationSection section = resourceSection.getConfigurationSection(key);
                if (section == null) continue;
                final int spawnInterval = section.getInt("spawn-interval");
                final double spread = section.getDouble("spread", 0.0);
                final List<ResourceDrop> drops = parseDrops(section);
                REGISTRY.put(key, new Type(key, spawnInterval, drops, spread));
            }
        }

        private static List<ResourceDrop> parseDrops(ConfigurationSection section) {
            final List<Map<?, ?>> items = section.getMapList("item");
            final List<ResourceDrop> drops = new ArrayList<>();
            for (Map<?, ?> entry : items) {
                final String typeName = (String) entry.get("type");
                final int amount = entry.containsKey("amount") ? ((Number) entry.get("amount")).intValue() : 1;
                if (typeName != null) {
                    final Material mat = Material.getMaterial(typeName);
                    if (mat != null) drops.add(new ResourceDrop(mat, amount));
                }
            }
            return drops;
        }

        public static Collection<Type> values() {
            return REGISTRY.values();
        }

        public static Type valueOf(String key) {
            final Type type = REGISTRY.get(key);
            if (type == null) throw new IllegalArgumentException("Unknown generator type: " + key);
            return type;
        }

        public long getSpawnIntervalTicks() {
            return Math.round(((double) spawnInterval) / 1000.0f * 20.0f);
        }

        public Material getMaterial() {
            return drops.isEmpty() ? Material.STONE : drops.get(0).material();
        }

        public record ResourceDrop(Material material, int amount) {}
    }

    private static final Random RANDOM = new Random();

    private final Game game;

    @Getter private final Type type;
    @Getter private final Location location;

    public Generator(Game game, Type type, Location location) {
        this.game = game;
        this.type = type;
        this.location = location.clone();
    }

    @Override
    public void run() {
        if (game.getState() == GameState.RUNNING) {
            double spread = type.getSpread();
            for (Type.ResourceDrop drop : type.getDrops()) {
                final ItemStack itemStack = new ItemStack(drop.material(), drop.amount());
                final Location dropLocation = location.clone();
                dropLocation.setY(dropLocation.getY() + 1.0);
                dropLocation.setX(dropLocation.getX() + 0.5);
                dropLocation.setZ(dropLocation.getZ() + 0.5);
                if (spread > 0) {
                    dropLocation.add(
                            (RANDOM.nextDouble() - 0.5) * spread,
                            0,
                            (RANDOM.nextDouble() - 0.5) * spread);
                }
                location.getWorld().dropItem(dropLocation, itemStack, item -> {
                    item.setVelocity(new Vector(0, 0, 0));
                    item.setPickupDelay(0);
                });
            }
        }
    }
}
