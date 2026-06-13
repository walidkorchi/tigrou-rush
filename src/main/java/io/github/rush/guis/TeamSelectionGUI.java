package io.github.rush.guis;

import io.github.rush.Main;
import io.github.rush.utils.ItemBuilder;
import io.github.rush.utils.i18n;
import io.github.rush.game.Game;
import io.github.rush.entities.GamePlayer;
import io.github.rush.game.GameRoom;
import io.github.rush.game.GameState;
import io.github.rush.abstracts.Team;
import io.github.rush.inventories.GamePlayerInventory;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.BannerPatternKeys;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.core.item.Item;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class TeamSelectionGUI {

    private static String title;

    private static GuiElement createNavElement(boolean next) {
        return GuiElement.paged(element -> {
            if (next ? element.gui().hasNextPage() : element.gui().hasPreviousPage())
                return GUI.itemManager()
                        .createCustomWrappedItem(Key.of(next ? "tland:next_page" : "tland:previous_page"), null);
            return GUI.itemManager().createCustomWrappedItem(Key.of("tland:empty_slot"), null);
        }, next);
    }

    private static GuiLayout createTeamLayout() {
        return new GuiLayout(
                "_________",
                "<AAAAAAA>",
                "_________")
                .addIngredient('A', Ingredient.paged())
                .addIngredient('_', GUI.fillerElement())
                .addIngredient('<', createNavElement(false))
                .addIngredient('>', createNavElement(true));
    }

    public static void openTeamSelection(Player player) {
        final Game game = getGameForPlayer(player);

        if (game == null || game.getState() != GameState.WAITING)
            return;

        if (title == null)
            title = GUI.loadCETitle("gui.browser.team_selection.title");

        final net.momirealms.craftengine.core.entity.player.Player craftPlayer = Main.getInstance()
                .adaptCraftPlayer(player);
        final List<ItemWithAction> icons = new ArrayList<>();

        for (Team.Color color : Team.Color.values())
            icons.add(createTeamIcon(color));

        PagedGui.builder()
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
                        title, PlayerOptionalContext.of(craftPlayer).tagResolvers()))
                .refresh()
                .open(craftPlayer);
    }

    private static ItemWithAction createTeamIcon(Team.Color color) {
        Item item = GUI.itemManager().createCustomWrappedItem(
                Key.of("tland:team_color-" + color.name().toLowerCase()), null);

        if (item == null || item.isEmpty())
            item = GUI.itemManager().wrap(new ItemStack(Material.WHITE_WOOL));

        if (item.platformItem() instanceof ItemStack itemStack) {
            final ItemMeta meta = itemStack.getItemMeta();

            meta.displayName(i18n.txt("rush.team_color_name", color.name()));
            itemStack.setItemMeta(meta);
        }

        return new ItemWithAction(item, (element, click) -> {
            click.cancel();

            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                selectTeam((Player) click.clicker().platformPlayer(), color);
            });
        });
    }

    private static void selectTeam(Player player, Team.Color color) {
        final Game game = getGameForPlayer(player);

        if (game == null)
            return;

        if (!game.joinTeam(new GamePlayer(player), color)) {
            player.sendMessage(Component.translatable("rush.team_full").color(color.getTextColor()));
            return;
        }

        player.getInventory().setItem(GamePlayerInventory.SLOT_LEAVE_TEAM, GamePlayerInventory.createSlimeballItem());
        player.getInventory().setItem(GamePlayerInventory.SLOT_READY_TOGGLER,
                GamePlayerInventory.createReadyItem(true));
        player.getInventory().setHelmet(createTeamBanner(color));
        player.closeInventory();

        Main.getInstance().getTablistManager().updatePlayerListName(player, color);

        player.sendMessage(i18n.txt("rush.joinTeam", color.name()).color(color.getTextColor()));
    }

    private static Game getGameForPlayer(Player player) {
        return Main.getInstance().getGameManager().getGameForPlayer(player);
    }

    public static void openLeaveTeamMenu(Player player) {
        Game game = getGameForPlayer(player);
        if (game == null || game.getState() != GameState.WAITING)
            return;

        game.leaveTeam(new GamePlayer(player));

        player.getInventory().setItem(GamePlayerInventory.SLOT_TEAM_SELECTION, GamePlayerInventory.createBannerItem());
        player.getInventory().setItem(GamePlayerInventory.SLOT_READY_TOGGLER, null);
        player.getInventory().setHelmet(null);

        Main.getInstance().getTablistManager().updatePlayerListName(player);

        player.sendMessage(Component.translatable("rush.quit_team"));
    }

    public static ItemStack createTeamBanner(Team.Color color) {
        final DyeColor dyeColor = color.getDyeColor();
        final Material bannerMaterial = Material.getMaterial(dyeColor.name() + "_BANNER");
        final ItemStack banner = new ItemStack(bannerMaterial);
        final BannerMeta meta = (BannerMeta) banner.getItemMeta();

        meta.displayName(Component.text(color.getTextColor() + "Équipe " + color.name()));

        final Registry<PatternType> bannerRegistry = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.BANNER_PATTERN);
        final PatternType snoutType = bannerRegistry.get(BannerPatternKeys.PIGLIN);

        if (snoutType != null) {
            meta.addPattern(new Pattern(color.getContrastDyeColor(), snoutType));
        }

        banner.setItemMeta(meta);

        return banner;
    }

    public static void toggleReady(Player player) {
        final Game game = getGameForPlayer(player);

        if (game == null || game.getState() != GameState.WAITING)
            return;

        final GamePlayer gp = new GamePlayer(player);
        final boolean currentlyReady = game.isPlayerReady(gp);

        game.setPlayerReady(gp, !currentlyReady);
        player.getInventory().setItem(GamePlayerInventory.SLOT_READY_TOGGLER,
                GamePlayerInventory.createReadyItem(!currentlyReady));

        final Team team = game.getPlayerTeam(gp);

        if (team != null)
            player.getInventory().setHelmet(createTeamBanner(team.getColor()));

        final GameRoom barRoom = Main.getInstance().getGameManager().getGameRoomByWorld(player.getWorld().getName());

        if (barRoom != null)
            GameRoom.sendReadyActionBar(barRoom);
    }
}
