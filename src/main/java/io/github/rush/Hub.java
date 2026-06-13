package io.github.rush;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.rush.game.Game;
import io.github.rush.utils.ItemBuilder;
import io.github.rush.inventories.HubInventory;
import io.github.rush.utils.i18n;
import net.kyori.adventure.text.Component;

public final class Hub {

    private Hub() {
    }

    public static void resetPlayer(Player player) {
        for (Player online : player.getServer().getOnlinePlayers()) {
            online.showPlayer(Main.getInstance(), player);
            player.showPlayer(Main.getInstance(), online);
        }

        player.teleport(Main.getInstance().getMainLobby());
        player.setAllowFlight(false);
        player.setFallDistance(0);
        player.setGameMode(GameMode.ADVENTURE);

        Game.resetPlayerHealth(player);
        HubInventory.give(player);

        Main.getInstance().getTablistManager().updatePlayerListName(player);

        if (Main.getInstance().getPlayerLevelManager() != null)
            Main.getInstance().getPlayerLevelManager().applyXPBar(player);
    }

    public static boolean isAtHub(Player player) {
        return player.getWorld().getName().equals(Main.getInstance().getConfig().getString("lobbyWorld"));
    }

    public static boolean isUndroppableArmor(Material material) {
        return switch (material.getEquipmentSlot()) {
            case HEAD, LEGS, FEET -> true;
            default -> false;
        };
    }
}
