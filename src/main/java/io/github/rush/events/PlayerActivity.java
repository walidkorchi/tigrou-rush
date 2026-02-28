package io.github.rush.events;

import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.game.Team;
import io.github.rush.menus.GUI;
import io.github.rush.menus.TeamSelectionGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerActivity implements Listener {

    private final Main plugin;

    public PlayerActivity(Main plugin) {
        this.plugin = plugin;
        startActionBarTask();
    }

    private void startActionBarTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.isGameStarted())
                return;

            sendActionBarToAll();
        }, 0L, 40L);
    }

    private void sendActionBarToAll() {
        String gameWorld = plugin.getGameWorld();
        if (gameWorld == null)
            return;

        int readyCount = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().getName().equals(gameWorld)) {
                if (plugin.getGameManager() != null
                        && plugin.getGameManager().getCurrentGame() != null
                        && plugin.getGameManager().getCurrentGame().isPlayerReady(player)) {
                    readyCount++;
                }
            }
        }

        NamedTextColor countColor = readyCount >= 4 ? NamedTextColor.GREEN : NamedTextColor.RED;
        TextComponent.Builder builder = Component.text()
                .content("Joueurs prêts (")
                .color(NamedTextColor.WHITE);
        builder.append(Component.text(readyCount + "/8").color(countColor));
        builder.append(Component.text(")").color(NamedTextColor.WHITE));

        Component message = builder.build();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().getName().equals(gameWorld)) {
                player.sendActionBar(message);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        player.getInventory().clear();
        player.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());
        plugin.getMusicManager().playForPlayer(player);

        if (player.getWorld().getName().equals(plugin.getGameWorld())) {
            sendActionBarToAll();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sendActionBarToAll();
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        String gameWorld = plugin.getGameWorld();
        if (event.getFrom().getName().equals(gameWorld) || event.getPlayer().getWorld().getName().equals(gameWorld)) {
            sendActionBarToAll();
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent pre) {
        if (!plugin.isGameStarted()) {
            return;
        }

        Player player = pre.getPlayer();
        player.getInventory().clear();
        player.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
        player.getInventory().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        player.getInventory().setItem(0, new ItemStack(Material.WOODEN_PICKAXE));
    }

    @EventHandler
    public void onPlayerDie(PlayerDeathEvent pd) {
        if (!plugin.isGameStarted()) {
            return;
        }

        Player player = pd.getEntity();
        Game game = Main.getInstance().getGameManager().getCurrentGame();

        pd.setDroppedExp(0);
        pd.deathMessage(null);
        player.getInventory().clear();

        Team team = game.getPlayerTeam(player);
        boolean bedDestroyed = team != null && team.isBedDestroyed();
        boolean isSpectator = bedDestroyed || game.isSpectator(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (isSpectator) {
                    game.addSpectator(player);
                } else {
                    player.spigot().respawn();
                    if (team != null) {
                        Location spawn = team.getSpawnLocation();
                        if (spawn != null) {
                            player.teleport(spawn);
                        }
                    }
                    game.addProtection(player);
                }
            }
        }.runTaskLater(Main.getInstance(), 20L);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!isPlayerInQueue(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent pie) {
        Player player = pie.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (plugin.isGameStarted()) {
            Game game = Main.getInstance().getGameManager().getCurrentGame();
            if (game != null && game.isSpectator(player)) {
                if (pie.getAction() == Action.RIGHT_CLICK_AIR || pie.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    if (item != null && item.getType() == Material.COMPASS) {
                        game.removeSpectator(player);
                        pie.setCancelled(true);
                        return;
                    }
                }
            }
        }

        if (pie.getAction() == Action.RIGHT_CLICK_AIR || pie.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (item != null && item.getType() == Material.WHITE_BANNER) {
                TeamSelectionGUI.openTeamSelection(player);
                pie.setCancelled(true);
                return;
            }

            if (item != null && item.getType() == Material.SLIME_BALL) {
                TeamSelectionGUI.openLeaveTeamMenu(player);
                pie.setCancelled(true);
                return;
            }

            if (item != null && (item.getType() == Material.LIME_DYE || item.getType() == Material.RED_DYE)) {
                TeamSelectionGUI.toggleReady(player);
                pie.setCancelled(true);
                return;
            }
        }

        if (plugin.isGameStarted()) {
            Block clickedBlock = pie.getClickedBlock();

            if (pie.getAction() == Action.PHYSICAL && clickedBlock != null) {
                if (clickedBlock.getType() == Material.WHEAT || clickedBlock.getType() == Material.FARMLAND) {
                    pie.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getInventory().getHolder() instanceof GUI gui) {
            event.setCancelled(true);
            gui.onClick(player, event.getRawSlot(), event.getClick());
            return;
        }

        if (isPlayerInQueue(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInvInteract(InventoryInteractEvent event) {
        if ((event.getWhoClicked() instanceof Player player)) {
            if (isPlayerInQueue(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isPlayerInQueue(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean isPlayerInQueue(Player player) {
        if (player.getWorld().getName().equals(plugin.getGameWorld())) {
            GameState gameState = GameState.WAITING;

            if (plugin.getGameManager() != null && plugin.getGameManager().getCurrentGame() != null) {
                gameState = plugin.getGameManager().getCurrentGame().getState();
            }

            if (gameState == GameState.WAITING) {
                return true;
            }
        }

        return false;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!plugin.isGameStarted()) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Game game = Main.getInstance().getGameManager().getCurrentGame();
        if (game != null && game.isProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.isGameStarted()) {
            return;
        }

        Player player = event.getPlayer();
        Game game = Main.getInstance().getGameManager().getCurrentGame();

        if (game != null && game.isProtected(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (from.getBlockX() != to.getBlockX() ||
                    from.getBlockY() != to.getBlockY() ||
                    from.getBlockZ() != to.getBlockZ()) {
                event.setTo(from);
            }
        }
    }
}
