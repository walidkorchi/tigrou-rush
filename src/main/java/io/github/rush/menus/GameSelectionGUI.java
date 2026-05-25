package io.github.rush.menus;

import io.github.rush.Main;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemManager;
import net.momirealms.craftengine.core.plugin.context.PlayerOptionalContext;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.plugin.gui.ItemWithAction;
import net.momirealms.craftengine.core.plugin.gui.PagedGui;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Key;
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

    private static final class Constants {
        static String TITLE = null;

        static void load() {
            final File configFile = new File(Main.getInstance().getCraftEngineDataFolder(), "config.yml");
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
            TITLE = yaml.getString("gui.browser.game_selection_browser.title");
        }
    }

    private static GuiElement emptySlotItem() {
        final Item item = itemManager()
                .createCustomWrappedItem(Key.of("tland:empty_slot"), null);
        if (item.platformItem() instanceof ItemStack itemStack) {
            itemStack.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                    TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        }
        return GuiElement.constant(item,
                (element, click) -> click.cancel());
    }

    private static GuiLayout createLayout() {
        return new GuiLayout(
                "_________",
                "__AAAAA__",
                "__AAAAA__",
                "_________")
                .addIngredient('A', Ingredient.paged())
                .addIngredient('_', emptySlotItem());
    }

    private static ItemManager itemManager() {
        return Main.getInstance().getCraftEngineItemManager();
    }

    public static void open(Player player) {
        if (Constants.TITLE == null) {
            Constants.load();
        }

        final net.momirealms.craftengine.core.entity.player.Player craftPlayer = Main.getInstance()
                .adaptCraftPlayer(player);
        final TagResolver[] resolvers = PlayerOptionalContext.of(craftPlayer).tagResolvers();

        final Gui gui = PagedGui.builder()
                .addIngredients(List.of(createRushModeIcon()))
                .layout(createLayout())
                .inventoryClickConsumer(c -> {
                    String type = c.type();
                    if ("SHIFT_LEFT".equals(type) || "SHIFT_RIGHT".equals(type)
                            || "DOUBLE_CLICK".equals(type)) {
                        c.cancel();
                    }
                })
                .build()
                .title(AdventureHelper.miniMessage().deserialize(Constants.TITLE, resolvers))
                .refresh();

        gui.open(craftPlayer);

        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            player.sendMessage("§e[DEBUG] Layout: height=" + createLayout().height()
                    + " width=" + createLayout().width()
                    + " → expected size=" + (createLayout().height() * createLayout().width()));
            org.bukkit.inventory.Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null) {
                player.sendMessage("§e[DEBUG] Actual Bukkit inventory size: " + top.getSize()
                        + " (" + (top.getSize() / 9) + " rows)");
            }
        });
    }

    private static ItemWithAction createRushModeIcon() {
        final Item item = itemManager().wrap(new ItemStack(Material.RED_BED));

        item.customNameComponent(AdventureHelper.miniMessage().deserialize("<red><bold>Rush"));
        item.loreComponent(List.of(
                AdventureHelper.miniMessage().deserialize("<gray>Mode BedWars / Rush"),
                AdventureHelper.miniMessage().deserialize("<gray>4 équipes · îles séparées"),
                AdventureHelper.miniMessage().deserialize(""),
                AdventureHelper.miniMessage().deserialize("<yellow>Clic pour rejoindre")));

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
