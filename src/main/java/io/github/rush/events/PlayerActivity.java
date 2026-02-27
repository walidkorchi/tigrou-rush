package io.github.rush.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.game.Team;
import io.github.rush.utils.ChatWriter;

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

        int playerCount = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().getName().equals(gameWorld)) {
                playerCount++;
            }
        }

        NamedTextColor countColor = playerCount >= 4 ? NamedTextColor.GREEN : NamedTextColor.RED;
        TextComponent.Builder builder = Component.text()
                .content("Joueurs à la file d'attente (")
                .color(NamedTextColor.WHITE);
        builder.append(Component.text(playerCount + "/8").color(countColor));
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
        player.getInventory().setItem(0, new ItemStack(Material.WHITE_BANNER, 1));
        player.getInventory().setItem(8, new ItemStack(Material.REPEATER, 1));

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
        Player p = pre.getPlayer();
        Game game = Main.getInstance().getGameManager().getGameOfPlayer(p);

        if (game == null) {
            return;
        }

        if (game.getState() == GameState.RUNNING) {
            game.getCycle().onPlayerRespawn(pre, p);
            return;
        }

        if (game.getState() == GameState.WAITING) {
            pre.setRespawnLocation(game.getLobby());
        }
    }

    @EventHandler
    public void onPlayerDie(PlayerDeathEvent pd) {
        final Player player = pd.getEntity();
        Game game = Main.getInstance().getGameManager().getGameOfPlayer(player);

        if (game != null) {

            if (game.getState() == GameState.RUNNING) {
                pd.setDroppedExp(0);
                pd.setDeathMessage(null);

                new BukkitRunnable() {

                    @Override
                    public void run() {
                        player.spigot().respawn();
                    }
                }.runTaskLater(Main.getInstance(), 20L);

                Player killer = player.getKiller();

                if (killer == null) {
                    killer = game.getPlayerDamager(player);
                }

                game.getCycle().onPlayerDies(player, killer);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void onIngameInventoryClick(InventoryClickEvent ice, Player player, Game game) {
        if (!ice.getInventory().getName().equals(Main._l(player, "ingame.shop.name"))) {
            if (game.isSpectator(player)
                    || (game.getCycle() instanceof BungeeGameCycle && game.getCycle().isEndGameRunning()
                            && Main.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true))) {

                ItemStack clickedStack = ice.getCurrentItem();
                if (clickedStack == null) {
                    return;
                }

                if (ice.getInventory().getName().equals(Main._l(player, "ingame.spectator"))) {
                    ice.setCancelled(true);
                    if (!clickedStack.getType().equals(Material.SKULL_ITEM)) {
                        return;
                    }

                    SkullMeta meta = (SkullMeta) clickedStack.getItemMeta();
                    Player pl = Main.getInstance().getServer().getPlayer(meta.getOwner());
                    if (pl == null) {
                        return;
                    }

                    if (!game.isInGame(pl)) {
                        return;
                    }

                    player.teleport(pl);
                    player.closeInventory();
                    return;
                }

                Material clickedMat = ice.getCurrentItem().getType();
                if (clickedMat.equals(Material.SLIME_BALL)) {
                    game.playerLeave(player, false);
                }

                if (clickedMat.equals(Material.COMPASS)) {
                    game.openSpectatorCompass(player);
                }
            }
            return;
        }

        ice.setCancelled(true);
        ItemStack clickedStack = ice.getCurrentItem();

        if (clickedStack == null) {
            return;
        }

        if (game.getPlayerSettings(player).useOldShop()) {
            try {
                if (clickedStack.getType() == Material.SNOW_BALL) {
                    game.getPlayerSettings(player).setUseOldShop(false);

                    // open new shop
                    NewItemShop itemShop = game.openNewItemShop(player);
                    itemShop.setCurrentCategory(null);
                    itemShop.openCategoryInventory(player);
                    return;
                }

                MerchantCategory cat = game.getItemShopCategories().get(clickedStack.getType());
                if (cat == null) {
                    return;
                }

                Class clazz = Class.forName("io.github.Main.com."
                        + Main.getInstance().getCurrentVersion().toLowerCase() + ".VillagerItemShop");
                Object villagerItemShop = clazz.getDeclaredConstructor(Game.class, Player.class, MerchantCategory.class)
                        .newInstance(game, player, cat);

                Method openTrade = clazz.getDeclaredMethod("openTrading", new Class[] {});
                openTrade.invoke(villagerItemShop, new Object[] {});
            } catch (Exception ex) {
                Main.getInstance().getBugsnag().notify(ex);
                ex.printStackTrace();
            }
        } else {
            game.getNewItemShop(player).handleInventoryClick(ice, game, player);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent pie) {

        Player player = pie.getPlayer();
        Game g = Main.getInstance().getGameManager().getGameOfPlayer(player);

        if (g == null) {
            if (pie.getAction() != Action.RIGHT_CLICK_BLOCK
                    && pie.getAction() != Action.RIGHT_CLICK_AIR) {
                return;
            }

            Block clicked = pie.getClickedBlock();

            if (clicked == null) {
                return;
            }

            if (!(clicked.getState() instanceof Sign)) {
                return;
            }

            Game game = Main.getInstance().getGameManager()
                    .getGameBySignLocation(clicked.getLocation());
            if (game == null) {
                return;
            }

            if (game.playerJoins(player)) {
                player.sendMessage(
                        ChatWriter.pluginMessage(ChatColor.GREEN + Main._l(player, "success.joined")));
            }
            return;
        }

        if (g.getState() == GameState.STOPPED) {
            return;
        }

        Material interactingMaterial = pie.getMaterial();
        Block clickedBlock = pie.getClickedBlock();

        if (g.getState() == GameState.RUNNING) {
            if (pie.getAction() == Action.PHYSICAL && clickedBlock != null
                    && (clickedBlock.getType() == Material.WHEAT
                            || clickedBlock.getType() == Material.SOIL)) {
                pie.setCancelled(true);
                return;
            }

            if (pie.getAction() != Action.RIGHT_CLICK_BLOCK
                    && pie.getAction() != Action.RIGHT_CLICK_AIR) {
                return;
            }

            if (clickedBlock != null && clickedBlock.getType() == Material.LEVER && !g.isSpectator(player)
                    && pie.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (!g.getRegion().isPlacedUnbreakableBlock(clickedBlock)) {
                    g.getRegion().addPlacedUnbreakableBlock(clickedBlock, clickedBlock.getState());
                }
                return;
            }

            if (g.isSpectator(player)
                    || (g.getCycle() instanceof BungeeGameCycle && g.getCycle().isEndGameRunning()
                            && Main.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true))) {
                if (interactingMaterial == Material.SLIME_BALL) {
                    g.playerLeave(player, false);
                    return;
                }

                if (interactingMaterial == Material.COMPASS) {
                    g.openSpectatorCompass(player);
                    pie.setCancelled(true);
                    return;
                }
            }

            // Spectators want to block
            if (clickedBlock != null) {
                try {
                    GameMode.valueOf("SPECTATOR");
                } catch (Exception ex) {
                    Main.getInstance().getBugsnag().notify(ex);
                    for (Player p : g.getFreePlayers()) {
                        if (!g.getRegion().isInRegion(p.getLocation())) {
                            continue;
                        }

                        if (pie.getClickedBlock().getLocation().distance(p.getLocation()) < 2) {
                            Location oldLocation = p.getLocation();

                            if (oldLocation.getY() >= pie.getClickedBlock().getLocation().getY()) {
                                oldLocation.setY(oldLocation.getY() + 2);
                            } else {
                                oldLocation.setY(oldLocation.getY() - 2);
                            }

                            p.teleport(oldLocation);
                        }
                    }
                }
            }

            if (clickedBlock != null && clickedBlock.getType() == Material.ENDER_CHEST
                    && !g.isSpectator(player)) {
                pie.setCancelled(true);

                Block chest = pie.getClickedBlock();
                Team chestTeam = g.getTeamOfEnderChest(chest);
                Team playerTeam = g.getPlayerTeam(player);

                if (chestTeam == null) {
                    return;
                }

                if (chestTeam.equals(playerTeam)) {
                    player.openInventory(chestTeam.getInventory());
                } else {
                    player.sendMessage(
                            ChatWriter
                                    .pluginMessage(ChatColor.RED + Main._l(player, "ingame.noturteamchest")));
                }

                return;
            }

            return;
        } else if (g.getState() == GameState.WAITING) {
            if (interactingMaterial == null) {
                pie.setCancelled(true);
                return;
            }

            if (pie.getAction() == Action.PHYSICAL) {
                if (clickedBlock != null && (clickedBlock.getType() == Material.WHEAT
                        || clickedBlock.getType() == Material.SOIL)) {
                    pie.setCancelled(true);
                    return;
                }
            }

            if (pie.getAction() != Action.RIGHT_CLICK_BLOCK
                    && pie.getAction() != Action.RIGHT_CLICK_AIR) {
                return;
            }

            switch (interactingMaterial) {
                case BED:
                    pie.setCancelled(true);
                    if (!g.isAutobalanceEnabled()) {
                        g.getPlayerStorage(player).openTeamSelection(g);
                    }

                    break;
                case DIAMOND:
                    pie.setCancelled(true);
                    if (player.isOp() || player.hasPermission("bw.setup")) {
                        g.start(player);
                    } else if (player.hasPermission("bw.vip.forcestart")) {
                        if (g.isStartable()) {
                            g.start(player);
                        } else {
                            if (!g.hasEnoughPlayers()) {
                                player.sendMessage(ChatWriter.pluginMessage(
                                        ChatColor.RED + Main._l(player, "lobby.cancelstart.not_enough_players")));
                            } else if (!g.hasEnoughTeams()) {
                                player.sendMessage(ChatWriter
                                        .pluginMessage(
                                                ChatColor.RED + Main
                                                        ._l(player, "lobby.cancelstart.not_enough_teams")));
                            }
                        }
                    }
                    break;
                case EMERALD:
                    pie.setCancelled(true);
                    if ((player.isOp() || player.hasPermission("bw.setup")
                            || player.hasPermission("bw.vip.reducecountdown"))
                            && g.getGameLobbyCountdown().getCounter() > g.getGameLobbyCountdown()
                                    .getLobbytimeWhenFull()) {
                        g.getGameLobbyCountdown().setCounter(g.getGameLobbyCountdown().getLobbytimeWhenFull());
                    }
                    break;
                case SLIME_BALL:
                    pie.setCancelled(true);
                    g.playerLeave(player, false);
                    break;
                case LEATHER_CHESTPLATE:
                    pie.setCancelled(true);
                    player.updateInventory();
                    break;
                default:
                    break;
            }
        }
    }
}
