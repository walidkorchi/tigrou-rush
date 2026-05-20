package io.github.rush.game;

import io.github.rush.Main;
import io.github.rush.menus.TeamSelectionGUI;
import io.github.rush.utils.ItemBuilder;
import io.github.rush.objects.Island;
import io.github.rush.replay.ReplayRecorder;
import io.github.rush.statistics.PlayerLevelManager;
import io.github.rush.statistics.PlayerStatistic;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class Game {

    @Setter
    @Getter
    private GameState state;
    @Getter
    private GameCycle cycle;
    private Location lobby;

    private final List<BukkitTask> runningTasks = new ArrayList<>();
    private final List<BukkitTask> spawnerTasks = new ArrayList<>();
    private final List<ResourceSpawner> resourceSpawners = new ArrayList<>();

    @Getter
    private final Map<String, Team> teams = new HashMap<>();
    @Getter
    private final List<Entity> freePlayers = new ArrayList<>();
    private final Map<Player, PlayerStatistic> playerStats = new HashMap<>();
    private final Set<Player> spectators = new HashSet<>();
    private final Set<Player> protectedPlayers = new HashSet<>();
    @Getter
    private final KillTracker killTracker = new KillTracker();

    @Getter
    private int gameTime = 0;

    @Getter
    private List<Team> islandAssignment;
    private List<Integer> islandSlotOrder;
    // Adjacent pair first (S+E) so 2-team forbidden zone covers the SE corridor between them.
    private static final int[] PREFERRED_ISLAND_ORDER = { 2, 1, 0, 3 };

    @Getter
    private int timeLeft = 0;
    @Getter
    private final int maxPlayers;
    private final int islandCount;
    @Getter
    private final int minPlayers = 2;
    @Getter
    private final int minTeams = 2;
    @Getter
    private final String worldName;

    private GameLobbyCountdown lobbyCountdown;
    private final Map<Entity, Boolean> playersReady = new HashMap<>();

    @Setter
    @Getter
    private GameRoom gameRoom = null;

    private ReplayRecorder recorder = null;

    /**
     * Legacy constructor for single-game mode.
     */
    public Game(String name) {
        this.state = GameState.WAITING;
        this.cycle = new GameCycle(this);
        this.worldName = Main.getInstance().getConfig().getString("gameWorld");
        this.maxPlayers = 8;
        this.islandCount = 4;

        String lobbyWorld = Main.getInstance().getConfig().getString("lobbyWorld");
        World world = Bukkit.getWorld(lobbyWorld);

        this.lobby = world != null ? world.getSpawnLocation() : null;

        initializeTeams(4, 4);
    }

    /**
     * Constructor for multi-game mode with configurable team sizes.
     * islandCount is always islandType.getCount() (4 for FOUR_ISLANDS).
     * maxTeams is the number of teams actually playing (host choice, 2–islandCount).
     */
    public Game(String name, String worldName, Location lobby, int islandCount, int maxTeams, int playersPerTeam) {
        this.state = GameState.WAITING;
        this.cycle = new GameCycle(this);
        this.worldName = worldName;
        this.lobby = lobby;
        this.maxPlayers = maxTeams * playersPerTeam;
        this.islandCount = islandCount;

        initializeTeams(maxTeams, playersPerTeam);
    }

    /**
     * Returns true if this game is running in a GameRoom (multi-game mode).
     */
    public boolean isGameRoomMode() {
        return gameRoom != null;
    }

    private void initializeTeams(int maxTeams, int playersPerTeam) {
        List<TeamColor> colors = List.of(
                TeamColor.RED, TeamColor.BLUE, TeamColor.GREEN, TeamColor.YELLOW);

        int teamCount = Math.min(maxTeams, colors.size());
        for (int i = 0; i < teamCount; i++) {
            TeamColor color = colors.get(i);
            Team team = new Team(color.name(), color, playersPerTeam);
            teams.put(color.name(), team);
        }
    }

    public int getTotalPlayerCount() {
        return getPlayers().size();
    }

    public void removePlayer(Entity player) {
        freePlayers.remove(player);
        playersReady.remove(player);
        spectators.remove(player);

        for (Team team : teams.values()) {
            if (team.isInTeam(player)) {
                team.removePlayer(player);
                break;
            }
        }

        updatePlayerList();
    }

    public boolean joinTeam(Entity player, TeamColor color) {
        final Team team = teams.get(color.name());

        if (team == null) {
            return false;
        }

        for (Team existingTeam : teams.values()) {
            if (existingTeam.isInTeam(player)) {
                existingTeam.removePlayer(player);
                break;
            }
        }

        Boolean added = team.addPlayer(player);

        if (freePlayers.contains(player)) {
            freePlayers.remove(player);
        }

        playersReady.put(player, false);

        for (Entity existingPlayer : team.getPlayers()) {
            if (existingPlayer instanceof Player && !existingPlayer.equals(player)) {
                existingPlayer
                        .sendMessage(Component.text("§a➜ " + player.getName() + " a rejoint l'équipe " + color.name())
                                .color(color.getTextColor()));
            }
        }

        updatePlayerList();
        checkStartCondition();

        return added;
    }

    public void leaveTeam(Entity entity) {
        for (Team team : teams.values()) {
            if (team.isInTeam(entity)) {
                team.removePlayer(entity);
                break;
            }
        }

        if (!freePlayers.contains(entity)) {
            freePlayers.add(entity);
        }

        playersReady.put(entity, false);

        if (entity instanceof Player) {
            updatePlayerList();
        }
    }

    public boolean isPlayerReady(Entity entity) {
        Boolean ready = playersReady.get(entity);
        return ready != null && ready;
    }

    public void addSpectator(Player player) {
        spectators.add(player);
        Team team = getPlayerTeam(player);
        if (team != null) {
            team.removePlayer(player);
        }
        freePlayers.remove(player);
        playersReady.remove(player);

        applySpectatorMode(player);
        player.getInventory().setItem(0, ItemBuilder.of(Material.COMPASS).name("§cQuitter le spectator").build());
        hideFromGameWorld(player);

        if (lobby != null) {
            player.teleport(lobby);
        }

        player.sendMessage(Component.text("§cVotre lit a été détruit! Vous êtes maintenant spectateur."));
        updatePlayerList();
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player);
    }

    public Collection<Player> getSpectators() {
        return Collections.unmodifiableCollection(spectators);
    }

    public void removeSpectator(Player player) {
        spectators.remove(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);

        showToGameWorld(player);

        player.getInventory().clear();

        Location lobbyLoc = Main.getInstance().getMainLobby();
        if (lobbyLoc != null) {
            player.teleport(lobbyLoc);
        }

        player.sendMessage(Component.text("§aVous avez quitté le mode spectateur."));
    }

    public void addObserver(Player player) {
        spectators.add(player);

        applySpectatorMode(player);
        player.getInventory().setItem(0, ItemBuilder.of(Material.COMPASS).name("§cQuitter la partie").build());
        hideFromGameWorld(player);

        if (lobby != null) {
            player.teleport(lobby);
        }

        player.sendMessage(Component.text("§7Vous regardez cette partie en spectateur."));
        updatePlayerList();
    }

    public boolean isProtected(Player player) {
        return protectedPlayers.contains(player);
    }

    public void addProtection(Player player) {
        int protectionTime = Main.getInstance().getRespawnProtectionTime();

        if (protectionTime <= 0)
            return;

        if (isGameRoomMode() && recorder != null) {
            var loc = player.getLocation();
            recorder.recordRespawn(player.getUniqueId(), loc.getX(), loc.getY(), loc.getZ());
        }

        protectedPlayers.add(player);

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                Integer.MAX_VALUE,
                9,
                false,
                false));

        AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) {
            jumpAttr.addModifier(new AttributeModifier(
                    new NamespacedKey(Main.getInstance(), "no_jump"),
                    -jumpAttr.getBaseValue(),
                    AttributeModifier.Operation.ADD_NUMBER));
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld().equals(player.getWorld())) {
                online.hidePlayer(Main.getInstance(), player);
                player.hidePlayer(Main.getInstance(), online);
            }
        }

        final java.util.concurrent.atomic.AtomicInteger elapsed = new java.util.concurrent.atomic.AtomicInteger(0);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (!protectedPlayers.contains(player)) {
                return;
            }
            int remaining = protectionTime - elapsed.getAndIncrement();
            if (remaining > 0) {
                player.sendActionBar(Component.text("§aProtection: " + remaining + "s"));
            }
        }, 0, 20L);

        runningTasks.add(task);

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            removeProtection(player);
            player.sendActionBar(Component.text(""));
            player.sendMessage(Component.text("§aProtection terminée! Vous pouvez bouger."));
        }, protectionTime * 20L);
    }

    public void removeProtection(Player player) {
        protectedPlayers.remove(player);

        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) {
            jumpAttr.removeModifier(new NamespacedKey(Main.getInstance(), "no_jump"));
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld().equals(player.getWorld())) {
                online.showPlayer(Main.getInstance(), player);
                player.showPlayer(Main.getInstance(), online);
            }
        }
    }

    private void applySpectatorMode(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();
    }

    private void hideFromGameWorld(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hidePlayer(Main.getInstance(), player);
        }
    }

    private void showToGameWorld(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(Main.getInstance(), player);
        }
    }

    private void clearGameState() {
        freePlayers.clear();
        playersReady.clear();
        playerStats.clear();
        spectators.clear();
        protectedPlayers.clear();
        killTracker.reset();
    }

    private void resetPlayerHealth(Player player) {
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(NamespacedKey.minecraft("extra_hearts"));
        }
        player.setHealth(20.0);
    }

    public void setPlayerReady(Entity entity, boolean ready) {
        playersReady.put(entity, ready);
        checkStartCondition();
    }

    public void checkStartCondition() {
        if (state == GameState.WAITING) {
            if (areEnoughTeamsFull()) {
                if (lobbyCountdown == null) {
                    lobbyCountdown = new GameLobbyCountdown(this);
                    lobbyCountdown.start();
                }
            } else if (lobbyCountdown != null) {
                lobbyCountdown.cancel();
                lobbyCountdown = null;
                broadcastMessage("§cPas assez de joueurs prêts pour démarrer!");
            }

            updateActionBar();
        }
    }

    public boolean areEnoughTeamsFull() {
        int ppTeam = getPlayersPerTeam();
        long fullReadyTeams = teams.values().stream()
                .filter(t -> t.getPlayers().stream()
                        .filter(e -> Boolean.TRUE.equals(playersReady.get(e)))
                        .count() >= ppTeam)
                .count();
        return fullReadyTeams >= minTeams;
    }

    private int getPlayersPerTeam() {
        return teams.values().stream().findFirst().map(Team::getMaxPlayers).orElse(1);
    }

    public void forceStart() {
        if (state == GameState.WAITING) {
            if (lobbyCountdown != null) {
                lobbyCountdown.cancel();
            }
            lobbyCountdown = new GameLobbyCountdown(this);
            lobbyCountdown.setCounter(5);
            lobbyCountdown.broadcastCountdownMessage(5);
            lobbyCountdown.start();
        }
    }

    public void autoStart() {
        if (state == GameState.WAITING) {
            final List<Entity> unassigned = freePlayers.stream()
                    .filter(p -> teams.values().stream().noneMatch(t -> t.isInTeam(p)))
                    .collect(Collectors.toList());

            if (!unassigned.isEmpty()) {
                final List<Team> sortedTeams = teams.values().stream()
                        .sorted(Comparator.comparingInt(t -> t.getPlayers().size()))
                        .collect(Collectors.toList());

                for (Entity player : unassigned) {
                    Team smallestTeam = sortedTeams.get(0);

                    if (smallestTeam.getPlayers().size() < smallestTeam.getMaxPlayers()) {
                        smallestTeam.addPlayer(player);
                    }
                }

                checkStartCondition();
            }
        }
    }

    private int getOvertimeSeconds() {
        if (isGameRoomMode()) {
            return gameRoom.getConfig().overtimeDuration() * 60;
        }
        return Main.getInstance().getConfig().getInt("overtime-duration", 30) * 60;
    }

    public boolean isOvertime() {
        return gameTime >= getOvertimeSeconds();
    }

    public String getFormattedTime() {
        int minutes = gameTime / 60;
        int seconds = gameTime % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void incrementGameTime() {
        gameTime++;
        if (gameTime == getOvertimeSeconds()) {
            broadcastMessage("§c§lOVERTIME! §7Les restrictions de placement sont levées!");
            if (isGameRoomMode() && recorder != null) {
                recorder.recordPhaseChange("OVERTIME");
            }
        }
    }

    public boolean isBlockInForbiddenZone(Location location) {
        if (isOvertime() || islandAssignment == null) {
            return false;
        }

        List<Team> activeTeamList = islandAssignment.stream()
                .filter(t -> t != null && !t.getPlayers().isEmpty())
                .collect(Collectors.toList());

        if (activeTeamList.size() != 2) {
            return false;
        }

        Team teamA = activeTeamList.get(0);
        Team teamB = activeTeamList.get(1);

        if (teamA.getSpawnLocation() == null || teamB.getSpawnLocation() == null) {
            return false;
        }

        return ForbiddenZone.isBlocked(
                location.getX(), location.getZ(),
                teamA.getSpawnLocation().getX(), teamA.getSpawnLocation().getZ(),
                teamB.getSpawnLocation().getX(), teamB.getSpawnLocation().getZ(),
                islandCount);
    }

    public boolean isBlockInRingPath(Location location) {
        double bx = location.getX();
        double bz = location.getZ();

        List<Island> allIslands = getAllIslandPositions();
        if (allIslands.isEmpty()) return true;

        double cx = 0, cz = 0;
        for (Island island : allIslands) {
            cx += island.getX();
            cz += island.getZ();
        }
        cx /= allIslands.size();
        cz /= allIslands.size();

        double rx = bx - cx;
        double rz = bz - cz;

        // All island positions are always buildable regardless of team assignment.
        double islandRadiusSq = 15.0 * 15.0;
        for (Island island : allIslands) {
            double dx = bx - island.getX();
            double dz = bz - island.getZ();
            if (dx * dx + dz * dz <= islandRadiusSq) return true;
        }

        // Angular check: must be within ±22.5° of a diagonal direction (NE/SE/SW/NW)
        double angle = Math.atan2(rz, rx);
        double normalized = angle % (Math.PI / 2);
        if (normalized < 0) normalized += Math.PI / 2;
        if (normalized > Math.PI / 4) normalized = Math.PI / 2 - normalized;
        return normalized > Math.PI / 8;
    }

    private List<Island> getAllIslandPositions() {
        if (isGameRoomMode() && gameRoom != null) {
            return gameRoom.getIslands();
        }
        List<Island> pluginIslands = Main.getInstance().getIslands();
        return pluginIslands != null ? pluginIslands : List.of();
    }

    private void computeIslandAssignment() {
        // Stable team ordering by color enum ordinal (RED < BLUE < GREEN < YELLOW)
        List<Team> orderedTeams = teams.values().stream()
                .sorted(Comparator.comparingInt(t -> t.getColor().ordinal()))
                .collect(Collectors.toList());

        // Build slot order filtered to valid island indices for this island count.
        // PREFERRED_ISLAND_ORDER spreads teams: opposite pairs first (West/East, then North/South).
        islandSlotOrder = new ArrayList<>();
        for (int s : PREFERRED_ISLAND_ORDER) {
            if (s < islandCount) islandSlotOrder.add(s);
        }

        islandAssignment = new ArrayList<>(Collections.nCopies(islandCount, null));

        for (int i = 0; i < orderedTeams.size() && i < islandSlotOrder.size(); i++) {
            islandAssignment.set(islandSlotOrder.get(i), orderedTeams.get(i));
        }
    }

    public void start() {
        if (state == GameState.WAITING) {
            if (lobbyCountdown != null) {
                lobbyCountdown.cancel();
                lobbyCountdown = null;
            }
            state = GameState.RUNNING;
            gameTime = 0;

            if (isGameRoomMode() && gameRoom.getConfig().overtimeStart()) {
                gameTime = getOvertimeSeconds();
                broadcastMessage("§c§lOVERTIME! §7Les restrictions de placement sont levées!");
            }

            // Only set global game started flag for legacy mode
            if (!isGameRoomMode()) {
                Main.getInstance().setGameStarted(true);
            }

            computeIslandAssignment();
            loadIslandsAndSetSpawns();

            for (int i = 0; i < islandAssignment.size(); i++) {
                final Team team = islandAssignment.get(i);

                if (team != null) {
                    team.placeBed(i);
                }
            }

            for (Entity entity : getPlayers()) {
                final Team team = getPlayerTeam(entity);

                if (team != null) {
                    if (entity instanceof Player player) {
                        player.getInventory().clear();
                        player.getEnderChest().clear();
                        player.setGameMode(GameMode.SURVIVAL);
                    }

                    teleportToTeamSpawn(entity, team);
                    equipEntity(entity, team);
                }
            }

            startResourceSpawners();
            startCompassTracker();

            cycle.onGameStart();

            if (isGameRoomMode()) {
                recorder = new ReplayRecorder(this, worldName);
            }

            // Notify GameManager that game started (for GameRoom mode)
            if (isGameRoomMode()) {
                Main.getInstance().getGameManager().onGameRoomStarted(gameRoom);
            }
        }
    }

    private void loadIslandsAndSetSpawns() {
        World gameWorld;
        List<Island> islands;
        int islandY;

        if (isGameRoomMode()) {
            // GameRoom mode: use room's own world and islands
            gameWorld = gameRoom.getWorld();
            islands = gameRoom.getIslands();
            islandY = gameRoom.getIslandY();

            // Load islands if not already loaded
            if (!gameRoom.isIslandsLoaded()) {
                Main.getInstance().getGameManager().loadIslandsForGameRoom(gameRoom);
            }
        } else {
            // Legacy mode: use Main's world and islands
            Main plugin = Main.getInstance();
            gameWorld = Bukkit.getWorld(plugin.getGameWorld());

            if (gameWorld == null) {
                plugin.getLogger().warning("Game world not found, cannot load islands");
                return;
            }

            if (!plugin.isIslandsLoaded()) {
                plugin.loadSchematicsSync();
            }

            islands = plugin.getIslands();
            islandY = Main.getISLAND_Y();
        }

        for (int i = 0; i < islandAssignment.size() && i < islands.size(); i++) {
            final Team team = islandAssignment.get(i);

            if (team == null)
                continue;

            final Island island = islands.get(i);
            final Location spawnLoc = new Location(gameWorld, island.getX(), islandY + 2, island.getZ());

            team.setSpawnLocation(spawnLoc);
        }
    }

    private void teleportToTeamSpawn(Entity player, Team team) {
        Location spawn = team.getSpawnLocation();

        if (team.getBedLocation() != null && !team.isBedDestroyed()) {
            Location bedLoc = team.getBedLocation();
            spawn = new Location(bedLoc.getWorld(), bedLoc.getX() + 0.5, bedLoc.getY() + 1, bedLoc.getZ() + 0.5);
        }

        if (spawn != null) {
            player.teleport(spawn);
        }
    }

    public EntityEquipment getPlayerInventory(Entity entity) {
        return entity instanceof Player player ? player.getEquipment()
                : ((Mannequin) entity).getEquipment();
    }

    public void equipEntity(Entity entity, Team team) {
        final ItemStack[] armorAndTool = createTeamArmorAndTool(team.getColor().getColor());
        final EntityEquipment equipment = getPlayerInventory(entity);

        equipment.setHelmet(armorAndTool[0]);
        equipment.setLeggings(armorAndTool[1]);
        equipment.setBoots(armorAndTool[2]);
        equipment.setItem(EquipmentSlot.HAND, armorAndTool[3]);
    }

    private ItemStack[] createTeamArmorAndTool(Color color) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta helmetMeta = (LeatherArmorMeta) helmet.getItemMeta();
        helmetMeta.setColor(color);
        helmetMeta.addEnchant(Enchantment.PROTECTION, 1, true);
        helmet.setItemMeta(helmetMeta);

        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta legsMeta = (LeatherArmorMeta) leggings.getItemMeta();
        legsMeta.setColor(color);
        legsMeta.addEnchant(Enchantment.PROTECTION, 1, true);
        leggings.setItemMeta(legsMeta);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bootsMeta = (LeatherArmorMeta) boots.getItemMeta();
        bootsMeta.setColor(color);
        bootsMeta.addEnchant(Enchantment.PROTECTION, 1, true);
        boots.setItemMeta(bootsMeta);

        ItemStack pickaxe = new ItemStack(Material.WOODEN_PICKAXE);
        ItemMeta pickMeta = pickaxe.getItemMeta();
        pickMeta.displayName(Component.text("Pickaxe"));
        pickMeta.addEnchant(Enchantment.EFFICIENCY, 1, true);
        pickaxe.setItemMeta(pickMeta);

        return new ItemStack[] { helmet, leggings, boots, pickaxe };
    }

    public void onPlayerDeath(Entity entity, Player bukkitKiller) {
        final Team playerTeam = getPlayerTeam(entity);

        if (entity instanceof Player player) {
            player.getInventory().clear();
        } else {
            final EntityEquipment equipment = getPlayerInventory(entity);
            equipment.clear();
            equipment.setArmorContents(null);
        }

        Location respawnLocation = lobby;

        if (playerTeam != null && playerTeam.getBedLocation() != null && !playerTeam.isBedDestroyed()) {
            respawnLocation = playerTeam.getBedLocation();
        } else if (playerTeam != null) {
            respawnLocation = playerTeam.getSpawnLocation();
        }

        Player killer = bukkitKiller;
        List<Player> assists = List.of();

        if (entity instanceof Player player) {
            player.setRespawnLocation(respawnLocation);

            KillTracker.KillResult result = killTracker.resolveKill(player, bukkitKiller);
            killer = result.killer();
            assists = result.assists();

            PlayerStatistic playerStat = playerStats.get(entity);
            if (playerStat != null) {
                playerStat.setCurrentDeaths(playerStat.getCurrentDeaths() + 1);
            }
        }

        if (killer != null) {
            PlayerStatistic killerStat = playerStats.get(killer);
            if (killerStat != null) {
                killerStat.setCurrentKills(killerStat.getCurrentKills() + 1);
                killerStat.setCurrentScore(killerStat.getCurrentScore() + 10);
            }

            for (Player assist : assists) {
                PlayerStatistic assistStat = playerStats.get(assist);
                if (assistStat != null) {
                    assistStat.setCurrentAssists(assistStat.getCurrentAssists() + 1);
                    assistStat.setCurrentScore(assistStat.getCurrentScore() + 5);
                }
            }
        }

        PlayerLevelManager levelManager = Main.getInstance().getPlayerLevelManager();
        if (levelManager != null) {
            if (entity instanceof Player deadPlayer) {
                levelManager.addXP(deadPlayer.getUniqueId(), -10);
            }
            if (killer != null) {
                levelManager.addXP(killer.getUniqueId(), 15);
                for (Player assist : assists) {
                    levelManager.addXP(assist.getUniqueId(), 5);
                }
            }
        }

        broadcastKillMessage(entity, playerTeam, killer, assists);

        if (isGameRoomMode() && recorder != null && entity instanceof Player dp) {
            recorder.recordDeath(dp.getUniqueId());
        }

        boolean bedDestroyed = playerTeam != null && playerTeam.isBedDestroyed();

        if (bedDestroyed) {
            if (entity instanceof Player player) {
                addSpectator(player);
            } else {
                playerTeam.removePlayer(entity);
            }
            checkGameOver();
        }
    }

    private void broadcastKillMessage(Entity victim, Team victimTeam, Player killer, List<Player> assists) {
        String victimColor = victimTeam != null ? victimTeam.getColor().getSectionColor() : "§7";

        if (killer == null) {
            broadcastMessage("⚔ " + victimColor + victim.getName() + "§7 est mort");
            return;
        }

        Team killerTeam = getPlayerTeam(killer);
        String killerColor = killerTeam != null ? killerTeam.getColor().getSectionColor() : "§7";

        List<String> killerNames = new ArrayList<>();
        killerNames.add(killer.getName());
        for (Player assist : assists) {
            killerNames.add(assist.getName());
        }

        String killersDisplay = killerColor + String.join("§7, " + killerColor, killerNames);
        broadcastMessage("⚔ " + victimColor + victim.getName() + "§7 a été tué par " + killersDisplay);
    }

    public void onBedDestroyed(Team team, Player destroyer) {
        team.setBedDestroyed(true);

        if (isGameRoomMode() && recorder != null) {
            recorder.recordBedDestroy(team.getColor().name(), destroyer != null ? destroyer.getUniqueId() : null);
        }

        String destroyerName = destroyer != null ? destroyer.getName() : "TNT";
        Team destroyerTeam = destroyer != null ? getPlayerTeam(destroyer) : null;
        String destroyerTeamName = destroyerTeam != null ? destroyerTeam.getColor().name() : "";

        broadcastMessage("§c" + destroyerName + " §7(" + destroyerTeamName + ") a détruit le lit de l'équipe §c"
                + team.getColor().name() + "§7!");

        for (Entity entity : getPlayers()) {
            if (entity instanceof Player player) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            }
        }

        if (destroyerTeam != null) {
            destroyerTeam.setBedsDestroyed(destroyerTeam.getBedsDestroyed() + 1);
            if (!isGameRoomMode() || gameRoom.getConfig().extraHearts()) {
                double bonusHealth = destroyerTeam.getBedsDestroyed() * 4.0;
                for (Entity entity : destroyerTeam.getPlayers()) {
                    if (entity instanceof Player player) {
                        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                        if (maxHealth != null) {
                            maxHealth.removeModifier(NamespacedKey.minecraft("extra_hearts"));
                            maxHealth.addModifier(
                                    new AttributeModifier(NamespacedKey.minecraft("extra_hearts"), bonusHealth,
                                            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                        }
                        player.sendMessage(
                                Component.text("§a+" + destroyerTeam.getBedsDestroyed() * 2 + " Cœurs permanents!"));
                    }
                }
            }
        }

        for (Entity entity : team.getPlayers()) {
            if (entity instanceof Player player) {
                player.sendMessage(Component.text("§cVotre lit a été détruit!"));
            }
        }

        checkGameOver();
    }

    private void checkGameOver() {
        List<Team> teamsWithBeds = teams.values().stream()
                .filter(t -> !t.isBedDestroyed())
                .collect(Collectors.toList());

        List<Team> teamsWithoutBedsButAlive = teams.values().stream()
                .filter(t -> t.isBedDestroyed() && !t.getPlayers().isEmpty())
                .collect(Collectors.toList());

        if (teamsWithBeds.size() == 1 && teamsWithoutBedsButAlive.isEmpty()) {
            endGame(teamsWithBeds.get(0));
        } else if (teamsWithBeds.isEmpty()) {
            List<Team> teamsWithPlayers = teams.values().stream()
                    .filter(t -> !t.getPlayers().isEmpty())
                    .collect(Collectors.toList());
            if (teamsWithPlayers.size() <= 1) {
                endGame(teamsWithPlayers.isEmpty() ? null : teamsWithPlayers.get(0));
            }
        }
    }

    private void endGame(Team winner) {
        state = GameState.STOPPED;
        Main.getInstance().setGameStarted(false);

        if (winner != null) {
            String winMessage = "Victoire de l'équipe " + winner.getColor().name();
            for (Entity entity : getPlayers()) {
                if (entity instanceof Player player) {
                    player.showTitle(Title.title(Component.text(winMessage), Component.empty()));
                }
            }
            for (Player spectator : spectators) {
                spectator.showTitle(Title.title(Component.text(winMessage), Component.empty()));
            }

            // Update winstreaks: increment for winners, reset for losers
            updateWinStreaks(winner);
        }

        // Clear all players' ender chests
        clearAllEnderChests();

        sendGameSummary();

        PlayerLevelManager endLevelManager = Main.getInstance().getPlayerLevelManager();
        if (endLevelManager != null) {
            for (Player player : playerStats.keySet()) {
                boolean won = winner != null && winner.equals(getPlayerTeam(player));
                endLevelManager.addXP(player.getUniqueId(), won ? 100 : 20);
            }
        }

        // Immediately set all players to spectator and remove mannequins
        for (Team team : teams.values()) {
            for (Entity entity : team.getPlayers()) {
                if (entity instanceof Player player) {
                    player.setGameMode(GameMode.SPECTATOR);
                } else if (entity instanceof Mannequin mannequin) {
                    mannequin.remove();
                }
            }
        }

        cycle.onGameEnd();

        // Update all leaderboard holograms after stats are persisted
        updateLeaderboardHolograms();

        // Notify GameManager that game ended (for GameRoom mode)
        if (isGameRoomMode()) {
            if (recorder != null) {
                List<String> participantNames = playerStats.keySet().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                Map<String, String> teamColorsByPlayerUuid = new HashMap<>();
                for (Team team : teams.values()) {
                    for (Entity entity : team.getPlayers()) {
                        if (entity instanceof Player tp) {
                            teamColorsByPlayerUuid.put(tp.getUniqueId().toString(), team.getColor().name());
                        }
                    }
                }
                recorder.stop(gameRoom.getId(), winner != null ? winner.getColor().name() : null,
                        gameRoom.getHostName(), participantNames,
                        gameRoom.getConfig().mapType().name(), teamColorsByPlayerUuid);
                recorder = null;
            }
            Main.getInstance().getGameManager().onGameRoomEnded(gameRoom);
        } else {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), this::resetGame, 400L);
        }
    }

    private void sendGameSummary() {
        Component header = Component.text("§6§l――――――――――――――――――")
                .append(Component.newline())
                .append(Component.text("§6§lRÉSUMÉ DE LA PARTIE"))
                .append(Component.newline())
                .append(Component.text("§6§l――――――――――――――――――"));

        Component duration = Component.text("§eDurée: §f" + getFormattedTime());

        broadcastMessage(header);
        broadcastMessage(duration);
        broadcastMessage(Component.text("§7"));

        playerStats.forEach((player, stats) -> {
            Component playerSummary = Component.text(player.getName() + ": ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text("§a" + stats.getCurrentKills() + " kills §7| "))
                    .append(Component.text("§c" + stats.getCurrentDeaths() + " morts §7| "))
                    .append(Component.text("§e" + stats.getCurrentAssists() + " assists"));
            broadcastMessage(playerSummary);
        });

        broadcastMessage(Component.text("§6§l――――――――――――――――――"));
    }

    private void broadcastMessage(Component message) {
        for (Entity entity : getPlayers()) {
            if (entity instanceof Player player) {
                player.sendMessage(message);
            }
        }
        for (Player spectator : spectators) {
            spectator.sendMessage(message);
        }
    }

    public void forceStop() {
        state = GameState.STOPPED;
        Main.getInstance().setGameStarted(false);

        runningTasks.forEach(BukkitTask::cancel);
        runningTasks.clear();
        stopResourceSpawners();

        List<Entity> allPlayers = new ArrayList<>(getPlayers());
        for (Entity entity : allPlayers) {

            removePlayer(entity);

            if (lobby != null) {
                entity.teleport(lobby);
            }

            EntityEquipment equipment = getPlayerInventory(entity);

            equipment.clear();
            equipment.setArmorContents(null);

            if (entity instanceof Player p) {
                p.setGameMode(GameMode.ADVENTURE);
                resetPlayerHealth(p);
                p.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());
            }
        }

        for (Player spectator : new ArrayList<>(spectators)) {
            removeSpectator(spectator);

            if (lobby != null) {
                spectator.teleport(lobby);
            }

            spectator.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());
        }

        for (Team team : teams.values()) {
            team.reset();
        }

        clearAllEnderChests();
        clearGameState();

        cycle.onGameEnd();

        state = GameState.WAITING;
    }

    private void resetGame() {
        for (Entity entity : getPlayers()) {
            if (entity instanceof Player p) {
                p.setGameMode(GameMode.ADVENTURE);
                resetPlayerHealth(p);
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);

                if (lobby != null) {
                    p.teleport(lobby);
                }

                p.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());
            } else if (entity instanceof Mannequin mannequin) {
                mannequin.remove();
            }

            removePlayer(entity);
        }

        for (Player spectator : new ArrayList<>(spectators)) {
            removeSpectator(spectator);

            if (lobby != null) {
                spectator.teleport(lobby);
            }

            spectator.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());
        }

        for (Team team : teams.values()) {
            team.reset();
        }

        clearGameState();
        runningTasks.forEach(BukkitTask::cancel);
        runningTasks.clear();

        stopResourceSpawners();

        state = GameState.WAITING;
    }

    private void startCompassTracker() {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            List<CompassTracker.Candidate> candidates = teams.values().stream()
                    .flatMap(t -> t.getPlayers().stream()
                            .filter(e -> e instanceof Player)
                            .map(e -> {
                                Player p = (Player) e;
                                return new CompassTracker.Candidate(
                                        UUID.nameUUIDFromBytes(t.getColor().name().getBytes()),
                                        p.getLocation().getX(),
                                        p.getLocation().getY(),
                                        p.getLocation().getZ());
                            }))
                    .toList();

            for (Team team : teams.values()) {
                UUID teamId = UUID.nameUUIDFromBytes(team.getColor().name().getBytes());
                for (Entity entity : team.getPlayers()) {
                    if (!(entity instanceof Player holder)) continue;
                    if (holder.getInventory().getItemInMainHand().getType() != org.bukkit.Material.COMPASS) continue;

                    CompassTracker.Candidate nearest = CompassTracker.findNearestEnemy(
                            holder.getLocation().getX(),
                            holder.getLocation().getY(),
                            holder.getLocation().getZ(),
                            teamId, candidates);

                    if (nearest == null) continue;

                    org.bukkit.inventory.ItemStack compass = holder.getInventory().getItemInMainHand();
                    org.bukkit.inventory.meta.CompassMeta meta =
                            (org.bukkit.inventory.meta.CompassMeta) compass.getItemMeta();
                    meta.setLodestone(new org.bukkit.Location(
                            holder.getWorld(), nearest.x(), nearest.y(), nearest.z()));
                    meta.setLodestoneTracked(false);
                    compass.setItemMeta(meta);
                }
            }
        }, 20L, 20L);
        runningTasks.add(task);
    }

    private void startResourceSpawners() {
        for (int i = 0; i < islandAssignment.size(); i++) {
            Team team = islandAssignment.get(i);

            if (team == null) {
                continue;
            }

            team.placeEnderChests(i);

            for (Location chestLocation : team.getEnderChestLocations()) {
                for (ResourceType type : ResourceType.values()) {
                    ResourceSpawner spawner = new ResourceSpawner(this, type, chestLocation);
                    resourceSpawners.add(spawner);

                    BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                            Main.getInstance(),
                            spawner,
                            spawner.getResourceType().getSpawnIntervalTicks(),
                            spawner.getResourceType().getSpawnIntervalTicks());
                    spawnerTasks.add(task);
                }
            }
        }
    }

    private void stopResourceSpawners() {
        spawnerTasks.forEach(BukkitTask::cancel);
        spawnerTasks.clear();
        resourceSpawners.clear();
    }

    private void updateActionBar() {
        if (state != GameState.WAITING)
            return;

        String gameWorld = Main.getInstance().getGameWorld();
        if (gameWorld == null)
            return;

        long readyCount = getPlayersReadyCount();
        NamedTextColor color = readyCount >= maxPlayers ? NamedTextColor.GREEN : NamedTextColor.RED;
        TextComponent.Builder builder = Component.text()
                .content("Joueurs prêts (")
                .color(NamedTextColor.WHITE);
        builder.append(Component.text(readyCount + "/" + maxPlayers).color(color));
        builder.append(Component.text(")").color(NamedTextColor.WHITE));
        Component message = builder.build();

        for (Player player : Main.getInstance().getServer().getOnlinePlayers()) {
            if (player.getWorld().getName().equals(gameWorld)) {
                player.sendActionBar(message);
            }
        }
    }

    private void broadcastMessage(String message) {
        for (Entity entity : getPlayers()) {
            if (entity instanceof Player player) {
                player.sendMessage(Component.text(message));
            }
        }
    }

    private void updatePlayerList() {
        for (Entity entity : freePlayers) {
            if (entity instanceof Player player) {
                Boolean isReady = playersReady.get(player);
                player.playerListName(
                        Component.text(entity.getName()).color(isReady ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
        }
    }

    public int getTeamCount() {
        return (int) teams.values().stream()
                .filter(t -> !t.getPlayers().isEmpty())
                .count();
    }

    public long getPlayersReadyCount() {
        return playersReady.values().stream().filter(r -> r).count();
    }

    public List<Entity> getPlayers() {
        List<Entity> allPlayers = new ArrayList<>(freePlayers);

        for (Team team : teams.values()) {
            allPlayers.addAll(team.getPlayers());
        }

        return allPlayers;
    }

    public Team getPlayerTeam(Entity player) {
        for (Team team : teams.values()) {
            if (team.isInTeam(player)) {
                return team;
            }
        }
        return null;
    }

    public Team getTeam(String name) {
        return teams.get(name);
    }

    public PlayerStatistic getPlayerStatistic(Player player) {
        return playerStats.computeIfAbsent(player,
                p -> Main.getInstance().getPlayerStatisticManager().loadStatistic(p.getUniqueId()));
    }

    public void saveStats() {
        for (PlayerStatistic stat : playerStats.values()) {
            Main.getInstance().getPlayerStatisticManager().saveStatistic(stat);
        }
    }

    private void updateLeaderboardHolograms() {
        if (Main.getInstance().getCommandManager() != null
                && Main.getInstance().getCommandManager().getLeaderboardCommand() != null) {
            Main.getInstance().getCommandManager().getLeaderboardCommand().updateAllHolograms();
        }
    }

    private void updateWinStreaks(Team winner) {
        for (Entity entity : getPlayers()) {
            if (entity instanceof Player player) {
                PlayerStatistic stats = playerStats.get(player);
                if (stats != null) {
                    Team playerTeam = getPlayerTeam(player);
                    if (playerTeam != null && playerTeam.equals(winner)) {
                        // Winner: increment winstreak
                        stats.setWinStreak(stats.getWinStreak() + 1);
                    } else {
                        // Loser: reset winstreak to 0
                        stats.setWinStreak(0);
                    }
                }
            }
        }
    }

    private void clearAllEnderChests() {
        for (Entity entity : getPlayers()) {
            if (entity instanceof Player player) {
                player.getEnderChest().clear();
            }
        }
        for (Player spectator : spectators) {
            spectator.getEnderChest().clear();
        }
    }

    /**
     * Stops the game and cleans up all resources.
     * Called during plugin shutdown or when a game room is removed.
     */
    public void stop() {
        // Cancel all running tasks
        for (BukkitTask task : runningTasks) {
            task.cancel();
        }
        runningTasks.clear();

        // Stop resource spawners
        stopResourceSpawners();

        // Reset kill tracker
        killTracker.reset();

        // Clear all collections
        freePlayers.clear();
        playersReady.clear();
        playerStats.clear();
        spectators.clear();
        protectedPlayers.clear();

        // Reset teams
        for (Team team : teams.values()) {
            team.getPlayers().clear();
        }

        // Set state to stopped
        state = GameState.STOPPED;
    }
}
