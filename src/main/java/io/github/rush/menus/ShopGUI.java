package io.github.rush.menus;

import io.github.rush.Main;
import io.github.rush.entities.Merchant;
import io.github.rush.entities.MerchantType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemManager;
import net.momirealms.craftengine.core.plugin.context.PlayerOptionalContext;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.plugin.gui.ItemWithAction;
import net.momirealms.craftengine.core.plugin.gui.PagedGui;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.libraries.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;

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
                        Main.getInstance().getCraftEngineDataFolder(), "config.yml");
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
        final Item item = itemManager().createCustomWrappedItem(Key.of("tland:empty_slot"), null);
        if (item.platformItem() instanceof ItemStack itemStack) {
            itemStack.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                    TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        }
        return GuiElement.constant(item, (element, click) -> click.cancel());
    }

    private static GuiLayout createMainLayout() {
        return new GuiLayout(
                "_________",
                "__A__A___",
                "_________",
                "__A__A___",
                "_________",
                "_________")
                .addIngredient('A', Ingredient.paged())
                .addIngredient('_', createFillerElement());
    }

    private static ItemManager itemManager() {
        return Main.getInstance().getCraftEngineItemManager();
    }

    public static void openMainMenu(Player bukkitPlayer) {
        final net.momirealms.craftengine.core.entity.player.Player craftPlayer = Main.getInstance()
                .adaptCraftPlayer(bukkitPlayer);

        if (Constants.SPEED_MERCHANT_BROWSER_TITLE == null) {
            Constants.load();
        }

        final List<ItemWithAction> icons = new ArrayList<>();

        icons.add(createCategoryIcon(
                Material.IRON_SWORD,
                "<red>Armes", List.of("<gray>Acheter des épées"),
                MerchantType.WEAPONSMITH));

        icons.add(createCategoryIcon(
                Material.LEATHER_CHESTPLATE,
                "<blue>Armure", List.of("<gray>Acheter de l'armure"),
                MerchantType.ARMORSMITH));

        icons.add(createCategoryIcon(
                Material.POTION,
                "<dark_purple>Potions", List.of("<gray>Acheter des potions"),
                MerchantType.ALCHEMIST));

        icons.add(createCategoryIcon(
                Material.SANDSTONE,
                "<yellow>Blocs", List.of("<gray>Acheter des blocs"),
                MerchantType.BUILDER));

        final TagResolver[] resolvers = PlayerOptionalContext.of(craftPlayer).tagResolvers();

        buildPagedGui(icons, createMainLayout(), Constants.SPEED_MERCHANT_BROWSER_TITLE, resolvers, craftPlayer);
    }

    private static ItemWithAction createCategoryIcon(
            Material material, String name, List<String> lore,
            MerchantType merchantType) {
        Item item = itemManager().wrap(new ItemStack(material));

        if (item == null || item.isEmpty()) {
            item = itemManager().wrap(new ItemStack(Material.BARRIER));
        }

        item.customNameComponent(AdventureHelper.miniMessage().deserialize(name));
        item.loreComponent(lore.stream()
                .map(l -> AdventureHelper.miniMessage().deserialize(l))
                .collect(Collectors.toList()));

        return new ItemWithAction(item, (element, click) -> {
            click.cancel();

            final Player bukkitPlayer = (Player) click.clicker().platformPlayer();
            final org.bukkit.inventory.Merchant merchant = Merchant.createBukkitMerchant(merchantType);

            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                bukkitPlayer.openInventory(MenuType.MERCHANT
                        .builder()
                        .title(Component.translatable(Merchant.typeKey(merchantType)))
                        .merchant(merchant)
                        .build(bukkitPlayer));
            });
        });
    }

    private static void buildPagedGui(
            List<ItemWithAction> items,
            GuiLayout layout,
            String title,
            TagResolver[] resolvers,
            net.momirealms.craftengine.core.entity.player.Player craftPlayer) {
        final Gui gui = PagedGui.builder()
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
}
