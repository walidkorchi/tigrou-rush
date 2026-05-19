package io.github.rush.menus;

import io.github.rush.entities.MerchantType;
import io.github.rush.entities.Trade;
import io.github.rush.utils.ItemBuilder;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ShopGUI {

    private static final String MAIN_MENU_TITLE = "§8Boutique";
    private static final String WEAPONS_TITLE = "§8Armes";
    private static final String ARMOR_TITLE = "§8Armure";
    private static final String POTIONS_TITLE = "§8Potions";
    private static final String BLOCKS_TITLE = "§8Blocs";

    public static void openMainMenu(Player player) {
        GUI gui = new GUI(MAIN_MENU_TITLE, 2);

        ItemStack swords = createMenuItem(
                Material.IRON_SWORD,
                "§cArmes",
                "§7Acheter des épées");
        gui.addItem(0, swords, p -> openCategory(p, Category.WEAPONS));

        ItemStack armor = createMenuItem(
                Material.LEATHER_CHESTPLATE,
                "§9Armure",
                "§7Acheter de l'armure");
        gui.addItem(1, armor, p -> openCategory(p, Category.ARMOR));

        ItemStack potions = createMenuItem(
                Material.POTION,
                "§5Potions",
                "§7Acheter des potions");
        gui.addItem(2, potions, p -> openCategory(p, Category.POTIONS));

        ItemStack blocks = createMenuItem(
                Material.SANDSTONE,
                "§eBlocs",
                "§7Acheter des blocs");
        gui.addItem(3, blocks, p -> openCategory(p, Category.BLOCKS));

        gui.openGUI(player);
    }

    public static void openCategory(Player player, Category category) {
        String title = switch (category) {
            case WEAPONS -> WEAPONS_TITLE;
            case ARMOR -> ARMOR_TITLE;
            case POTIONS -> POTIONS_TITLE;
            case BLOCKS -> BLOCKS_TITLE;
        };

        GUI gui = new GUI(title, 3);

        List<Trade> trades = switch (category) {
            case WEAPONS -> MerchantType.WEAPONSMITH.getTrades();
            case ARMOR -> MerchantType.ARMORSMITH.getTrades();
            case POTIONS -> MerchantType.ALCHEMIST.getTrades();
            case BLOCKS -> MerchantType.BUILDER.getTrades();
        };

        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            ItemStack item = createTradeItem(trade);
            final int tradeIndex = i;
            gui.addItem(i, item, p -> handleTrade(p, trades.get(tradeIndex)));
        }

        gui.openGUI(player);
    }

    private static void handleTrade(Player player, Trade trade) {
        Material currency = trade.currency();
        int costAmount = trade.costAmount();

        int currencyCount = 0;
        for (ItemStack item : player.getInventory().all(currency).values()) {
            currencyCount += item.getAmount();
        }

        if (currencyCount < costAmount) {
            player.sendMessage(Component.text("§cPas assez de " + currency.name().toLowerCase().replace("_", " ") + "!")
                    .color(NamedTextColor.RED));
            return;
        }

        ItemStack resultItem = new ItemStack(trade.result(), trade.resultAmount());
        applyTradeProperties(resultItem, trade);

        player.getInventory().removeItem(new ItemStack(currency, costAmount));
        player.getInventory().addItem(resultItem);

        player.sendMessage(Component.text("§aAchat réussi!").color(NamedTextColor.GREEN));
    }

    private static ItemStack createMenuItem(Material material, String name, String description) {
        return ItemBuilder.of(material).name(name).lore(description).build();
    }

    private static void applyTradeProperties(ItemStack item, Trade trade) {
        if (trade.enchantments() != null && !trade.enchantments().isEmpty()) {
            item.addEnchantments(trade.enchantments());
        }
        if (trade.durability() != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable damageable) {
                short maxDurability = item.getType().getMaxDurability();
                int damage = (int) (maxDurability * (trade.durability() / 100.0));
                damageable.setDamage(damage);
                item.setItemMeta((ItemMeta) damageable);
            }
        }
    }

    private static ItemStack createTradeItem(Trade trade) {
        ItemStack item = new ItemStack(trade.result(), trade.resultAmount());
        applyTradeProperties(item, trade);

        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        String currencyName = trade.currency().name().replace("_", " ").toLowerCase();
        lore.add(Component.text("§7Prix: §a" + trade.costAmount() + " " + currencyName));
        if (trade.secondCost() != null) {
            lore.add(Component.text("§7+ " + trade.secondCost().amount() + " " + trade.secondCost().material().name()));
        }
        if (trade.durability() != null) {
            lore.add(Component.text("§7Durabilité: " + trade.durability() + "%"));
        }
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));

        return item;
    }

    public enum Category {
        WEAPONS,
        ARMOR,
        POTIONS,
        BLOCKS
    }
}
