package io.github.rush.guis;

import io.github.rush.entities.GameCombatant;
import io.github.rush.entities.GameMannequin;
import io.github.rush.entities.GamePlayer;
import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;
import io.github.rush.utils.i18n;
import io.github.rush.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public final class HostPlayerListGUI {

    private HostPlayerListGUI() {
        /**
         * Secondary host GUI listing all combatants (real players and mannequins) in
         * the waiting room. Clicking a mannequin entry immediately despawns it;
         * clicking a player entry opens a 1-row confirmation sub-GUI before kicking.
         */
    }

    public static void open(Player host, GameRoom room, GameManager manager) {
        final List<GameCombatant> combatants = room.getGame().getPlayers().stream()
                .filter(c -> !(c instanceof GamePlayer gp) || !gp.player().equals(host))
                .toList();

        final int combatantRows = (int) Math.ceil(combatants.size() / 9.0);
        final int rows = combatantRows + 1; // bottom action row
        final GUI gui = new GUI(i18n.txt("rush.host_panel_manage_players"), rows);

        int slot = 0;
        for (GameCombatant combatant : combatants) {
            final ItemStack icon = buildIcon(combatant);

            if (combatant instanceof GameMannequin gm) {
                gui.addItem(slot, icon, p -> {
                    p.closeInventory();
                    room.getGame().removePlayer(gm);
                    gm.remove();
                    open(p, room, manager);
                });
            } else {
                gui.addItem(slot, icon,
                        null,
                        p -> openRemoveConfirmation(p, combatant, room, manager));
            }
            slot++;
        }

        // "Add Mannequin" button — centre of the bottom action row
        final boolean atCapacity = room.getGame().getTotalPlayerCount() >= room.getGame().getMaxPlayers();
        final ItemStack addButton = ItemBuilder.of(atCapacity ? Material.GRAY_DYE : Material.ZOMBIE_SPAWN_EGG)
                .name(i18n.txt("rush.mannequin_create_button"))
                .lore(i18n.txt(atCapacity ? "rush.room_full" : "rush.mannequin_create_lore"))
                .build();

        if (atCapacity) {
            gui.addItem(combatantRows * 9 + 4, addButton);
        } else {
            gui.addItem(combatantRows * 9 + 4, addButton,
                    p -> AnvilInputGUI.open(p, "Bot", name -> HostMannequinTeamGUI.open(p, name, room, manager)));
        }

        gui.openGUI(host);
    }

    private static ItemStack buildIcon(GameCombatant combatant) {
        if (combatant instanceof GamePlayer gp) {
            return ItemBuilder.<SkullMeta>of(Material.PLAYER_HEAD)
                    .meta(m -> m.setOwningPlayer(gp.player()))
                    .name(i18n.txt("rush.host_panel_kick_name", gp.player().getName()))
                    .lore(i18n.txt("rush.host_panel_kick_lore1", gp.player().getName()),
                            i18n.txt("rush.host_panel_kick_lore2"))
                    .build();
        }

        // GameMannequin — use a zombie head as stand-in icon
        return ItemBuilder.of(Material.ZOMBIE_HEAD)
                .name(i18n.txt("rush.host_panel_kick_name", combatant.name()))
                .lore(
                        i18n.txt("rush.host_panel_kick_lore1", combatant.name()),
                        i18n.txt("rush.host_panel_kick_lore2"))
                .build();
    }

    private static void openRemoveConfirmation(
            Player host, GameCombatant combatant, GameRoom room, GameManager manager) {

        final ItemStack yes = ItemBuilder.of(Material.RED_WOOL)
                .name(i18n.txt("rush.host_panel_kick_confirm"))
                .build();
        final ItemStack no = ItemBuilder.of(Material.GREEN_WOOL)
                .name(i18n.txt("rush.host_panel_kick_cancel"))
                .build();

        ConfirmationGUI.of(buildIcon(combatant))
                .confirm(yes, p -> {
                    if (combatant instanceof GamePlayer gp) {
                        room.getGame().removePlayer(gp);
                        final Player target = gp.player();
                        manager.removePlayerFromGameRoom(target);
                        target.teleport(target.getServer().getWorlds().get(0).getSpawnLocation());
                        target.getInventory().clear();
                        target.sendMessage(Component.translatable("rush.room_kicked"));
                        p.sendMessage(Component.translatable("rush.player_kicked",
                                Component.text(target.getName())));
                    }
                    open(p, room, manager);
                })
                .cancel(no, p -> open(p, room, manager))
                .open(host);
    }
}
