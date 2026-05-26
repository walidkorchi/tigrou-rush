package io.github.rush.entities;

import io.github.rush.abstracts.Trade;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public enum MerchantType {

    WEAPONSMITH     (Profession.WEAPONSMITH,    Material.IRON_SWORD),
    BUILDER         (Profession.CARTOGRAPHER,   Material.SANDSTONE),
    ALCHEMIST       (Profession.CLERIC,         Material.GOLDEN_APPLE),
    ARMORSMITH      (Profession.ARMORER,        Material.LEATHER_CHESTPLATE),
    SPEED           (Profession.LIBRARIAN,      null);

    @Getter
    private final Profession profession;
    @Getter
    private final @Nullable Material displayItem;
    private @Nullable List<Trade> trades;

    MerchantType(Profession profession, @Nullable Material displayItem) {
        this.profession = profession;
        this.displayItem = displayItem;
    }

    public List<Trade> getTrades() {
        return trades == null ? List.of() : trades;
    }

    public static void loadFromConfig(FileConfiguration config) {
        final ConfigurationSection merchants = config.getConfigurationSection("merchants");

        if (merchants == null)
            return;

        for (MerchantType type : values()) {
            final ConfigurationSection typeSection = merchants.getConfigurationSection(type.name().toLowerCase());

            if (typeSection == null)
                continue;

            final List<?> tradesList = typeSection.getList("trades");

            if (tradesList == null || tradesList.isEmpty())
                continue;

            final List<Trade> parsed = new ArrayList<>();

            for (Object obj : tradesList) {
                if (!(obj instanceof Map<?, ?> entry))
                    continue;

                final Trade trade = parseTrade(entry);

                if (trade != null) {
                    parsed.add(trade);
                }
            }

            if (!parsed.isEmpty()) {
                type.trades = parsed;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Trade parseTrade(Map<?, ?> entry) {
        Material result = null;
        Map<String, Object> enchantMap = null;
        Integer durability = null;
        int resultAmount = 1;

        if (entry.containsKey("potion")) {
            final PotionType potionType = parsePotionType((String) entry.get("potion"));
            final boolean upgrade = String.valueOf(entry.get("upgrade")).equals("true");
            final ItemStack potion = buildPotionStack(potionType, upgrade);

            result = potion.getType();
        } else if (entry.containsKey("result")) {
            result = Material.getMaterial((String) entry.get("result"));
        }

        if (result == null)
            return null;

        if (entry.containsKey("resultAmount")) {
            resultAmount = ((Number) entry.get("resultAmount")).intValue();
        }

        if (entry.get("enchantments") instanceof Map<?, ?> raw) {
            enchantMap = (Map<String, Object>) raw;
        }

        if (entry.containsKey("durability")) {
            durability = ((Number) entry.get("durability")).intValue();
        }

        final List<Map<String, Object>> costs = (List<Map<String, Object>>) entry.get("cost");

        if (costs == null || costs.isEmpty())
            return null;

        final Map<String, Object> firstCost = costs.get(0);
        final Material currency = Material.getMaterial((String) firstCost.get("material"));
        final int costAmount = ((Number) firstCost.getOrDefault("amount", 1)).intValue();

        if (currency == null)
            return null;

        Trade trade = new Trade(result, currency, costAmount, resultAmount, null,
                parseEnchantments(enchantMap), durability);

        if (costs.size() >= 2) {
            final Map<String, Object> second = costs.get(1);
            final Material secondMat = Material.getMaterial((String) second.get("material"));
            final int secondAmt = ((Number) second.getOrDefault("amount", 1)).intValue();

            if (secondMat != null) {
                trade = trade.withSecondCost(secondMat, secondAmt);
            }
        }

        return trade;
    }

    private static Map<Enchantment, Integer> parseEnchantments(@Nullable Map<String, Object> raw) {
        if (raw == null)
            return Collections.emptyMap();

        final Map<Enchantment, Integer> result = new java.util.HashMap<>();

        for (Map.Entry<String, Object> e : raw.entrySet()) {
            final Enchantment ench = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(
                    NamespacedKey.minecraft(e.getKey().toLowerCase()));

            if (ench != null) {
                result.put(ench, ((Number) e.getValue()).intValue());
            }
        }
        return result;
    }

    private static PotionType parsePotionType(String name) {
        try {
            return PotionType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PotionType.HEALING;
        }
    }

    private static ItemStack buildPotionStack(PotionType type, boolean upgrade) {
        final ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        final PotionContents.Builder builder = PotionContents.potionContents()
                .potion(type);

        if (upgrade) {
            PotionEffect effect = switch (type) {
                case HEALING -> new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 0, false, true, true);
                default -> null;
            };
            if (effect != null) {
                builder.addCustomEffect(effect);
            }
        }

        potion.setData(DataComponentTypes.POTION_CONTENTS, builder.build());

        return potion;
    }

    public static MerchantType[] firstN(int n) {
        final MerchantType[] values = MerchantType.values();
        final int length = Math.min(n, values.length);
        final MerchantType[] result = new MerchantType[length];

        System.arraycopy(values, 0, result, 0, length);

        return result;
    }

}
