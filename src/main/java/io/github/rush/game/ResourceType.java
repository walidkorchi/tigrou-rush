package io.github.rush.game;

import org.bukkit.Material;

import lombok.Getter;

public enum ResourceType {
    COPPER("copper", Material.COPPER_INGOT, 1000),
    IRON("iron", Material.IRON_INGOT, 10000),
    GOLD("gold", Material.GOLD_INGOT, 20000),
    DIAMOND("diamond", Material.DIAMOND, 300000);

    @Getter
    private final String configKey;
    @Getter
    private final Material material;
    @Getter
    private final int spawnInterval;

    ResourceType(String configKey, Material material, int spawnInterval) {
        this.configKey = configKey;
        this.material = material;
        this.spawnInterval = spawnInterval;
    }

    public long getSpawnIntervalTicks() {
        return Math.round(((double) spawnInterval) / 1000.0f * 20.0f);
    }

}
