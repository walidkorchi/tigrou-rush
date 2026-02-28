package io.github.rush.menus;

import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.game.Team;
import io.github.rush.game.TeamColor;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.BannerPatternKeys;
import net.kyori.adventure.text.Component;
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

    private static final String TITLE = "§8Choix d'équipe";

    public static void openTeamSelection(Player player) {
        final Game game = Main.getInstance().getGameManager().getCurrentGame();

        if (game.getState() == GameState.WAITING) {
            final GUI gui = new GUI(TITLE, 2);
            final List<TeamColor> availableColors = getAvailableTeams(game);

            for (int i = 0; i < Math.min(4, availableColors.size()); i++) {
                final TeamColor color = availableColors.get(i);
                final ItemStack wool = createWoolItem(color);
                final TeamColor selectedColor = color;

                gui.addItem(i, wool, p -> selectTeam(p, selectedColor));
            }

            gui.openGUI(player);
        }
    }

    private static List<TeamColor> getAvailableTeams(Game game) {
        List<TeamColor> ordered = List.of(TeamColor.YELLOW, TeamColor.RED, TeamColor.BLUE, TeamColor.GREEN);
        List<TeamColor> available = new ArrayList<>();

        for (TeamColor color : ordered) {
            Team team = game.getTeam(color.name());
            if (team == null || team.getPlayers().isEmpty()) {
                available.add(color);
            }
        }

        if (available.isEmpty()) {
            available.add(TeamColor.RED);
            available.add(TeamColor.BLUE);
            available.add(TeamColor.GREEN);
            available.add(TeamColor.YELLOW);
        }
        return available;
    }

    private static ItemStack createWoolItem(TeamColor color) {
        DyeColor dyeColor = color.getDyeColor();
        Material woolMaterial = Material.getMaterial(dyeColor.name() + "_WOOL");
        if (woolMaterial == null) {
            woolMaterial = Material.WHITE_WOOL;
        }

        ItemStack wool = new ItemStack(woolMaterial);
        ItemMeta meta = wool.getItemMeta();
        meta.displayName(Component.text(color.getTextColor() + color.name() + " Team"));
        wool.setItemMeta(meta);
        wool.setData(DataComponentTypes.LORE,
                ItemLore.lore(List.of(Component.text("§7Clic droit pour choisir une équipe"))));
        return wool;
    }

    private static void selectTeam(Player player, TeamColor color) {
        final Game game = Main.getInstance().getGameManager().getCurrentGame();

        game.getPlayerTeam(player);
        game.joinTeam(player, color);

        player.getInventory().setItem(0, createSlimeballItem());
        player.getInventory().setItem(1, createReadyItem(false));
        player.getInventory().setHelmet(createTeamBanner(color));
        player.closeInventory();

        player.sendMessage(Component.text("§aVous avez rejoint l'équipe " + color.name()).color(color.getTextColor()));
    }

    public static ItemStack createBannerItem() {
        final ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        final BannerMeta meta = (BannerMeta) banner.getItemMeta();

        meta.displayName(Component.text("§f§lChoix d'équipe"));
        meta.addItemFlags(ItemFlag.HIDE_DYE);
        banner.setItemMeta(meta);

        final ItemStack bannerCopy = banner.clone();

        bannerCopy.setAmount(1);

        return bannerCopy;
    }

    public static ItemStack createSlimeballItem() {
        final ItemStack slimeball = new ItemStack(Material.SLIME_BALL);
        final ItemMeta meta = slimeball.getItemMeta();

        meta.displayName(Component.text("§c§lQuitter l'équipe"));
        slimeball.setItemMeta(meta);
        slimeball.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(Component.text("§7Clic droit pour quitter"))));

        return slimeball;
    }

    public static ItemStack createReadyItem(boolean ready) {
        final Material dyeMaterial = ready ? Material.LIME_DYE : Material.RED_DYE;
        final ItemStack dye = new ItemStack(dyeMaterial);
        final ItemMeta meta = dye.getItemMeta();

        if (ready) {
            meta.displayName(Component.text("§a§lPrêt"));
        } else {
            meta.displayName(Component.text("§c§lPas prêt"));
        }

        dye.setItemMeta(meta);
        dye.setData(DataComponentTypes.LORE, ItemLore.lore(
                List.of(Component.text(ready ? "§7Clic droit pour annuler" : "§7Clic droit pour se déclarer prêt"))));

        return dye;
    }

    public static void openLeaveTeamMenu(Player player) {
        Game game = Main.getInstance().getGameManager().getCurrentGame();

        if (game.getState() == GameState.WAITING) {
            game.leaveTeam(player);

            player.getInventory().setItem(0, createBannerItem());
            player.getInventory().setItem(1, null);
            player.getInventory().setHelmet(null);

            player.sendMessage(Component.text("§cVous avez quitté votre équipe"));
        }
    }

    private static ItemStack createTeamBanner(TeamColor color) {
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
        Game game = Main.getInstance().getGameManager().getCurrentGame();

        if (game.getState() == GameState.WAITING) {
            final boolean currentlyReady = game.isPlayerReady(player);

            game.setPlayerReady(player, !currentlyReady);
            player.getInventory().setItem(1, createReadyItem(!currentlyReady));

            // team of player should no return null since the clicked
            // item only appears after selecting a team
            final TeamColor color = game.getPlayerTeam(player).getColor();

            player.getInventory().setHelmet(createTeamBanner(color));

            if (!currentlyReady) {
                player.sendMessage(Component.text("§aVous êtes prêt!"));
            } else {
                player.sendMessage(Component.text("§7Vous n'êtes plus prêt."));
            }
        }
    }
}
