package io.github.rush.menus;

import io.github.rush.game.GameRoom;
import io.github.rush.game.GameRoomConfig;
import io.github.rush.game.GameManager;
import io.github.rush.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class HostConfigGUI {

    private HostConfigGUI() {
    }

    private static final String TITLE = "§8Configurer la partie";

    public static void open(Player player, GameRoomConfig.Builder builder, GameManager manager) {
        GUI gui = new GUI(TITLE, 3);

        // Row 1: the 4 main settings
        gui.addItem(10, islandTypeItem(builder), p -> {
            GameRoom.IslandType next = builder.islandType() == GameRoom.IslandType.FOUR_ISLANDS
                    ? GameRoom.IslandType.EIGHT_ISLANDS
                    : GameRoom.IslandType.FOUR_ISLANDS;
            builder.islandType(next);
            open(p, builder, manager);
        });

        gui.addItem(12, maxTeamsItem(builder),
                p -> {
                    builder.maxTeams(-1);
                    open(p, builder, manager);
                },
                p -> {
                    builder.maxTeams(+1);
                    open(p, builder, manager);
                });

        gui.addItem(14, teamSizeItem(builder), p -> {
            GameRoom.TeamSize[] sizes = GameRoom.TeamSize.values();
            GameRoom.TeamSize next = sizes[(builder.teamSize().ordinal() + 1) % sizes.length];
            builder.teamSize(next);
            open(p, builder, manager);
        });

        gui.addItem(16, mapTypeItem(builder), p -> {
            builder.cycleMapType();
            open(p, builder, manager);
        });

        // Row 2: boolean flags
        gui.addItem(19, flagItem(
                "§aCoeurs supplémentaires",
                builder.extraHearts(),
                "§7Active un bonus de cœurs permanents"), p -> {
                    builder.extraHearts(!builder.extraHearts());
                    open(p, builder, manager);
                });

        gui.addItem(21, flagItem(
                "§6Mode overtime immédiat",
                builder.overtimeStart(),
                "§7Démarre directement en overtime"), p -> {
                    builder.overtimeStart(!builder.overtimeStart());
                    open(p, builder, manager);
                });

        gui.addItem(23, overtimeDurationItem(builder),
                p -> {
                    builder.overtimeDuration(-5);
                    open(p, builder, manager);
                },
                p -> {
                    builder.overtimeDuration(+5);
                    open(p, builder, manager);
                });

        // Row 2 right: confirm
        gui.addItem(25, confirmItem(builder), p -> {
            p.closeInventory();
            manager.removePendingConfig(p);
            manager.createGameRoom(p, builder.build());
        });

        gui.openGUI(player);
    }

    private static ItemStack islandTypeItem(GameRoomConfig.Builder b) {
        Material mat = b.islandType() == GameRoom.IslandType.FOUR_ISLANDS
                ? Material.GRASS_BLOCK
                : Material.STONE;
        return labeled(mat,
                "§e" + b.islandType().getDisplayName(),
                List.of("§7Clique pour changer", "§7", "§fActuel: §a" + b.islandType().getDisplayName()));
    }

    private static ItemStack maxTeamsItem(GameRoomConfig.Builder b) {
        return labeled(Material.COMPARATOR,
                "§eNombre d'équipes: §f" + b.maxTeams(),
                List.of("§7Clic gauche: §c-1", "§7Clic droit: §a+1",
                        "§7Min: 2 · Max: " + b.islandType().getCount()));
    }

    private static ItemStack teamSizeItem(GameRoomConfig.Builder b) {
        return labeled(Material.IRON_HELMET,
                "§e" + b.teamSize().getDisplayName(),
                List.of("§7Clique pour changer", "§7", "§fActuel: §a" + b.teamSize().getDisplayName()));
    }

    private static ItemStack mapTypeItem(GameRoomConfig.Builder b) {
        return labeled(Material.FILLED_MAP,
                "§eThème: §f" + b.mapType().name(),
                List.of("§7Clique pour changer", "§7", "§fActuel: §a" + b.mapType().name()));
    }

    private static ItemStack overtimeDurationItem(GameRoomConfig.Builder b) {
        return labeled(Material.CLOCK,
                "§6Délai avant overtime: §f" + b.overtimeDuration() + " min",
                List.of("§7Clic gauche: §c-5 min", "§7Clic droit: §a+5 min",
                        "§7Min: 5 min · Max: 120 min",
                        "§7", "§fActuel: §a" + b.overtimeDuration() + " min"));
    }

    private static ItemStack flagItem(String label, boolean enabled, String description) {
        Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        String state = enabled ? "§aActivé" : "§cDésactivé";
        return labeled(mat, label, List.of(description, "§7", "§fEtat: " + state, "§7Clique pour basculer"));
    }

    private static ItemStack confirmItem(GameRoomConfig.Builder b) {
        return labeled(Material.NETHER_STAR,
                "§a§lCréer la partie",
                List.of(
                        "§7Îles: §f" + b.islandType().getDisplayName(),
                        "§7Équipes: §f" + b.maxTeams() + " × " + b.teamSize().getDisplayName(),
                        "§7Thème: §f" + b.mapType().name(),
                        "§7Cœurs supp.: §f" + (b.extraHearts() ? "§aOui" : "§cNon"),
                        "§7Overtime immédiat: §f" + (b.overtimeStart() ? "§aOui" : "§cNon"),
                        "§7Délai overtime: §f" + b.overtimeDuration() + " min",
                        "§7",
                        "§aClic pour confirmer"));
    }

    private static ItemStack labeled(Material mat, String name, List<String> lore) {
        return ItemBuilder.of(mat).name(name).lore(lore).build();
    }
}
