package io.github.rush.entities;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.PotionContents;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum MerchantType {

    WEAPONSMITH(Profession.WEAPONSMITH, new ArrayList<>(List.of(
            new Trade(Material.IRON_SWORD, Material.IRON_INGOT, 1, Map.of(Enchantment.SHARPNESS, 1)),
            new Trade(Material.IRON_SWORD, Material.IRON_INGOT, 3, Map.of(Enchantment.SHARPNESS, 2)),
            new Trade(Material.IRON_SWORD, Material.IRON_INGOT, 7, Map.of(Enchantment.SHARPNESS, 3)),
            new Trade(Material.DIAMOND_SWORD, Material.IRON_INGOT, 5, Map.of(Enchantment.SHARPNESS, 2))
                    .withSecondCost(Material.DIAMOND, 1)
                    .withDurability(85),
            new Trade(Material.FLINT_AND_STEEL, Material.GOLD_INGOT, 1),
            new Trade(Material.TNT, Material.IRON_INGOT, 5)))),
    BUILDER(Profession.CARTOGRAPHER, new ArrayList<>(List.of(
            new Trade(Material.SANDSTONE, Material.COPPER_INGOT, 1, 4),
            new Trade(Material.END_STONE, Material.IRON_INGOT, 2, 4),
            new Trade(Material.IRON_PICKAXE, Material.IRON_INGOT, 1, Map.of(Enchantment.EFFICIENCY, 1)),
            new Trade(Material.IRON_PICKAXE, Material.COPPER_INGOT, 5, Map.of(Enchantment.EFFICIENCY, 2)),
            new Trade(Material.IRON_PICKAXE, Material.GOLD_INGOT, 1, Map.of(Enchantment.EFFICIENCY, 3))))),
    ALCHEMIST(Profession.CLERIC, new ArrayList<>(List.of(
            new Trade(Material.GOLDEN_APPLE, Material.IRON_INGOT, 1),
            new Trade(createHealingPotion(), Material.GOLD_INGOT, 1)))),
    ARMORSMITH(Profession.ARMORER, new ArrayList<>(List.of(
            new Trade(Material.LEATHER_CHESTPLATE, Material.IRON_INGOT, 2),
            new Trade(Material.LEATHER_CHESTPLATE, Material.IRON_INGOT, 5, Map.of(Enchantment.PROTECTION, 1)),
            new Trade(Material.LEATHER_CHESTPLATE, Material.GOLD_INGOT, 1, Map.of(Enchantment.PROTECTION, 2)),
            new Trade(Material.LEATHER_CHESTPLATE, Material.GOLD_INGOT, 5, Map.of(Enchantment.PROTECTION, 3)),
            new Trade(Material.COMPASS, Material.EMERALD, 1)))),
    SPEED(Profession.LIBRARIAN, new ArrayList<>(List.of(
            new Trade(Material.BOOK, Material.EMERALD, 1))));

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

    public static MerchantType[] firstN(int n) {
        MerchantType[] values = MerchantType.values();
        int length = Math.min(n, values.length);
        MerchantType[] result = new MerchantType[length];
        System.arraycopy(values, 0, result, 0, length);
        return result;
    }

    private static Material createHealingPotion() {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        PotionContents potionContents = PotionContents.potionContents()
                .potion(PotionType.HEALING)
                .addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 0, true, true, true))
                .build();
        potion.setData(DataComponentTypes.POTION_CONTENTS, potionContents);
        return potion.getType();
    }
}
