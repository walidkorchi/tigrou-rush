package io.github.rush.entities;

import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.List;

public class Merchant {

    private Merchant() {
    }

    /**
     * Applies a merchant type to a villager, setting its profession and trades.
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
