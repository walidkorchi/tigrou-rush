package io.github.rush.entities;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a trade configuration for a villager.
 *
 * @param result       The material the villager sells
 * @param emeraldCost  The cost in emeralds (primary currency)
 * @param resultAmount The amount of the result item
 * @param secondCost   Optional second ingredient (Material, amount)
 * @param enchantments Optional enchantments to apply to the result
 */
public record Trade(
        Material result,
        int emeraldCost,
        int resultAmount,
        Cost secondCost,
        Map<Enchantment, Integer> enchantments) {

    public Trade(Material result, int emeraldCost) {
        this(result, emeraldCost, 1, null, Collections.emptyMap());
    }

    public Trade(Material result, int emeraldCost, int resultAmount) {
        this(result, emeraldCost, resultAmount, null, Collections.emptyMap());
    }

    public Trade(Material result, int emeraldCost, Map<Enchantment, Integer> enchantments) {
        this(result, emeraldCost, 1, null, enchantments);
    }

    public Trade withSecondCost(Material secondMaterial, int secondAmount) {
        return new Trade(result(), emeraldCost(), resultAmount(), new Cost(secondMaterial, secondAmount),
                enchantments());
    }

    public Trade withEnchantment(Enchantment enchantment, int level) {
        Map<Enchantment, Integer> newEnchantments = new java.util.HashMap<>(enchantments());
        newEnchantments.put(enchantment, level);
        return new Trade(result(), emeraldCost(), resultAmount(), secondCost(), newEnchantments);
    }

    public record Cost(Material material, int amount) {
    }
}
