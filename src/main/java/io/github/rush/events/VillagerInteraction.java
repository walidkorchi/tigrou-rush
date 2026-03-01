package io.github.rush.events;

import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.menus.ShopGUI;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class VillagerInteraction implements Listener {

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        Player player = event.getPlayer();
        
        String gameWorld = Main.getInstance().getGameWorld();
        if (gameWorld == null || !player.getWorld().getName().equals(gameWorld)) {
            return;
        }

        Game game = Main.getInstance().getGameManager().getCurrentGame();
        if (game == null || game.getState() != GameState.RUNNING) {
            return;
        }

        if (!Main.getInstance().isMerchantVillager(villager)) {
            return;
        }

        event.setCancelled(true);

        if (Main.getInstance().isSpeedMerchantVillager(villager)) {
            ShopGUI.openMainMenu(player);
        } else {
            player.openMerchant(villager, true);
        }
    }
}
