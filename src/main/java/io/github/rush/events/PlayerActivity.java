package io.github.rush.events;

import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameState;
import io.github.rush.game.Team;
import io.github.rush.game.TeamColor;
import io.github.rush.menus.GUI;
import io.github.rush.menus.PlayerSettingsGUI;
import io.github.rush.menus.TeamSelectionGUI;
import io.github.rush.statistics.PlayerLevel;
import io.github.rush.statistics.PlayerLevelManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.GameMode;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

public class PlayerActivity implements Listener {

    private final Main plugin;

    private BukkitTask actionBarTask;

    // tolerance for floating-point imprecision in position tracking
    private static final double EPSILON = 0.05;

    public PlayerActivity(Main plugin) {
        this.plugin = plugin;
        startActionBarTask();
    }

    private void startActionBarTask() {
        actionBarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.isGameStarted()) {
                if (actionBarTask != null) {
                    actionBarTask.cancel();
                    actionBarTask = null;
                }
                return;
            }

            sendActionBarToAll();
        }, 0L, 40L);
    }

    private void sendActionBarToAll() {
        String gameWorld = plugin.getGameWorld();
        if (gameWorld == null)
            return;

        int readyCount = 0;

        if (plugin.getGameManager() != null && plugin.getGameManager().getCurrentGame() != null) {
            var game = plugin.getGameManager().getCurrentGame();
            readyCount = (int) game.getPlayersReadyCount();
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

        event.joinMessage(Component.text("§a[+] §f" + player.getName()));

        player.getInventory().clear();
        player.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());
        player.getInventory().setItem(8, createSettingsItem());

        if (plugin.getMusicManager() != null) {
            plugin.getMusicManager().playForPlayer(player);
        }

        if (player.getWorld().getName().equals(plugin.getGameWorld())) {
            sendActionBarToAll();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        if (!player.getWorld().getName().equals(plugin.getGameWorld())) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // sandstone/endstone are emancipated from island block protection logic
        if (blockType == Material.SANDSTONE || blockType == Material.END_STONE) {
            final Game game = Main.getInstance().getGameManager().getCurrentGame();

            if (game != null) {
                final Team breakerTeam = game.getPlayerTeam(player);

                // anti-spleef for same team players
                for (Player p : block.getWorld().getNearbyPlayers(block.getLocation(), 2)) {
                    if (p.equals(player) || !isStandingOn(p, block)) {
                        continue;
                    }

                    final Team pTeam = game.getPlayerTeam(p);

                    if (pTeam != null && breakerTeam.equals(pTeam)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            return;
        }

        event.setCancelled(true);
    }

    /**
     * Checks if a player is physically standing
     * on a given block bounding boxes only
     */
    private boolean isStandingOn(Player player, Block block) {
        BoundingBox playerBox = player.getBoundingBox();
        BoundingBox blockBox = block.getBoundingBox();

        // skip empty bounding box (no collision shape)
        if (blockBox.getVolume() == 0) {
            return false;
        }

        double feetY = playerBox.getMinY();
        double blockTopY = blockBox.getMaxY();

        if (Math.abs(feetY - blockTopY) > EPSILON) {
            return false;
        }

        // player's hitbox must overlap the block horizontally
        // (handles edge-standing on up to 4 blocks)
        return playerBox.getMinX() < blockBox.getMaxX()
                && playerBox.getMaxX() > blockBox.getMinX()
                && playerBox.getMinZ() < blockBox.getMaxZ()
                && playerBox.getMaxZ() > blockBox.getMinZ();
    }

    private static ItemStack createSettingsItem() {
        ItemStack repeater = new ItemStack(Material.REPEATER);
        ItemMeta meta = repeater.getItemMeta();
        meta.displayName(Component.text("§f§lParamètres"));
        repeater.setItemMeta(meta);
        return repeater;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.quitMessage(Component.text("§c[-] §f" + event.getPlayer().getName()));
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
    public void onPlayerDie(PlayerDeathEvent pd) {
        if (!plugin.isGameStarted()) {
            return;
        }

        Entity entity = pd.getEntity();
        Game game = Main.getInstance().getGameManager().getCurrentGame();

        if (game == null)
            return;

        pd.setCancelled(true);
        pd.setDroppedExp(0);
        pd.deathMessage(null);

        Player killer = null;
        if (entity instanceof Player player) {
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.setSaturation(20f);
            killer = player.getKiller();
        }

        game.onPlayerDeath(entity, killer);

        Team team = game.getPlayerTeam(entity);
        boolean bedDestroyed = team != null && team.isBedDestroyed();

        if (bedDestroyed) {
            if (entity instanceof Player player) {
                game.addSpectator(player);
            }
        } else if (team != null) {
            Location bedLoc = team.getBedLocation();
            Location spawn = bedLoc != null
                    ? new Location(bedLoc.getWorld(), bedLoc.getX() + 0.5, bedLoc.getY() + 1, bedLoc.getZ() + 0.5)
                    : team.getSpawnLocation();

            if (spawn != null) {
                entity.teleport(spawn);
            }

            if (entity instanceof Player player) {
                game.equipEntity(player, team);
                game.addProtection(player);
            }
        }
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
        if (pie.getHand() != EquipmentSlot.HAND) {
            return;
        }

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

            if (item != null && item.getType() == Material.REPEATER) {
                PlayerSettingsGUI.openPlayerSettings(player);
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

    @EventHandler
    public void onAsyncPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerLevelManager levelManager = Main.getInstance().getPlayerLevelManager();
        PlayerLevel playerLevel = levelManager.loadPlayerLevel(player.getUniqueId());

        int level = playerLevel.getLevel();
        String levelStr = String.valueOf(level);
        if (level < 10)
            levelStr = "0" + level;

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean isGlobal = message.startsWith("@");
        if (isGlobal) {
            message = message.substring(1).trim();
        }

        Component formatComponent;

        if (isPlayerInQueue(player)) {
            formatComponent = Component.text("§7[§e" + levelStr + "§7] [§9Lobby§7] §f")
                    .append(player.displayName())
                    .append(Component.text(" §f> "))
                    .append(Component.text(message));
        } else if (plugin.isGameStarted()) {
            Game game = Main.getInstance().getGameManager().getCurrentGame();
            Team team = game.getPlayerTeam(player);

            if (team != null) {
                TeamColor color = team.getColor();
                String teamColorCode = color.getTextColor().toString();

                if (isGlobal) {
                    formatComponent = Component
                            .text("§7[§e" + levelStr + "§7] [" + teamColorCode + color.name() + "§7] §f")
                            .append(player.displayName())
                            .append(Component.text(" §f> "))
                            .append(Component.text(message));
                } else {
                    formatComponent = Component
                            .text("§7[§e" + levelStr + "§7] [" + teamColorCode + color.name() + "§7] §f")
                            .append(player.displayName())
                            .append(Component.text(" §f> "))
                            .append(Component.text(message));

                    event.setCancelled(true);
                    for (Player recipient : plugin.getServer().getOnlinePlayers()) {
                        Team recipientTeam = game.getPlayerTeam(recipient);
                        if (recipientTeam != null && recipientTeam.equals(team)) {
                            recipient.sendMessage(formatComponent);
                        }
                    }
                    return;
                }
            } else {
                formatComponent = Component.text("§7[§e" + levelStr + "§7] §f")
                        .append(player.displayName())
                        .append(Component.text(" §f> "))
                        .append(Component.text(message));
            }
        } else {
            formatComponent = Component.text("§7[§e" + levelStr + "§7] §f")
                    .append(player.displayName())
                    .append(Component.text(" §f> "))
                    .append(Component.text(message));
        }

        event.renderer((source, sourceDisplayName, msg, viewer) -> formatComponent);
    }
}
