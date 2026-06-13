package io.github.rush.guis;

import io.github.rush.Main;
import io.github.rush.utils.i18n;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.context.PlayerOptionalContext;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.plugin.gui.ItemWithAction;
import net.momirealms.craftengine.core.plugin.gui.PagedGui;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.libraries.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.List;

public final class GameSelectionGUI {

    private GameSelectionGUI() {
    }

    private static String title;

    private static GuiLayout createLayout() {
        return new GuiLayout(
                "_________",
                "__AAAAA__",
                "__AAAAA__",
                "_________")
                .addIngredient('A', Ingredient.paged())
                .addIngredient('_', GUI.fillerElement());
    }

    public static void open(Player player) {
        if (title == null)
            title = GUI.loadCETitle("gui.browser.game_selection_browser.title");

        final net.momirealms.craftengine.core.entity.player.Player craftPlayer = Main.getInstance()
                .adaptCraftPlayer(player);
        final TagResolver[] resolvers = PlayerOptionalContext.of(craftPlayer).tagResolvers();

        PagedGui.builder()
            .addIngredients(List.of(createRushModeIcon()))
            .layout(createLayout())
            .inventoryClickConsumer(c -> {
                final String type = c.type();
                if ("SHIFT_LEFT".equals(type) || "SHIFT_RIGHT".equals(type)
                        || "DOUBLE_CLICK".equals(type))
                    c.cancel();
            })
            .build()
            .title(AdventureHelper.miniMessage().deserialize(title, resolvers))
            .refresh()
            .open(craftPlayer);
    }

    private static ItemWithAction createRushModeIcon() {
        final Item item = GUI.itemManager().wrap(new ItemStack(Material.RED_BED));

        item.customNameComponent(AdventureHelper.miniMessage().deserialize(
                i18n.raw("rush.game_selection_rush_name")));
        item.loreComponent(List.of(
                AdventureHelper.miniMessage().deserialize(i18n.raw("rush.game_selection_rush_lore1")),
                AdventureHelper.miniMessage().deserialize(i18n.raw("rush.game_selection_rush_lore2")),
                AdventureHelper.miniMessage().deserialize(""),
                AdventureHelper.miniMessage().deserialize(i18n.raw("rush.game_selection_rush_click"))));

        return new ItemWithAction(item, (element, click) -> {
            click.cancel();
            final Player player = (Player) click.clicker().platformPlayer();
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                player.closeInventory();
                Main.getInstance().getGameManager().openGameList(player);
            });
        });
    }
}
