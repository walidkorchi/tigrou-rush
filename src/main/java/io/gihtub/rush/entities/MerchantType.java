package io.gihtub.rush.entities;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager.Profession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum MerchantType {

    WEAPONSMITH(Profession.WEAPONSMITH, new ArrayList<>(List.of(
            new Trade(Material.IRON_SWORD, 1, Map.of(Enchantment.SHARPNESS, 1)),
            new Trade(Material.IRON_SWORD, 3, Map.of(Enchantment.SHARPNESS, 2)),
            new Trade(Material.IRON_SWORD, 5, Map.of(Enchantment.SHARPNESS, 2)),
            new Trade(Material.DIAMOND_SWORD, 5, Map.of(Enchantment.SHARPNESS, 2))
                    .withSecondCost(Material.DIAMOND, 1),
            new Trade(Material.TNT, 4),
            new Trade(Material.TNT, 1).withSecondCost(Material.DIAMOND, 1),
            new Trade(Material.FLINT_AND_STEEL, 1)))),
    BUILDER(Profession.CARTOGRAPHER, new ArrayList<>(List.of(
            new Trade(Material.SANDSTONE, 1, 4),
            new Trade(Material.END_STONE, 4),
            new Trade(Material.WOODEN_PICKAXE, 4, Map.of(Enchantment.EFFICIENCY, 1)),
            new Trade(Material.STONE_PICKAXE, 2, Map.of(Enchantment.EFFICIENCY, 2))
                    .withSecondCost(Material.IRON_INGOT, 2),
            new Trade(Material.IRON_PICKAXE, 1, Map.of(Enchantment.EFFICIENCY, 2))
                    .withSecondCost(Material.GOLD_INGOT, 1)))),
    ALCHEMIST(Profession.CLERIC, new ArrayList<>(List.of(
            new Trade(Material.GOLDEN_APPLE, 1),
            new Trade(Material.POTION, 3).withSecondCost(Material.DIAMOND, 3)))),
    ARMORSMITH(Profession.ARMORER, new ArrayList<>(List.of(
            new Trade(Material.POTION, 1, 2),
            new Trade(Material.LEATHER_CHESTPLATE, 2),
            new Trade(Material.LEATHER_CHESTPLATE, 15, Map.of(Enchantment.PROTECTION, 1)),
            new Trade(Material.LEATHER_CHESTPLATE, 1, Map.of(Enchantment.PROTECTION, 2))
                    .withSecondCost(Material.GOLD_INGOT, 1),
            new Trade(Material.LEATHER_CHESTPLATE, 2).withSecondCost(Material.DIAMOND, 2),
            new Trade(Material.COMPASS, 1)))),
    SPEED(Profession.LIBRARIAN, new ArrayList<>(List.of(
            new Trade(Material.BOOK, 1))));

    private final Profession profession;
    private final List<Trade> trades;

    MerchantType(Profession profession, List<Trade> trades) {
        this.profession = profession;
        this.trades = trades;
    }

    public Profession getProfession() {
        return profession;
    }

    public List<Trade> getTrades() {
        return trades;
    }
}
