package io.github.rush.menus;

import io.github.rush.entities.MerchantType;
import io.github.rush.entities.Trade;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemManager;
import net.momirealms.craftengine.core.plugin.context.PlayerOptionalContext;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.plugin.gui.ItemWithAction;
import net.momirealms.craftengine.core.plugin.gui.PagedGui;
import net.momirealms.craftengine.core.util.AdventureHelper;

import net.momirealms.craftengine.libraries.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ShopGUI {

    private static final class Constants {
        static String SPEED_MERCHANT_BROWSER_TITLE = null;

        static void load() {
            try {
                File configFile = new File(
                        BukkitCraftEngine.instance().dataFolderFile(), "config.yml");
                if (configFile.exists()) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
                    String value = yaml.getString("gui.browser.speed_merchant.title");
                    if (value != null && !value.isEmpty()) {
                        SPEED_MERCHANT_BROWSER_TITLE = value;
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
            SPEED_MERCHANT_BROWSER_TITLE = "<dark_gray>Boutique";
        }
    }

    private static GuiElement createFillerElement() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        Item<ItemStack> item = itemManager().wrap(stack);
        item.customNameComponent(AdventureHelper.miniMessage().deserialize(" "));
        return GuiElement.constant(item, (element, click) -> click.cancel());
    }

    private static GuiLayout createMainLayout() {
        return new GuiLayout(
                "_________",
                "__A__A___",
                "_________",
                "__A__A___",
                "_________",
                "_________"
        )
                .addIngredient('A', Ingredient.paged())
                .addIngredient('_', createFillerElement());
    }

    private static GuiLayout createCategoryLayout() {
        return new GuiLayout(
                "AAAAAAAAA",
                "AAAAAAAAA",
                "AAAAAAAAA",
                "AAAAAAAAA",
                "AAAAAAAAA",
                "________="
        )
                .addIngredient('A', Ingredient.paged())
                .addIngredient('_', createFillerElement())
                .addIngredient('=', createBackElement());
    }

    private static final GuiLayout MAIN_LAYOUT = createMainLayout();
    private static final GuiLayout CATEGORY_LAYOUT = createCategoryLayout();

    @SuppressWarnings("unchecked")
    private static ItemManager<ItemStack> itemManager() {
        return BukkitCraftEngine.instance().itemManager();
    }

    private static GuiElement createBackElement() {
        ItemStack stack = new ItemStack(Material.BARRIER);
        Item<ItemStack> item = itemManager().wrap(stack);
        item.customNameComponent(AdventureHelper.miniMessage().deserialize("<red>Retour au menu principal"));
        return GuiElement.constant(item, (element, click) -> {
            click.cancel();
            Player bukkitPlayer = (Player) click.clicker().platformPlayer();
            openMainMenu(bukkitPlayer);
        });
    }

    public static void openMainMenu(Player bukkitPlayer) {
        var craftPlayer = BukkitCraftEngine.instance().adapt(bukkitPlayer);

        if (Constants.SPEED_MERCHANT_BROWSER_TITLE == null) {
            Constants.load();
        }

        List<ItemWithAction> icons = new ArrayList<>();

        icons.add(createCategoryIcon(craftPlayer,
                Material.IRON_SWORD,
                "<red>Armes", List.of("<gray>Acheter des épées"),
                Category.WEAPONS));

        icons.add(createCategoryIcon(craftPlayer,
                Material.LEATHER_CHESTPLATE,
                "<blue>Armure", List.of("<gray>Acheter de l'armure"),
                Category.ARMOR));

        icons.add(createCategoryIcon(craftPlayer,
                Material.POTION,
                "<dark_purple>Potions", List.of("<gray>Acheter des potions"),
                Category.POTIONS));

        icons.add(createCategoryIcon(craftPlayer,
                Material.SANDSTONE,
                "<yellow>Blocs", List.of("<gray>Acheter des blocs"),
                Category.BLOCKS));

        TagResolver[] resolvers = PlayerOptionalContext.of(craftPlayer).tagResolvers();
        buildPagedGui(icons, MAIN_LAYOUT, Constants.SPEED_MERCHANT_BROWSER_TITLE, resolvers, craftPlayer);
    }

    public static void openCategory(Player bukkitPlayer, Category category) {
        openCategory(BukkitCraftEngine.instance().adapt(bukkitPlayer), category);
    }

    private static void openCategory(
            net.momirealms.craftengine.core.entity.player.Player craftPlayer,
            Category category
    ) {
        String title = switch (category) {
            case WEAPONS -> "<dark_gray>Armes";
            case ARMOR -> "<dark_gray>Armure";
            case POTIONS -> "<dark_gray>Potions";
            case BLOCKS -> "<dark_gray>Blocs";
        };

        List<Trade> trades = switch (category) {
            case WEAPONS -> MerchantType.WEAPONSMITH.getTrades();
            case ARMOR -> MerchantType.ARMORSMITH.getTrades();
            case POTIONS -> MerchantType.ALCHEMIST.getTrades();
            case BLOCKS -> MerchantType.BUILDER.getTrades();
        };

        List<ItemWithAction> tradeItems = new ArrayList<>();
        for (Trade trade : trades) {
            tradeItems.add(createTradeItem(craftPlayer, trade));
        }

        TagResolver[] resolvers = PlayerOptionalContext.of(craftPlayer).tagResolvers();
        buildPagedGui(tradeItems, CATEGORY_LAYOUT, title, resolvers, craftPlayer);
    }

    private static ItemWithAction createCategoryIcon(
            net.momirealms.craftengine.core.entity.player.Player player,
            Material material, String name, List<String> lore,
            Category category
    ) {
        Item<ItemStack> item = itemManager().wrap(new ItemStack(material));
        if (item == null || item.isEmpty()) {
            item = itemManager().wrap(new ItemStack(Material.BARRIER));
        }

        item.customNameComponent(AdventureHelper.miniMessage().deserialize(name));
        item.loreComponent(lore.stream()
                .map(l -> AdventureHelper.miniMessage().deserialize(l))
                .collect(Collectors.toList()));

        return new ItemWithAction(item, (element, click) -> {
            click.cancel();
            openCategory(click.clicker(), category);
        });
    }

    private static ItemWithAction createTradeItem(
            net.momirealms.craftengine.core.entity.player.Player craftPlayer,
            Trade trade
    ) {
        ItemStack bukkitItem = new ItemStack(trade.result(), trade.resultAmount());
        applyTradeProperties(bukkitItem, trade);

        Item<ItemStack> item = itemManager().wrap(bukkitItem);
        if (item == null || item.isEmpty()) {
            item = itemManager().wrap(new ItemStack(Material.BARRIER));
        }

        var loreLines = new ArrayList<net.momirealms.craftengine.libraries.adventure.text.Component>();
        loreLines.add(AdventureHelper.miniMessage().deserialize(
                "<gray>Prix: <green>" + trade.costAmount() + " " + trade.currency().name().replace("_", " ").toLowerCase()));
        if (trade.secondCost() != null) {
            loreLines.add(AdventureHelper.miniMessage().deserialize(
                    "<gray>+ " + trade.secondCost().amount() + " "
                            + trade.secondCost().material().name().toLowerCase().replace("_", " ")));
        }
        if (trade.durability() != null) {
            loreLines.add(AdventureHelper.miniMessage().deserialize(
                    "<gray>Durabilité: " + trade.durability() + "%"));
        }
        item.loreComponent(loreLines);

        return new ItemWithAction(item, (element, click) -> {
            click.cancel();
            Player bukkitPlayer = (Player) click.clicker().platformPlayer();
            handleTrade(bukkitPlayer, trade);
        });
    }

    private static void applyTradeProperties(ItemStack item, Trade trade) {
        if (trade.enchantments() != null && !trade.enchantments().isEmpty()) {
            item.addEnchantments(trade.enchantments());
        }
        if (trade.durability() != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable damageable) {
                short maxDurability = item.getType().getMaxDurability();
                int damage = maxDurability * trade.durability() / 100;
                damageable.setDamage(damage);
                item.setItemMeta((ItemMeta) damageable);
            }
        }
    }

    private static void handleTrade(Player player, Trade trade) {
        Material currency = trade.currency();
        int costAmount = trade.costAmount();

        int currencyCount = 0;
        for (ItemStack stack : player.getInventory().all(currency).values()) {
            currencyCount += stack.getAmount();
        }

        if (currencyCount < costAmount) {
            player.sendMessage(Component.text(
                    "§cPas assez de " + currency.name().toLowerCase().replace("_", " ") + "!")
                    .color(NamedTextColor.RED));
            return;
        }

        ItemStack resultItem = new ItemStack(trade.result(), trade.resultAmount());
        applyTradeProperties(resultItem, trade);

        player.getInventory().removeItem(new ItemStack(currency, costAmount));
        player.getInventory().addItem(resultItem);

        player.sendMessage(Component.text("§aAchat réussi!").color(NamedTextColor.GREEN));
    }

    private static void buildPagedGui(
            List<ItemWithAction> items,
            GuiLayout layout,
            String title,
            TagResolver[] resolvers,
            net.momirealms.craftengine.core.entity.player.Player craftPlayer
    ) {
        var gui = PagedGui.builder()
                .addIngredients(items)
                .layout(layout)
                .inventoryClickConsumer(c -> {
                    String type = c.type();
                    if ("SHIFT_LEFT".equals(type) || "SHIFT_RIGHT".equals(type)
                            || "DOUBLE_CLICK".equals(type)) {
                        c.cancel();
                    }
                })
                .build()
                .title(AdventureHelper.miniMessage().deserialize(title, resolvers))
                .refresh();

        gui.open(craftPlayer);
    }

    public enum Category {
        WEAPONS,
        ARMOR,
        POTIONS,
        BLOCKS
    }
}
