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
import org.bukkit.SoundCategory;
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
import org.bukkit.inventory.meta.CompassMeta;
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

    private BukkitTask overtimeMusicTask;

    @Getter
    private final Map<String, Team> teams = new HashMap<>();
    @Getter
    private final List<Entity> freePlayers = new ArrayList<>();
    private final Map<Player, PlayerStatistic> playerStats = new HashMap<>();
    private final Map<UUID, TeamColor> playerTeamColors = new HashMap<>();
    private final Set<Player> spectators = new HashSet<>();
    private final Set<Player> protectedPlayers = new HashSet<>();
    @Getter
    private final KillTracker killTracker = new KillTracker();

    @Getter
    private int gameTime = 0;

    @Getter
    private List<Team> islandAssignment;
    private List<Integer> islandSlotOrder;
    // Adjacent pair first (S+E) so 2-team forbidden zone covers the SE corridor
    // between them.
    static final int[] PREFERRED_ISLAND_ORDER = { 2, 1, 0, 3 };

    public static List<Integer> islandSlotOrder(int islandCount) {
        List<Integer> order = new ArrayList<>();
        for (int s : PREFERRED_ISLAND_ORDER) {
            if (s < islandCount) order.add(s);
        }
        return order;
    }
    private static final int[][] ISLAND_DIRECTIONS = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final int[] MERCHANT_SPREADS = { 5, 7 };
    private static final int[] SIGNS = { 1, -1 };
    // Visual centres computed once per game by scanning island blocks outward from
    // paste origin.
    private final Map<String, double[]> islandVisualCenterCache = new HashMap<>();

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

    @Setter
    @Getter
    private double coefficient = 1.0;

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
     * maxTeams is the number of teams actually playing (host choice,
     * 2–islandCount).
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
        playerTeamColors.put(player.getUniqueId(), color);

        for (Entity existingPlayer : team.getPlayers()) {
            if (existingPlayer instanceof Player && !existingPlayer.equals(player)) {
                existingPlayer
                        .sendMessage(Component.translatable("rush.player_joined_team",
                                        Component.text(player.getName()), Component.text(color.name()))
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
        addSpectator(player, false);
        player.sendMessage(Component.translatable("rush.spectatorModeEnteredAfterBedDestroyed"));
    }

    public void addObserver(Player player) {
        addSpectator(player, true);
        player.sendMessage(Component.translatable("rush.spectator_viewing"));
    }

    private void addSpectator(Player player, boolean isObserver) {
        spectators.add(player);
        if (!isObserver) {
            Team team = getPlayerTeam(player);
            if (team != null) {
                team.removePlayer(player);
            }
            freePlayers.remove(player);
            playersReady.remove(player);
        }
        applySpectatorMode(player);
        String compassName = isObserver ? "§cQuitter la partie" : "§cQuitter le spectator";
        player.getInventory().setItem(0, ItemBuilder.of(Material.COMPASS).name(compassName).build());
        hideFromGameWorld(player);
        if (lobby != null) {
            player.teleport(lobby);
        }
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
        player.sendMessage(Component.translatable("rush.spectatorModeQuit"));
    }

    public boolean isProtected(Player player) {
        return protectedPlayers.contains(player);
    }

    public void addProtection(Player player) {
        int protectionTime = Main.getInstance().getRespawnProtectionTime();

        if (protectionTime <= 0)
            return;

        if (isGameRoomMode() && recorder != null) {
            Location loc = player.getLocation();
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
                player.sendActionBar(Component.translatable("rush.protection_countdown", Component.text(remaining)));
            }
        }, 0, 20L);

        runningTasks.add(task);

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            removeProtection(player);
            player.sendActionBar(Component.text(""));
            player.sendMessage(Component.translatable("rush.protection_ended"));
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
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
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
                broadcastMessage(Component.translatable("rush.not_enough_ready"));
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
            broadcastMessage(Component.translatable("rush.overtime"));
            if (isGameRoomMode() && recorder != null) {
                recorder.recordPhaseChange("OVERTIME");
            }
            playOvertimeMusic();
        }
    }

    private void playOvertimeMusic() {
        if (overtimeMusicTask != null) return;
        String intro = "tland:music.global.overtime_intro_music";
        String loop = "tland:music.global.overtime_loop_music";
        for (Entity entity : getPlayers()) {
            if (entity instanceof Player player) {
                player.playSound(player.getLocation(), intro, SoundCategory.MUSIC, 1.0f, 1.0f);
            }
        }
        overtimeMusicTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (Entity entity : getPlayers()) {
                if (entity instanceof Player player) {
                    player.playSound(player.getLocation(), loop, SoundCategory.MUSIC, 1.0f, 1.0f);
                }
            }
        }, 160L, 800L);
    }

    public boolean isBlockInForbiddenZone(Location location) {
        if (isOvertime() || islandAssignment == null) {
            return false;
        }

        // Island platforms are never part of the forbidden corridor.
        if (isBlockOnIsland(location)) {
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

    public boolean isBlockOnResourceSpawn(Location location) {
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        for (Team team : islandAssignment) {
            if (team == null)
                continue;
            for (Location chestLoc : team.getEnderChestLocations()) {
                if (bx == chestLoc.getBlockX() && by == chestLoc.getBlockY() + 1 && bz == chestLoc.getBlockZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isBlockNearRegularMerchant(Location location) {
        World world = location.getWorld();
        if (world == null)
            return false;
        List<Island> islands = getAllIslandPositions();
        if (islands.isEmpty())
            return false;

        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        int speedOffset = Main.getInstance().getConfig().getInt("villagerSpeedOffset", 13);
        int regularOffset = Main.getInstance().getConfig().getInt("villagerRegularOffset", speedOffset - 1);
        int radius = Main.getInstance().getConfig().getInt("merchantProtectionRadius", 3);
        int islandY = isGameRoomMode() && gameRoom != null
                ? gameRoom.getIslandY()
                : (Main.getISLAND_Y() > 0 ? Main.getISLAND_Y() : world.getMaxHeight() - 12);

        // Merchants span islandY+1 (feet) to islandY+2 (head); skip all iteration if Y
        // is out of range
        if (by < islandY + 1 - radius || by > islandY + 2 + radius)
            return false;

        for (int islandIndex = 0; islandIndex < islands.size(); islandIndex++) {
            Island island = islands.get(islandIndex);
            int[] dir = ISLAND_DIRECTIONS[islandIndex];
            int perpX = dir[1];
            int perpZ = -dir[0];
            int baseX = island.getX() + dir[0] * regularOffset;
            int baseZ = island.getZ() + dir[1] * regularOffset;

            for (int spread : MERCHANT_SPREADS) {
                for (int sign : SIGNS) {
                    int regX = baseX + perpX * spread * sign;
                    int regZ = baseZ + perpZ * spread * sign;
                    if (Math.abs(bx - regX) <= radius && Math.abs(bz - regZ) <= radius) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isBlockInRingPath(Location location) {
        final List<Island> allIslands = getAllIslandPositions();
        if (allIslands.isEmpty())
            return true;
        if (isBlockOnIsland(location))
            return true;
        return RingPath.isOnPath(location.getX(), location.getZ(), allIslands);
    }

    /**
     * Returns true if the location falls within the visual-centre radius of any
     * island platform. Used to exempt island surfaces from both the ring-path
     * restriction and the forbidden-zone corridor restriction.
     */
    private boolean isBlockOnIsland(Location location) {
        final World world = location.getWorld();
        if (world == null)
            return false;

        final List<Island> allIslands = getAllIslandPositions();
        if (allIslands.isEmpty())
            return false;

        final int islandY = isGameRoomMode() && gameRoom != null
                ? gameRoom.getIslandY()
                : (Main.getISLAND_Y() > 0 ? Main.getISLAND_Y() : world.getMaxHeight() - 12);

        final int regularOffset = Main.getInstance().getConfig().getInt("villagerRegularOffset",
                Main.getInstance().getConfig().getInt("villagerSpeedOffset", 13) - 1);
        final double islandRadiusSq = (regularOffset + 2.0) * (regularOffset + 2.0);
        final double bx = location.getX();
        final double bz = location.getZ();

        for (Island island : allIslands) {
            final String key = island.getX() + "," + island.getZ();
            final double[] center = islandVisualCenterCache.computeIfAbsent(key,
                    k -> computeIslandVisualCenter(island, world, islandY));
            final double dx = bx - center[0];
            final double dz = bz - center[1];
            if (dx * dx + dz * dz <= islandRadiusSq) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scans outward from the island's paste origin along the radial axis (away
     * from the map centre) at surface level to find the island's far edge, then
     * returns the midpoint as the visual centre.
     *
     * The paste origin is the inner edge of the island (map-facing side,
     * transversely
     * centred). The island extends outward from there, so the true visual centre is
     * at origin + depth/2 along the radial direction.
     */
    private double[] computeIslandVisualCenter(Island island, World world, int islandY) {
        final double ix = island.getX();
        final double iz = island.getZ();
        final double len = Math.sqrt(ix * ix + iz * iz);
        if (len == 0.0)
            return new double[] { ix, iz };

        // Unit vector pointing away from map centre (outward through the island)
        final double radX = ix / len;
        final double radZ = iz / len;

        // Walk outward from the paste origin; remember the farthest non-air block
        int lastOffset = 0;
        for (int d = 0; d <= 40; d++) {
            final int bx = (int) Math.round(ix + d * radX);
            final int bz = (int) Math.round(iz + d * radZ);
            if (world.getBlockAt(bx, islandY, bz).getType() != Material.AIR) {
                lastOffset = d;
            }
        }

        // Midpoint between paste origin and far edge = visual centre
        final double halfDepth = lastOffset / 2.0;
        return new double[] { ix + halfDepth * radX, iz + halfDepth * radZ };
    }

    public List<Island> getAllIslandPositions() {
        List<Island> raw;
        if (isGameRoomMode() && gameRoom != null) {
            raw = gameRoom.getIslands();
        } else {
            raw = Main.getInstance().getIslands();
        }
        if (raw == null || raw.isEmpty())
            return List.of();
        // Sort by angle from centroid to guarantee cyclic (N→E→S→W) order for
        // the bridge-segment check, regardless of how the list was built.
        double cx = raw.stream().mapToDouble(Island::getX).average().orElse(0);
        double cz = raw.stream().mapToDouble(Island::getZ).average().orElse(0);
        return raw.stream()
                .sorted(Comparator.comparingDouble((Island i) -> Math.atan2(i.getZ() - cz, i.getX() - cx)))
                .toList();
    }

    private void computeIslandAssignment() {
        List<Team> orderedTeams = teams.values().stream()
                .sorted(Comparator.comparingInt(t -> t.getColor().ordinal()))
                .collect(Collectors.toList());

        islandSlotOrder = islandSlotOrder(islandCount);

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
                broadcastMessage(Component.translatable("rush.overtime"));
                playOvertimeMusic();
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
        pickMeta.displayName(Component.translatable("rush.item_pickaxe"));
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

            PlayerStatistic playerStat = getPlayerStatistic(player);
            playerStat.setCurrentDeaths(playerStat.getCurrentDeaths() + 1);
        }

        if (killer != null) {
            PlayerStatistic killerStat = getPlayerStatistic(killer);
            killerStat.setCurrentKills(killerStat.getCurrentKills() + 1);
            killerStat.setCurrentScore(killerStat.getCurrentScore() + 10);

            for (Player assist : assists) {
                PlayerStatistic assistStat = getPlayerStatistic(assist);
                assistStat.setCurrentAssists(assistStat.getCurrentAssists() + 1);
                assistStat.setCurrentScore(assistStat.getCurrentScore() + 5);
            }
        }

        PlayerLevelManager levelManager = Main.getInstance().getPlayerLevelManager();
        if (levelManager != null && killer != null) {
            levelManager.addXP(killer.getUniqueId(), Math.round(15 * coefficient));
            for (Player assist : assists) {
                levelManager.addXP(assist.getUniqueId(), Math.round(8 * coefficient));
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
        net.kyori.adventure.text.format.TextColor victimColor = victimTeam != null
                ? victimTeam.getColor().getTextColor() : NamedTextColor.GRAY;

        if (killer == null) {
            broadcastMessage(Component.translatable("rush.kill_no_killer",
                    Component.text(victim.getName()).color(victimColor)));
            return;
        }

        Team killerTeam = getPlayerTeam(killer);
        net.kyori.adventure.text.format.TextColor killerColor = killerTeam != null
                ? killerTeam.getColor().getTextColor() : NamedTextColor.GRAY;

        List<Component> killerNames = new ArrayList<>();
        killerNames.add(Component.text(killer.getName()).color(killerColor));
        for (Player assist : assists) {
            killerNames.add(Component.text(assist.getName()).color(killerColor));
        }

        Component killersDisplay = Component.join(Component.text(", ", NamedTextColor.GRAY), killerNames);
        broadcastMessage(Component.translatable("rush.kill_with_killer",
                Component.text(victim.getName()).color(victimColor), killersDisplay));
    }

    public void onBedDestroyed(Team team, Player destroyer) {
        team.setBedDestroyed(true);

        if (isGameRoomMode() && recorder != null) {
            recorder.recordBedDestroy(team.getColor().name(), destroyer != null ? destroyer.getUniqueId() : null);
        }

        String destroyerName = destroyer != null ? destroyer.getName() : "TNT";
        Team destroyerTeam = destroyer != null ? getPlayerTeam(destroyer) : null;
        String destroyerTeamName = destroyerTeam != null ? destroyerTeam.getColor().name() : "";

        broadcastMessage(Component.translatable("rush.bed_destroyed_broadcast",
                Component.text(destroyerName), Component.text(destroyerTeamName),
                Component.text(team.getColor().name())));

        for (Entity entity : getPlayers()) {
            if (entity instanceof Player player) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            }
        }

        if (destroyer != null) {
            PlayerLevelManager bedLevelManager = Main.getInstance().getPlayerLevelManager();
            if (bedLevelManager != null) {
                bedLevelManager.addXP(destroyer.getUniqueId(), Math.round(30 * coefficient));
            }
            PlayerStatistic destroyerStat = getPlayerStatistic(destroyer);
            destroyerStat.setCurrentDestroyedBeds(destroyerStat.getCurrentDestroyedBeds() + 1);
        }

        if (destroyerTeam != null) {
            destroyerTeam.setBedsDestroyed(destroyerTeam.getBedsDestroyed() + 1);
            if (!isGameRoomMode() || gameRoom.getConfig().extraHearts()) {
                double bonusHealth = destroyerTeam.getBedsDestroyed() * 4.0;
                for (Entity entity : destroyerTeam.getPlayers()) {
                    if (entity instanceof Player player) {
                        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                        if (maxHealth != null) {
                            maxHealth.removeModifier(NamespacedKey.minecraft("extra_hearts"));
                            maxHealth.addModifier(
                                    new AttributeModifier(NamespacedKey.minecraft("extra_hearts"), bonusHealth,
                                            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                        }
                        player.sendMessage(
                                Component.translatable("rush.extra_hearts",
                                        Component.text(destroyerTeam.getBedsDestroyed() * 2)));
                    }
                }
            }
        }

        for (Entity entity : team.getPlayers()) {
            if (entity instanceof Player player) {
                player.sendMessage(Component.translatable("rush.bed_destroyed"));
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
            for (Entity entity : getPlayers()) {
                if (entity instanceof Player player) {
                    player.showTitle(Title.title(
                            Component.translatable("rush.win", Component.text(winner.getColor().name())),
                            Component.empty()));
                }
            }
            for (Player spectator : spectators) {
                spectator.showTitle(
                        Title.title(Component.translatable("rush.win", Component.text(winner.getColor().name())),
                                Component.empty()));
            }

            // Update winstreaks: increment for winners, reset for losers
            updateWinStreaks(winner);
        }

        // Cancel overtime music
        if (overtimeMusicTask != null) {
            overtimeMusicTask.cancel();
            overtimeMusicTask = null;
        }

        // Play game-end sounds
        if (isGameRoomMode()) {
            String winSound = "tland:games.global.win_celebrate";
            String endMusic = "tland:music.global.gameendmusic";
            for (Entity entity : getPlayers()) {
                if (entity instanceof Player player) {
                    player.playSound(player.getLocation(), winSound, SoundCategory.MUSIC, 1.0f, 1.0f);
                    player.playSound(player.getLocation(), endMusic, SoundCategory.MUSIC, 1.0f, 1.0f);
                }
            }
            for (Player spectator : spectators) {
                spectator.playSound(spectator.getLocation(), winSound, SoundCategory.MUSIC, 1.0f, 1.0f);
                spectator.playSound(spectator.getLocation(), endMusic, SoundCategory.MUSIC, 1.0f, 1.0f);
            }
        }

        // Clear all players' ender chests
        clearAllEnderChests();

        sendGameSummary();

        PlayerLevelManager endLevelManager = Main.getInstance().getPlayerLevelManager();
        if (endLevelManager != null) {
            for (Player player : playerStats.keySet()) {
                boolean won = winner != null && winner.equals(getPlayerTeam(player));
                if (won) {
                    endLevelManager.addXP(player.getUniqueId(), Math.round(200 * coefficient));
                }
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
                // Include all players (including eliminated ones whose team was tracked persistently)
                for (Map.Entry<UUID, TeamColor> entry : playerTeamColors.entrySet()) {
                    teamColorsByPlayerUuid.put(entry.getKey().toString(), entry.getValue().name());
                }
                // Also include mannequins still on teams
                for (Team team : teams.values()) {
                    for (Entity entity : team.getPlayers()) {
                        if (entity instanceof Mannequin) {
                            teamColorsByPlayerUuid.put(entity.getUniqueId().toString(), team.getColor().name());
                        }
                    }
                }
                recorder.stop(gameRoom.getId(), winner != null ? winner.getColor().name() : null,
                        gameRoom.getHostName(), participantNames,
                        gameRoom.getConfig().mapType().name(),
                        gameRoom.getConfig().islandType().name(),
                        gameRoom.getConfig().maxTeams(),
                        teamColorsByPlayerUuid);
                recorder = null;
            }
            Main.getInstance().getGameManager().onGameRoomEnded(gameRoom);
        } else {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), this::resetGame, 400L);
        }
    }

    private void sendGameSummary() {
        Component header = Component.translatable("rush.game_end_summary")
                .append(Component.newline())
                .append(Component.translatable("rush.game_summary_header"))
                .append(Component.newline())
                .append(Component.translatable("rush.game_end_summary"));

        Component duration = Component.translatable("rush.game_duration", Component.text(getFormattedTime()));

        broadcastMessage(header);
        broadcastMessage(duration);
        broadcastMessage(Component.empty());

        for (Entity entity : getPlayers()) {
            if (entity instanceof Player player) {
                PlayerStatistic stats = getPlayerStatistic(player);
                Component playerSummary = Component.text(player.getName())
                        .color(NamedTextColor.GRAY)
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(Component.translatable("rush.game_summary_kills",
                                Component.text(stats.getCurrentKills()),
                                Component.text(stats.getCurrentDeaths()),
                                Component.text(stats.getCurrentAssists()),
                                Component.text(stats.getCurrentDestroyedBeds())));
                broadcastMessage(playerSummary);
            }
        }

        broadcastMessage(Component.translatable("rush.game_end_summary"));
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
                    if (!(entity instanceof Player holder))
                        continue;
                    if (holder.getInventory().getItemInMainHand().getType() != Material.COMPASS)
                        continue;

                    CompassTracker.Candidate nearest = CompassTracker.findNearestEnemy(
                            holder.getLocation().getX(),
                            holder.getLocation().getY(),
                            holder.getLocation().getZ(),
                            teamId, candidates);

                    if (nearest == null)
                        continue;

                    ItemStack compass = holder.getInventory().getItemInMainHand();
                    CompassMeta meta = (CompassMeta) compass.getItemMeta();

                    meta.setLodestone(new Location(
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
        Component message = Component.translatable("rush.ready_players",
                Component.text(readyCount + "/" + maxPlayers).color(color));

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
                final Boolean isReady = playersReady.get(player);
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
        final List<Entity> allPlayers = new ArrayList<>(freePlayers);
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
                final PlayerStatistic stats = getPlayerStatistic(player);
                final Team playerTeam = getPlayerTeam(player);
                if (playerTeam != null && playerTeam.equals(winner)) {
                    stats.setWinStreak(stats.getWinStreak() + 1);
                } else {
                    stats.setWinStreak(0); // streak is lost
                }
            }
        }
    }

    /**
     * Removes all items from the ender chests
     * of all players/spectators.
     */
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
        if (overtimeMusicTask != null) {
            overtimeMusicTask.cancel();
            overtimeMusicTask = null;
        }

        for (BukkitTask task : runningTasks) {
            task.cancel();
        }
        runningTasks.clear();

        stopResourceSpawners();

        killTracker.reset();

        freePlayers.clear();
        playersReady.clear();
        playerStats.clear();
        spectators.clear();
        protectedPlayers.clear();

        for (Team team : teams.values()) {
            team.getPlayers().clear();
        }

        state = GameState.STOPPED;
    }
}
