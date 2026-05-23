package io.github.rush.menus;

import io.github.rush.game.GameRoom;
import io.github.rush.game.GameRoomConfig;
import io.github.rush.game.GameManager;
import io.github.rush.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class HostConfigGUI {

    private HostConfigGUI() {
    }

    public static void open(Player player, GameRoomConfig.Builder builder, GameManager manager) {
        GUI gui = new GUI(Component.translatable("rush.config_gui_title"), 3);

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
                Component.translatable("rush.config_flag_extra_hearts"),
                builder.extraHearts(),
                Component.translatable("rush.config_flag_desc_extra_hearts")), p -> {
                    builder.extraHearts(!builder.extraHearts());
                    open(p, builder, manager);
                });

        gui.addItem(21, flagItem(
                Component.translatable("rush.config_flag_overtime_start"),
                builder.overtimeStart(),
                Component.translatable("rush.config_flag_desc_overtime_start")), p -> {
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
        Component displayName = Component.translatable("rush.config_island_type_name",
                Component.text(b.islandType().getDisplayName()));
        return labeled(mat, displayName, List.of(
                Component.translatable("rush.config_island_type_click"),
                Component.empty(),
                Component.translatable("rush.config_island_type_current", Component.text(b.islandType().getDisplayName()))));
    }

    private static ItemStack maxTeamsItem(GameRoomConfig.Builder b) {
        Component displayName = Component.translatable("rush.config_max_teams_name",
                Component.text(b.maxTeams()));
        return labeled(Material.COMPARATOR, displayName, List.of(
                Component.translatable("rush.config_max_teams_left"),
                Component.translatable("rush.config_max_teams_right"),
                Component.translatable("rush.config_max_teams_range", Component.text(b.islandType().getCount()))));
    }

    private static ItemStack teamSizeItem(GameRoomConfig.Builder b) {
        Component displayName = Component.translatable("rush.config_team_size_name",
                Component.text(b.teamSize().getDisplayName()));
        return labeled(Material.IRON_HELMET, displayName, List.of(
                Component.translatable("rush.config_team_size_click"),
                Component.empty(),
                Component.translatable("rush.config_team_size_current", Component.text(b.teamSize().getDisplayName()))));
    }

    private static ItemStack mapTypeItem(GameRoomConfig.Builder b) {
        Component displayName = Component.translatable("rush.config_map_type_name",
                Component.text(b.mapType().name()));
        return labeled(Material.FILLED_MAP, displayName, List.of(
                Component.translatable("rush.config_map_type_click"),
                Component.empty(),
                Component.translatable("rush.config_map_type_current", Component.text(b.mapType().name()))));
    }

    private static ItemStack overtimeDurationItem(GameRoomConfig.Builder b) {
        Component displayName = Component.translatable("rush.config_overtime_dur_name",
                Component.text(b.overtimeDuration()));
        return labeled(Material.CLOCK, displayName, List.of(
                Component.translatable("rush.config_overtime_dur_left"),
                Component.translatable("rush.config_overtime_dur_right"),
                Component.translatable("rush.config_overtime_dur_range"),
                Component.empty(),
                Component.translatable("rush.config_overtime_dur_current", Component.text(b.overtimeDuration()))));
    }

    private static ItemStack flagItem(Component label, boolean enabled, Component description) {
        Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        Component state = Component.translatable(enabled ? "rush.config_flag_state_enabled" : "rush.config_flag_state_disabled");
        return labeled(mat, label, List.of(
                description,
                Component.empty(),
                Component.translatable("rush.config_flag_state", state),
                Component.translatable("rush.config_flag_click_toggle")));
    }

    private static ItemStack confirmItem(GameRoomConfig.Builder b) {
        Component extraHearts = Component.translatable(b.extraHearts()
                ? "rush.config_confirm_lore_extra_hearts_yes"
                : "rush.config_confirm_lore_extra_hearts_no");
        Component overtimeStart = Component.translatable(b.overtimeStart()
                ? "rush.config_confirm_lore_overtime_start_yes"
                : "rush.config_confirm_lore_overtime_start_no");
        return labeled(Material.NETHER_STAR,
                Component.translatable("rush.config_confirm_name"),
                List.of(
                        Component.translatable("rush.config_confirm_lore_islands", Component.text(b.islandType().getDisplayName())),
                        Component.translatable("rush.config_confirm_lore_teams",
                                Component.text(b.maxTeams()), Component.text(b.teamSize().getDisplayName())),
                        Component.translatable("rush.config_confirm_lore_map", Component.text(b.mapType().name())),
                        Component.translatable("rush.config_confirm_lore_extra_hearts", extraHearts),
                        Component.translatable("rush.config_confirm_lore_overtime_start", overtimeStart),
                        Component.translatable("rush.config_confirm_lore_overtime_dur", Component.text(b.overtimeDuration())),
                        Component.empty(),
                        Component.translatable("rush.config_confirm_lore_click")));
    }

    private static ItemStack labeled(Material mat, Component name, List<Component> lore) {
        return ItemBuilder.of(mat).name(name).lore(lore.toArray(new Component[0])).build();
    }
}
