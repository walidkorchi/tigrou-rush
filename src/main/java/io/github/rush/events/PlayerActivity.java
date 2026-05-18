package io.github.rush.events;

import io.github.rush.Main;
import io.github.rush.commands.AuthorCommand;
import io.github.rush.game.Game;
import io.github.rush.game.GameRoom;
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
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.GameMode;
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
        player.getInventory().setItem(0, Main.getInstance().getGameManager().createCompassItem());
        player.getInventory().setItem(8, createSettingsItem());

        // Teleport to main lobby
        Location mainLobby = Main.getInstance().getMainLobby();
        if (mainLobby != null && mainLobby.getWorld() != null) {
            player.teleport(mainLobby);
        }

        if (plugin.getMusicManager() != null
                && plugin.getPlayerSettingsManager().isMusicEnabled(player.getUniqueId())) {
            plugin.getMusicManager().playForPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        event.quitMessage(Component.text("§c[-] §f" + player.getName()));

        // Stop music if playing
        if (plugin.getMusicManager() != null) {
            plugin.getMusicManager().stopForPlayer(player);
        }

        // Remove from scoreboard
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().removeScoreboard(player);
        }

        if (plugin.getPlayerSettingsManager() != null) {
            plugin.getPlayerSettingsManager().removePlayer(player.getUniqueId());
        }

        // Remove from GameRoom
        if (plugin.getGameManager() != null) {
            // Host disconnect during CREATING: cancel the room
            plugin.getGameManager().getAllGameRooms().stream()
                    .filter(r -> r.getGame().getState() == GameState.CREATING
                            && player.getUniqueId().equals(r.getHostUUID()))
                    .findFirst()
                    .ifPresent(r -> plugin.getGameManager().cancelRoomCreation(r));

            GameRoom room = plugin.getGameManager().getGameRoomOfPlayer(player);
            if (room != null) {
                room.removePlayer(player);
                plugin.getGameManager().removePlayerFromGameRoom(player);

                // If room is now empty and waiting, remove it
                if (room.getPlayerCount() == 0 && room.isWaiting()) {
                    plugin.getGameManager().removeGameRoom(room.getId());
                }
            }

            // Remove from legacy game
            Game game = plugin.getGameManager().getGameOfPlayer(player);
            if (game != null) {
                // Remove from KillTracker
                game.getKillTracker().removePlayer(player.getUniqueId());

                // Remove player from game
                game.removePlayer(player);
                plugin.getGameManager().removePlayerFromGame(player);
            }
        }

        // Remove from playerBoards
        plugin.setFastBoard(player, null);
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

                // anti-spleef for same team players and mannequins
                for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation(), 2, 2, 2)) {
                    if (!(entity instanceof Player) && !(entity instanceof Mannequin)) {
                        continue;
                    }
                    if (entity.equals(player) || !isStandingOn(entity, block)) {
                        continue;
                    }

                    final Team entityTeam = game.getPlayerTeam(entity);

                    if (entityTeam != null && breakerTeam.equals(entityTeam)) {
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
     * Checks if an entity is physically standing
     * on a given block bounding boxes only
     */
    private boolean isStandingOn(Entity entity, Block block) {
        BoundingBox entityBox = entity.getBoundingBox();
        BoundingBox blockBox = block.getBoundingBox();

        // skip empty bounding box (no collision shape)
        if (blockBox.getVolume() == 0) {
            return false;
        }

        double feetY = entityBox.getMinY();
        double blockTopY = blockBox.getMaxY();

        if (Math.abs(feetY - blockTopY) > EPSILON) {
            return false;
        }

        // entity's hitbox must overlap the block horizontally
        // (handles edge-standing on up to 4 blocks)
        return entityBox.getMinX() < blockBox.getMaxX()
                && entityBox.getMaxX() > blockBox.getMinX()
                && entityBox.getMinZ() < blockBox.getMaxZ()
                && entityBox.getMaxZ() > blockBox.getMinZ();
    }

    private static ItemStack createSettingsItem() {
        final ItemStack repeater = new ItemStack(Material.REPEATER);
        final ItemMeta meta = repeater.getItemMeta();

        meta.displayName(Component.text("§f§lParamètres"));
        repeater.setItemMeta(meta);

        return repeater;
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        final Player player = event.getPlayer();

        if (isPlayerInQueue(player)) {
            event.setCancelled(true);
            return;
        }

        // prevents dropping armor during game
        if (isPlayerInGame(player)) {
            final ItemStack item = event.getItemDrop().getItemStack();

            if (isArmorItem(item.getType())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        final String gameWorld = plugin.getGameWorld();

        if (event.getFrom().getName().equals(gameWorld) || event.getPlayer().getWorld().getName().equals(gameWorld)) {
            sendActionBarToAll();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.isGameStarted()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(plugin.getGameWorld())) {
            return;
        }

        Game game = Main.getInstance().getGameManager().getCurrentGame();
        if (game == null || game.isSpectator(player)) {
            return;
        }

        double voidThreshold = Main.getISLAND_Y() - 60;
        if (player.getLocation().getY() < voidThreshold) {
            Team team = game.getPlayerTeam(player);
            if (team != null) {
                Location respawnLoc = team.getBedLocation() != null
                        ? new Location(team.getBedLocation().getWorld(), team.getBedLocation().getX() + 0.5,
                                team.getBedLocation().getY() + 1, team.getBedLocation().getZ() + 0.5)
                        : team.getSpawnLocation();

                if (respawnLoc != null) {
                    player.teleport(respawnLoc);
                    player.setFallDistance(0);
                }
            }
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

        handleEntityDeath(game, entity, killer);
    }

    @EventHandler
    public void onMannequinDie(EntityDeathEvent event) {
        if (!plugin.isGameStarted()) {
            return;
        }

        if (!(event.getEntity() instanceof Mannequin mannequin)) {
            return;
        }

        Game game = Main.getInstance().getGameManager().getCurrentGame();
        if (game == null || game.getPlayerTeam(mannequin) == null) {
            return;
        }

        event.setCancelled(true);
        event.setDroppedExp(0);
        event.getDrops().clear();

        mannequin.setHealth(mannequin.getAttribute(Attribute.MAX_HEALTH).getValue());

        Player killer = mannequin.getKiller();
        handleEntityDeath(game, mannequin, killer);
    }

    private void handleEntityDeath(Game game, Entity entity, Player killer) {
        game.onPlayerDeath(entity, killer);

        Team team = game.getPlayerTeam(entity);
        boolean bedDestroyed = team != null && team.isBedDestroyed();

        if (!bedDestroyed && team != null) {
            Location bedLoc = team.getBedLocation();
            Location spawn = bedLoc != null
                    ? new Location(bedLoc.getWorld(), bedLoc.getX() + 0.5, bedLoc.getY() + 1, bedLoc.getZ() + 0.5)
                    : team.getSpawnLocation();

            if (spawn != null) {
                entity.teleport(spawn);
            }

            game.equipEntity(entity, team);

            if (entity instanceof Player player) {
                game.addProtection(player);
            }
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isPlayerInQueue(player)) {
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
            if (item != null && item.getType() == Material.COMPASS) {
                // Check if player is in spectator mode during game
                if (plugin.isGameStarted()) {
                    Game game = Main.getInstance().getGameManager().getCurrentGame();
                    if (game != null && game.isSpectator(player)) {
                        game.removeSpectator(player);
                        pie.setCancelled(true);
                        return;
                    }
                }
                // Open game listing GUI
                Main.getInstance().getGameManager().openGameList(player);
                pie.setCancelled(true);
                return;
            }

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

            Block clickedBlock = pie.getClickedBlock();
            if (clickedBlock != null && isGlass(clickedBlock.getType())) {
                Location blockLoc = clickedBlock.getLocation();
                for (Location glassLoc : plugin.getCommandManager().getAuthorCommand().getGlassBlocks().keySet()) {
                    if (blockLoc.getWorld().equals(glassLoc.getWorld())
                            && blockLoc.getBlockX() == glassLoc.getBlockX()
                            && blockLoc.getBlockY() == glassLoc.getBlockY()
                            && blockLoc.getBlockZ() == glassLoc.getBlockZ()) {
                        AuthorCommand.playCookieFountain(blockLoc.add(0.5, 0.5, 0.5));
                        pie.setCancelled(true);
                        return;
                    }
                }
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
            return;
        }

        // Disable 2x2 crafting grid while in game
        if (isPlayerInGame(player)) {
            if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
                if (event.getRawSlot() >= 0 && event.getRawSlot() <= 4) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Prevent taking off armor during game
        if (isPlayerInGame(player)) {
            // Check if clicking on armor slots
            if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }
            // Check if shift-clicking armor into inventory (trying to unequip)
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem != null && isArmorItem(currentItem.getType())) {
                    event.setCancelled(true);
                    return;
                }
            }
            // Prevent dropping armor via click
            if (event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                    event.getAction() == InventoryAction.DROP_ALL_SLOT) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem != null && isArmorItem(currentItem.getType())) {
                    event.setCancelled(true);
                }
            }
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
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
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

    private boolean isPlayerInGame(Player player) {
        if (player.getWorld().getName().equals(plugin.getGameWorld())) {
            GameState gameState = GameState.WAITING;

            if (plugin.getGameManager() != null && plugin.getGameManager().getCurrentGame() != null) {
                gameState = plugin.getGameManager().getCurrentGame().getState();
            }

            return gameState == GameState.RUNNING;
        }

        return false;
    }

    private boolean isArmorItem(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") ||
                name.endsWith("_CHESTPLATE") ||
                name.endsWith("_LEGGINGS") ||
                name.endsWith("_BOOTS");
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
    public void onPlayerDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.isGameStarted()) {
            return;
        }

        Entity victim = event.getEntity();
        if (!(victim instanceof Player) && !(victim instanceof Mannequin)) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        Game game = Main.getInstance().getGameManager().getCurrentGame();
        if (game != null) {
            Team victimTeam = game.getPlayerTeam(victim);
            Team attackerTeam = game.getPlayerTeam(attacker);

            if (victimTeam != null && victimTeam.equals(attackerTeam)) {
                event.setCancelled(true);
                return;
            }

            if (victim instanceof Player playerVictim) {
                game.getKillTracker().recordDamage(playerVictim, attacker, event.getFinalDamage());
            }
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerLevelManager levelManager = Main.getInstance().getPlayerLevelManager();
        PlayerLevel playerLevel = levelManager.loadPlayerLevel(player.getUniqueId());

        String formattedLevel = playerLevel.getFormattedLevel();

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean isGlobal = message.startsWith("@");
        if (isGlobal) {
            message = message.substring(1).trim();
        }

        Component formatComponent;

        if (isPlayerInQueue(player)) {
            formatComponent = Component.text("§7[" + formattedLevel + "§7] [§9Lobby§7] §f")
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
                            .text("§7[" + formattedLevel + "§7] [" + teamColorCode + color.name() + "§7] §f")
                            .append(player.displayName())
                            .append(Component.text(" §f> "))
                            .append(Component.text(message));
                } else {
                    formatComponent = Component
                            .text("§7[" + formattedLevel + "§7] [" + teamColorCode + color.name() + "§7] §f")
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
                formatComponent = Component.text("§7[" + formattedLevel + "§7] §f")
                        .append(player.displayName())
                        .append(Component.text(" §f> "))
                        .append(Component.text(message));
            }
        } else {
            formatComponent = Component.text("§7[" + formattedLevel + "§7] §f")
                    .append(player.displayName())
                    .append(Component.text(" §f> "))
                    .append(Component.text(message));
        }

        event.renderer((source, sourceDisplayName, msg, viewer) -> formatComponent);
    }

    private boolean isGlass(Material material) {
        String name = material.name();
        return material == Material.GLASS
                || material == Material.TINTED_GLASS
                || name.endsWith("_STAINED_GLASS");
    }
}
