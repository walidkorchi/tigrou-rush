package io.github.rush.menus;

import io.github.rush.Main;
import io.github.rush.TranslationLoader;
import io.github.rush.game.Game;
import io.github.rush.game.GameManager;
import io.github.rush.game.GamePlayer;
import io.github.rush.game.GameRoom;
import io.github.rush.game.GameState;
import io.github.rush.game.TeamColor;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.BannerPatternKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TeamSelectionGUI {

    private static final class Constants {
        static String TEAM_SELECTION_BROWSER_TITLE = null;

        static void load() {
            final File configFile = new File(Main.getInstance().getCraftEngineDataFolder(), "config.yml");
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
            TEAM_SELECTION_BROWSER_TITLE = yaml.getString("gui.browser.team_selection.title");
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

    private static GuiElement createNavElement(boolean next) {
        final Key key = Key.of(next ? "tland:next_page" : "tland:previous_page");
        return GuiElement.paged(element -> {
            if (next ? element.gui().hasNextPage() : element.gui().hasPreviousPage()) {
                return itemManager().createCustomWrappedItem(key, null);
            }
            return itemManager().createCustomWrappedItem(Key.of("tland:empty_slot"), null);
        }, next);
    }

    private static GuiLayout createTeamLayout() {
        return new GuiLayout(
                "_________",
                "<AAAAAAA>",
                "_________")
                .addIngredient('A', Ingredient.paged())
                .addIngredient('_', createFillerElement())
                .addIngredient('<', createNavElement(false))
                .addIngredient('>', createNavElement(true));
    }

    private static ItemManager itemManager() {
        return Main.getInstance().getCraftEngineItemManager();
    }

    public static void openTeamSelection(Player player) {
        final Game game = getGameForPlayer(player);
        if (game == null || game.getState() != GameState.WAITING) {
            return;
        }

        if (Constants.TEAM_SELECTION_BROWSER_TITLE == null) {
            Constants.load();
        }

        final net.momirealms.craftengine.core.entity.player.Player craftPlayer = Main.getInstance()
                .adaptCraftPlayer(player);

        final List<ItemWithAction> icons = new ArrayList<>();
        for (TeamColor color : TeamColor.values()) {
            icons.add(createTeamIcon(color));
        }

        final TagResolver[] resolvers = PlayerOptionalContext.of(craftPlayer).tagResolvers();

        final Gui gui = PagedGui.builder()
                .addIngredients(icons)
                .layout(createTeamLayout())
                .inventoryClickConsumer(c -> {
                    String type = c.type();
                    if ("SHIFT_LEFT".equals(type) || "SHIFT_RIGHT".equals(type)
                            || "DOUBLE_CLICK".equals(type)) {
                        c.cancel();
                    }
                })
                .build()
                .title(AdventureHelper.miniMessage().deserialize(
                        Constants.TEAM_SELECTION_BROWSER_TITLE, resolvers))
                .refresh();

        gui.open(craftPlayer);
    }

    private static ItemWithAction createTeamIcon(TeamColor color) {
        final Key itemKey = Key.of("tland:team_color-" + color.name().toLowerCase());
        Item item = itemManager().createCustomWrappedItem(itemKey, null);
        if (item == null || item.isEmpty()) {
            item = itemManager().wrap(new ItemStack(Material.WHITE_WOOL));
        }

        if (item.platformItem() instanceof ItemStack itemStack) {
            final ItemMeta meta = itemStack.getItemMeta();
            meta.displayName(TranslationLoader.txt("rush.team_color_name", color.name()));
            itemStack.setItemMeta(meta);
            itemStack.setData(DataComponentTypes.LORE,
                    ItemLore.lore(List.of(TranslationLoader.txt("rush.chooseTeam"))));
        }

        return new ItemWithAction(item, (element, click) -> {
            click.cancel();
            final Player bukkitPlayer = (Player) click.clicker().platformPlayer();
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> selectTeam(bukkitPlayer, color));
        });
    }

    private static void selectTeam(Player player, TeamColor color) {
        final Game game = getGameForPlayer(player);
        if (game == null)
            return;

        game.joinTeam(new GamePlayer(player), color);

        player.getInventory().setItem(0, createSlimeballItem());
        player.getInventory().setItem(1, createReadyItem(true));
        player.getInventory().setHelmet(createTeamBanner(color));
        player.closeInventory();

        player.sendMessage(TranslationLoader.txt("rush.joinTeam", color.name()).color(color.getTextColor()));
    }

    public static ItemStack createBannerItem() {
        final ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        final BannerMeta meta = (BannerMeta) banner.getItemMeta();

        meta.displayName(TranslationLoader.txt("rush.bannerName"));
        meta.addItemFlags(ItemFlag.HIDE_DYE);
        banner.setItemMeta(meta);

        final ItemStack bannerCopy = banner.clone();
        bannerCopy.setAmount(1);

        return bannerCopy;
    }

    public static ItemStack createSlimeballItem() {
        final ItemStack slimeball = new ItemStack(Material.SLIME_BALL);
        final ItemMeta meta = slimeball.getItemMeta();

        meta.displayName(TranslationLoader.txt("rush.quit_team_confirm"));
        slimeball.setItemMeta(meta);
        slimeball.setData(DataComponentTypes.LORE,
                io.papermc.paper.datacomponent.item.ItemLore.lore(
                        List.of(TranslationLoader.txt("rush.quitLore"))));

        return slimeball;
    }

    public static ItemStack createReadyItem(boolean ready) {
        final Material dyeMaterial = ready ? Material.LIME_DYE : Material.RED_DYE;
        final ItemStack dye = new ItemStack(dyeMaterial);
        final ItemMeta meta = dye.getItemMeta();

        if (ready) {
            meta.displayName(TranslationLoader.txt("rush.ready"));
        } else {
            meta.displayName(TranslationLoader.txt("rush.notReady"));
        }

        dye.setItemMeta(meta);
        dye.setData(DataComponentTypes.LORE,
                io.papermc.paper.datacomponent.item.ItemLore.lore(
                        List.of(TranslationLoader.txt(ready ? "rush.notReadyLore" : "rush.readyLore"))));

        return dye;
    }

    private static Game getGameForPlayer(Player player) {
        return Main.getInstance().getGameManager().getGameForPlayer(player);
    }

    public static void openLeaveTeamMenu(Player player) {
        Game game = getGameForPlayer(player);
        if (game == null || game.getState() != GameState.WAITING)
            return;

        game.leaveTeam(new GamePlayer(player));

        player.getInventory().setItem(0, createBannerItem());
        player.getInventory().setItem(1, null);
        player.getInventory().setHelmet(null);

        player.sendMessage(Component.translatable("rush.quit_team"));
    }

    public static ItemStack createTeamBanner(TeamColor color) {
        final DyeColor dyeColor = color.getDyeColor();
        final Material bannerMaterial = Material.getMaterial(dyeColor.name() + "_BANNER");
        final ItemStack banner = new ItemStack(bannerMaterial);
        final BannerMeta meta = (BannerMeta) banner.getItemMeta();

        meta.displayName(Component.text(color.getTextColor() + "Équipe " + color.name()));

        final Registry<PatternType> bannerRegistry = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.BANNER_PATTERN);
        final PatternType snoutType = bannerRegistry.get(BannerPatternKeys.PIGLIN);

        if (snoutType != null) {
            meta.addPattern(new Pattern(DyeColor.WHITE, snoutType));
        }

        banner.setItemMeta(meta);

        return banner;
    }

    public static void toggleReady(Player player) {
        Game game = getGameForPlayer(player);
        if (game == null || game.getState() != GameState.WAITING)
            return;

        GamePlayer gp = new GamePlayer(player);
        final boolean currentlyReady = game.isPlayerReady(gp);

        game.setPlayerReady(gp, !currentlyReady);
        player.getInventory().setItem(1, createReadyItem(!currentlyReady));

        final TeamColor color = game.getPlayerTeam(gp).getColor();
        player.getInventory().setHelmet(createTeamBanner(color));

        GameManager gm = Main.getInstance().getGameManager();
        GameRoom barRoom = gm.getGameRoomByWorld(player.getWorld().getName());
        if (barRoom != null) {
            long readyCount = game.getPlayersReadyCount();
            int maxPlayers = barRoom.getMaxPlayers();
            NamedTextColor countColor = readyCount >= maxPlayers ? NamedTextColor.GREEN : NamedTextColor.RED;
            Component bar = Component.translatable("rush.ready_players",
                    Component.text(readyCount + "/" + maxPlayers).color(countColor));
            for (Player p : barRoom.getWorld().getPlayers()) {
                p.sendActionBar(bar);
            }
        }
    }
}
