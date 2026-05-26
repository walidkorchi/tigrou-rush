package io.github.rush.abstracts;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Represents a trade configuration for a villager.
 *
 * @param result       The material the villager sells
 * @param currency     The material used as currency (e.g., EMERALD, IRON_INGOT)
 * @param costAmount   The amount of currency required
 * @param resultAmount The amount of the result item
 * @param secondCost   Optional second ingredient (Material, amount)
 * @param enchantments Optional enchantments to apply to the result
 * @param durability   Optional durability percentage (0-100)
 * @param resultItem   Optional pre-built ItemStack (used for potions with data components)
 */
public record Trade(
        Material result,
        Material currency,
        int costAmount,
        int resultAmount,
        Cost secondCost,
        Map<Enchantment, Integer> enchantments,
        Integer durability,
        @Nullable ItemStack resultItem) {

    public Trade(Material result, Material currency, int costAmount) {
        this(result, currency, costAmount, 1, null, Collections.emptyMap(), null, null);
    }

    public Trade(Material result, Material currency, int costAmount, int resultAmount) {
        this(result, currency, costAmount, resultAmount, null, Collections.emptyMap(), null, null);
    }

    public Trade(Material result, Material currency, int costAmount, Map<Enchantment, Integer> enchantments) {
        this(result, currency, costAmount, 1, null, enchantments, null, null);
    }

    public Trade(Material result, int emeraldCost) {
        this(result, Material.EMERALD, emeraldCost, 1, null, Collections.emptyMap(), null, null);
    }

    public Trade(Material result, int emeraldCost, int resultAmount) {
        this(result, Material.EMERALD, emeraldCost, resultAmount, null, Collections.emptyMap(), null, null);
    }

    public Trade(Material result, int emeraldCost, Map<Enchantment, Integer> enchantments) {
        this(result, Material.EMERALD, emeraldCost, 1, null, enchantments, null, null);
    }

    public Trade withSecondCost(Material secondMaterial, int secondAmount) {
        return new Trade(result(), currency(), costAmount(), resultAmount(), new Cost(secondMaterial, secondAmount),
                enchantments(), durability(), resultItem());
    }

    public Trade withEnchantment(Enchantment enchantment, int level) {
        final Map<Enchantment, Integer> newEnchantments = new java.util.HashMap<>(enchantments());

        newEnchantments.put(enchantment, level);

        return new Trade(result(), currency(), costAmount(), resultAmount(), secondCost(), newEnchantments, durability(), resultItem());
    }

    public Trade withDurability(int percent) {
        return new Trade(result(), currency(), costAmount(), resultAmount(), secondCost(), enchantments(), percent, resultItem());
    }

    public record Cost(Material material, int amount) {
    }
}
