package io.github.rush.guis;

import io.github.rush.utils.i18n;
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
        final GUI gui = new GUI(i18n.txt("rush.config_gui_title"), 3);

        gui.addItem(10, islandTypeItem(builder), p -> {
            final GameRoom.IslandType next = builder.islandType() == GameRoom.IslandType.FOUR_ISLANDS
                    ? GameRoom.IslandType.EIGHT_ISLANDS
                    : GameRoom.IslandType.FOUR_ISLANDS;

            builder.islandType(next);
            open(p, builder, manager);
        });

        gui.addItem(13, formatItem(builder), p -> {
            cycleFormat(builder);
            open(p, builder, manager);
        });

        gui.addItem(16, mapTypeItem(builder), p -> {
            builder.cycleMapType();
            open(p, builder, manager);
        });

        // Row 2: boolean flags
        gui.addItem(19, flagItem(
                i18n.txt("rush.config_flag_extra_hearts"),
                builder.extraHearts(),
                i18n.txt("rush.config_flag_desc_extra_hearts")), p -> {
                    builder.extraHearts(!builder.extraHearts());
                    open(p, builder, manager);
                });

        gui.addItem(21, flagItem(
                i18n.txt("rush.config_flag_overtime_start"),
                builder.overtimeStart(),
                i18n.txt("rush.config_flag_desc_overtime_start")), p -> {
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

    private static void cycleFormat(GameRoomConfig.Builder b) {
        int players = b.teamSize().getPlayersPerTeam();
        int teams = b.maxTeams();

        teams++;
        if (teams > b.islandType().getCount()) {
            teams = 2;
            players++;
            if (players > 4) {
                players = 1;
            }
        }

        b.maxTeams(teams - b.maxTeams());
        b.teamSize(GameRoom.TeamSize.values()[players - 1]);
    }

    private static String formatDisplayName(int teams, int playersPerTeam) {
        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < teams; i++) {
            if (i > 0)
                sb.append("vs");
            sb.append(playersPerTeam);
        }

        return sb.toString();
    }

    private static ItemStack islandTypeItem(GameRoomConfig.Builder b) {
        return labeled(b.islandType() == GameRoom.IslandType.FOUR_ISLANDS
                ? Material.GRASS_BLOCK
                : Material.STONE,
                i18n.txt("rush.config_island_type_name",
                        b.islandType().getDisplayName()),
                List.of(
                        i18n.txt("rush.config_island_type_click"),
                        Component.empty(),
                        i18n.txt("rush.config_island_type_current", b.islandType().getDisplayName())));
    }

    private static ItemStack formatItem(GameRoomConfig.Builder b) {
        final String displayName = formatDisplayName(b.maxTeams(), b.teamSize().getPlayersPerTeam());
        return labeled(Material.IRON_SWORD, i18n.txt("rush.config_format_name", displayName),
                List.of(
                        i18n.txt("rush.config_format_click"),
                        Component.empty(),
                        i18n.txt("rush.config_format_current", displayName)));
    }

    private static ItemStack mapTypeItem(GameRoomConfig.Builder b) {
        return labeled(Material.FILLED_MAP,
                i18n.txt("rush.config_map_type_name", b.mapType().name()),
                List.of(
                        i18n.txt("rush.config_map_type_click"),
                        Component.empty(),
                        i18n.txt("rush.config_map_type_current", b.mapType().name())));
    }

    private static ItemStack overtimeDurationItem(GameRoomConfig.Builder b) {
        return labeled(Material.CLOCK, i18n.txt("rush.config_overtime_dur_name", b.overtimeDuration()), List.of(
                i18n.txt("rush.config_overtime_dur_left"),
                i18n.txt("rush.config_overtime_dur_right"),
                i18n.txt("rush.config_overtime_dur_range"),
                Component.empty(),
                i18n.txt("rush.config_overtime_dur_current", b.overtimeDuration())));
    }

    private static ItemStack flagItem(Component label, boolean enabled, Component description) {
        return labeled(enabled ? Material.LIME_DYE : Material.GRAY_DYE, label, List.of(
                description,
                Component.empty(),
                i18n.txt("rush.config_flag_state",
                        i18n.raw(enabled ? "rush.config_flag_state_enabled" : "rush.config_flag_state_disabled")),
                i18n.txt("rush.config_flag_click_toggle")));
    }

    private static ItemStack confirmItem(GameRoomConfig.Builder b) {
        return labeled(Material.NETHER_STAR,
                i18n.txt("rush.config_confirm_name"),
                List.of(
                        i18n.txt("rush.config_confirm_lore_islands", b.islandType().getDisplayName()),
                        i18n.txt("rush.config_confirm_lore_format",
                                formatDisplayName(b.maxTeams(), b.teamSize().getPlayersPerTeam())),
                        i18n.txt("rush.config_confirm_lore_map", b.mapType().name()),
                        i18n.txt("rush.config_confirm_lore_extra_hearts",
                                i18n.raw(b.extraHearts() ? "rush.config_confirm_lore_extra_hearts_yes"
                                        : "rush.config_confirm_lore_extra_hearts_no")),
                        i18n.txt("rush.config_confirm_lore_overtime_start",
                                i18n.raw(b.overtimeStart() ? "rush.config_confirm_lore_overtime_start_yes"
                                        : "rush.config_confirm_lore_overtime_start_no")),
                        i18n.txt("rush.config_confirm_lore_overtime_dur", b.overtimeDuration()),
                        Component.empty(),
                        i18n.txt("rush.config_confirm_lore_click")));
    }

    private static ItemStack labeled(Material mat, Component name, List<Component> lore) {
        return ItemBuilder.of(mat).name(name).lore(lore.toArray(new Component[0])).build();
    }
}
