package io.github.rush.guis;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;

import io.github.rush.Main;
import io.github.rush.entities.Merchant;
import io.github.rush.entities.MerchantType;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.context.PlayerOptionalContext;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.plugin.gui.ItemWithAction;
import net.momirealms.craftengine.core.plugin.gui.PagedGui;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.libraries.adventure.text.minimessage.tag.resolver.TagResolver;

public class ShopGUI {

    private static String title;

    private static GuiLayout createMainLayout() {
        return new GuiLayout(
                "_________",
                "___AAA___",
                "___AAA___",
                "___AAA___",
                "___AAA___",
                "_________")
                .addIngredient('A', Ingredient.paged())
                .addIngredient('_', GUI.fillerElement());
    }

    public static void openMainMenu(Player bukkitPlayer) {
        final net.momirealms.craftengine.core.entity.player.Player craftPlayer = Main.getInstance()
                .adaptCraftPlayer(bukkitPlayer);

        if (title == null)
            title = GUI.loadCETitle("gui.browser.speed_merchant.title");

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

        buildPagedGui(icons, createMainLayout(), title, PlayerOptionalContext.of(craftPlayer).tagResolvers(), craftPlayer);
    }

    private static ItemWithAction createCategoryIcon(
            Material material, String name, List<String> lore,
            MerchantType merchantType) {
        Item item = GUI.itemManager().wrap(new ItemStack(material));

        if (item == null || item.isEmpty())
            item = GUI.itemManager().wrap(new ItemStack(Material.BARRIER));

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
        PagedGui.builder()
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
                .refresh().open(craftPlayer);
    }
}
