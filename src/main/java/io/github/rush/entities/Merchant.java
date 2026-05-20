package io.github.rush.entities;

import io.github.rush.Main;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class Merchant {

    public static final NamespacedKey MERCHANT_TYPE_KEY = new NamespacedKey(Main.getInstance(), "merchant_type");

    private Merchant() {
    }

    /**
     * Reads the merchant type from a villager's PersistentDataContainer.
     *
     * @return the MerchantType, or null if this villager is not a rush merchant
     */
    public static MerchantType getType(Villager villager) {
        String name = villager.getPersistentDataContainer().get(MERCHANT_TYPE_KEY, PersistentDataType.STRING);
        if (name == null) return null;
        try {
            return MerchantType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Checks whether this villager is a rush merchant (has the PDC tag).
     */
    public static boolean isMerchant(Villager villager) {
        return villager.getPersistentDataContainer().has(MERCHANT_TYPE_KEY, PersistentDataType.STRING);
    }

    /**
     * Applies a merchant type to a villager, setting its profession and trades,
     * and tags it with a PersistentDataContainer key for later identification.
     *
     * @param villager     the villager to modify
     * @param merchantType the type of merchant to apply
     */
    public static void apply(Villager villager, MerchantType merchantType) {
        villager.setProfession(merchantType.getProfession());

        List<MerchantRecipe> recipes = merchantType.getTrades().stream()
                .map(Merchant::toMerchantRecipe)
                .toList();

        villager.setRecipes(recipes);

        villager.getPersistentDataContainer().set(MERCHANT_TYPE_KEY, PersistentDataType.STRING, merchantType.name());
    }

    /**
     * Converts a Trade to a MerchantRecipe.
     *
     * @param trade the trade to convert
     * @return the corresponding MerchantRecipe
     */
    private static MerchantRecipe toMerchantRecipe(Trade trade) {
        final ItemStack result = new ItemStack(trade.result(), trade.resultAmount());

        if (trade.enchantments() != null && !trade.enchantments().isEmpty()) {
            result.addEnchantments(trade.enchantments());
        }

        final MerchantRecipe recipe = new MerchantRecipe(result, 0, Integer.MAX_VALUE, false);

        recipe.addIngredient(new ItemStack(trade.currency(), trade.costAmount()));

        if (trade.secondCost() != null) {
            recipe.addIngredient(new ItemStack(trade.secondCost().material(), trade.secondCost().amount()));
        }

        return recipe;
    }
}
