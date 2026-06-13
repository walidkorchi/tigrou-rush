package io.github.rush.guis;

import io.github.rush.Main;
import io.github.rush.abstracts.Team;
import io.github.rush.entities.GameMannequin;
import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;
import io.github.rush.utils.i18n;
import io.github.rush.utils.ItemBuilder;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * CE-backed team-picker opened after the host names a new mannequin in
 * {@link AnvilInputGUI}. Shows each configured team as a wool block; full teams
 * are non-interactive. On selection the mannequin is spawned at the team's
 * spawn
 * point and added to the game.
 */
public final class HostMannequinTeamGUI {

    private HostMannequinTeamGUI() {
    }

    /**
     * Opens the team picker for {@code host}.
     *
     * @param host          the OP host player
     * @param mannequinName name chosen via the anvil step
     * @param room          the game room the mannequin will join
     * @param manager       the game manager (used to reopen
     *                      {@link HostPlayerListGUI})
     */
    public static void open(Player host, String mannequinName, GameRoom room, GameManager manager) {
        final List<Team> teams = new ArrayList<>(room.getGame().getTeams().values());
        final int rows = Math.max(1, (int) Math.ceil(teams.size() / 9.0));
        final GUI gui = new GUI(i18n.txt("rush.mannequin_team_gui_title"), rows);

        int slot = 0;
        for (Team team : teams) {
            final Team captured = team;
            final boolean full = team.getPlayers().size() >= team.getMaxPlayers();
            final ItemStack icon = buildTeamIcon(team, full);

            if (!full) {
                gui.addItem(slot, icon, p -> {
                    p.closeInventory();
                    spawnMannequin(p, mannequinName, captured.getColor(), room, manager);
                });
            } else {
                gui.addItem(slot, icon);
            }
            slot++;
        }

        gui.openGUI(host);
    }

    private static ItemStack buildTeamIcon(Team team, boolean full) {
        final Material wool = Material.valueOf(team.getColor().getDyeColor().name() + "_WOOL");
        final int current = team.getPlayers().size();
        final int max = team.getMaxPlayers();

        final List<Component> lore = new ArrayList<>();
        lore.add(i18n.txt("rush.mannequin_team_lore_slots", current, max));
        lore.add(full
                ? i18n.txt("rush.mannequin_team_lore_full")
                : i18n.txt("rush.mannequin_team_lore_join"));

        return ItemBuilder.of(wool)
                .name(i18n.txt("rush.team_color_name", team.getColor().name()))
                .lore(lore.toArray(new Component[0]))
                .build();
    }

    private static void spawnMannequin(
            Player host, String name, Team.Color color, GameRoom room, GameManager manager) {

        final Team team = room.getGame().getTeam(color.name());
        if (team == null)
            return;

        final var spawnLoc = team.getSpawnLocation() != null
                ? team.getSpawnLocation()
                : host.getLocation();

        final Mannequin mannequin = (Mannequin) spawnLoc.getWorld()
                .spawnEntity(spawnLoc, EntityType.MANNEQUIN);

        mannequin.customName(Component.text(name));
        mannequin.setDescription(null);
        mannequin.setCustomNameVisible(true);

        Bukkit.createProfile(name).update().thenAccept(updated -> {
            if (updated != null && updated.getTextures() != null
                    && updated.getTextures().getSkin() != null) {
                mannequin.setProfile(ResolvableProfile.resolvableProfile(updated));
            }
        });

        final GameMannequin gm = new GameMannequin(mannequin);
        if (room.getGame().joinTeam(gm, color)) {
            room.getGame().setPlayerReady(gm, true);
            mannequin.getEquipment().setItem(EquipmentSlot.HEAD,
                    TeamSelectionGUI.createTeamBanner(color));
            host.sendMessage(i18n.txt("rush.mannequin_created", name, color.name()));
        } else {
            mannequin.remove();
            host.sendMessage(i18n.txt("rush.mannequin_team_full_error"));
        }

        HostPlayerListGUI.open(host, room, manager);
    }
}
