package io.github.rush.game;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResourceType {

    private static final Map<String, ResourceType> REGISTRY = new LinkedHashMap<>();

    @Getter
    private final String configKey;
    @Getter
    private final int spawnInterval;
    @Getter
    private final List<ResourceDrop> drops;
    @Getter
    private final double spread;

    private ResourceType(String configKey, int spawnInterval, List<ResourceDrop> drops, double spread) {
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

            REGISTRY.put(key, new ResourceType(key, spawnInterval, drops, spread));
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

                if (mat != null) {
                    drops.add(new ResourceDrop(mat, amount));
                }
            }
        }

        return drops;
    }

    public static Collection<ResourceType> values() {
        return REGISTRY.values();
    }

    public static ResourceType valueOf(String key) {
        final ResourceType type = REGISTRY.get(key);
        if (type == null) throw new IllegalArgumentException("Unknown resource type: " + key);
        return type;
    }

    public long getSpawnIntervalTicks() {
        return Math.round(((double) spawnInterval) / 1000.0f * 20.0f);
    }

    public Material getMaterial() {
        return drops.isEmpty() ? Material.STONE : drops.get(0).material();
    }

    public record ResourceDrop(Material material, int amount) {
    }

}
