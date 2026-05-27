package io.github.rush.game;

import io.github.rush.geometry.RingPath;

import io.github.rush.geometry.ForbiddenZone;

import io.github.rush.abstracts.Generator;
import io.github.rush.abstracts.Team;

import io.github.rush.entities.GameMannequin;
import io.github.rush.entities.GameCombatant;
import io.github.rush.entities.GamePlayer;
import io.github.rush.Main;
import io.github.rush.utils.i18n;
import io.github.rush.guis.TeamSelectionGUI;
import io.github.rush.sound.RushSounds;
import io.github.rush.utils.ItemBuilder;
import io.github.rush.objects.Island;
import io.github.rush.utils.ReplayUtils.ReplayFile;
import io.github.rush.replay.ReplayRecorder;
import io.github.rush.storage.PlayerLevelManager;
import io.github.rush.storage.PlayerStatisticManager.PlayerStatistic;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final List<Generator> generators = new ArrayList<>();
    private BukkitTask overtimeMusicTask;

    @Getter
    private final Map<String, Team> teams = new HashMap<>();

    @Getter
    private final List<GameCombatant> freePlayers = new ArrayList<>();
    private final Map<Player, PlayerStatistic> playerStats = new HashMap<>();
    private final Map<UUID, Team.Color> playerTeamColors = new HashMap<>();
    private final Set<GamePlayer> spectators = new HashSet<>();
    private final Set<Player> protectedPlayers = new HashSet<>();
    private final Set<UUID> mannequinsBeingRescued = new HashSet<>();

    @Getter
    private final GameKillTracker killTracker = new GameKillTracker();

    @Getter
    private int gameTime = 0;

    @Getter
    private List<Team> islandAssignment;
    private List<Integer> islandSlotOrder;

    public static List<Integer> islandSlotOrder(int islandCount) {
        List<Integer> order = new ArrayList<>();
        for (int s : Island.Layout.PREFERRED_ISLAND_ORDER) {
            if (s < islandCount)
                order.add(s);
        }
        return order;
    }

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
    private List<Island> islands = List.of();

    @Getter
    private final int minPlayers = 2;

    @Getter
    private final int minTeams = 2;

    @Getter
    private final String worldName;
    private GameLobbyCountdown lobbyCountdown;
    private final Map<GameCombatant, Boolean> playersReady = new HashMap<>();

    @Setter
    @Getter
    private GameRoom gameRoom = null;

    @Setter
    @Getter
    private double coefficient = 1.0;
    private ReplayRecorder recorder = null;

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

    private void initializeTeams(int maxTeams, int playersPerTeam) {
        for (Team.Color color : Team.Color.values()) {
            Team team = new Team(color.name(), color, playersPerTeam);
            teams.put(color.name(), team);
        }
    }

    public int getTotalPlayerCount() {
        return getPlayers().size();
    }

    /**
     * Unsubscribe a game participant from the game room, and updates the tablist.
     */
    public void removePlayer(GameCombatant participant) {
        freePlayers.remove(participant);
        playersReady.remove(participant);
        spectators.remove(participant);

        for (Team team : teams.values()) {
            if (team.isInTeam(participant)) {
                team.removePlayer(participant);
                break;
            }
        }

        updatePlayerList();
    }

    /**
     * Assigns a team and color to the specified player
     * Marks the player by default as not ready
     * Notifies a message to all team players
     * Updates the player list
     * Checks if current game room is auto-startable
     *
     * @param player - the player to join the team
     * @param color  - the color of the team to join
     * @return boolean - evaluates if the player was successfully added to the team
     */
    public boolean joinTeam(GameCombatant participant, Team.Color color) {
        final Team team = teams.get(color.name());

        if (team == null) {
            return false;
        }

        for (Team existingTeam : teams.values()) {
            if (existingTeam.isInTeam(participant)) {
                existingTeam.removePlayer(participant);
                break;
            }
        }

        final Boolean added = team.addPlayer(participant);

        if (freePlayers.contains(participant)) {
            freePlayers.remove(participant);
        }

        playersReady.put(participant, true);
        playerTeamColors.put(participant.uniqueId(), color);

        for (GameCombatant existing : team.getPlayers()) {
            if (existing instanceof GamePlayer gp && !gp.uniqueId().equals(participant.uniqueId())) {
                gp.player().sendMessage(Component.translatable("rush.player_joined_team",
                        Component.text(participant.name()), Component.text(color.name()))
                        .color(color.getTextColor()));
            }
        }

        updatePlayerList();
        checkStartCondition();

        return added;
    }

    public void leaveTeam(GameCombatant participant) {
        for (Team team : teams.values()) {
            if (team.isInTeam(participant)) {
                team.removePlayer(participant);
                break;
            }
        }

        if (!freePlayers.contains(participant)) {
            freePlayers.add(participant);
        }

        playersReady.put(participant, false);

        if (participant instanceof GamePlayer) {
            updatePlayerList();
        }
    }

    public boolean isPlayerReady(GameCombatant participant) {
        Boolean ready = playersReady.get(participant);
        return ready != null && ready;
    }

    public void addSpectator(GamePlayer gamePlayer) {
        addSpectator(gamePlayer, false);
        gamePlayer.player().sendMessage(Component.translatable("rush.spectatorModeEnteredAfterBedDestroyed"));
    }

    public void addObserver(GamePlayer gamePlayer) {
        addSpectator(gamePlayer, true);
        gamePlayer.player().sendMessage(Component.translatable("rush.spectator_viewing"));
    }

    private void addSpectator(GamePlayer gamePlayer, boolean isObserver) {
        spectators.add(gamePlayer);

        if (!isObserver) {
            Team team = getPlayerTeam(gamePlayer);
            if (team != null) {
                team.removePlayer(gamePlayer);
            }
            freePlayers.remove(gamePlayer);
            playersReady.remove(gamePlayer);
        }

        gamePlayer.player().setGameMode(GameMode.SPECTATOR);
        gamePlayer.player().getInventory().clear();

        final String compassName = isObserver ? "§cQuitter la partie" : "§cQuitter le spectator";

        gamePlayer.player().getInventory().setItem(0, ItemBuilder.of(Material.COMPASS).name(compassName).build());
        hideFromGameWorld(gamePlayer.player());

        if (lobby != null) {
            gamePlayer.teleport(lobby);
        }

        updatePlayerList();
    }

    public boolean isSpectator(GamePlayer gamePlayer) {
        return spectators.contains(gamePlayer);
    }

    public Collection<GamePlayer> getSpectators() {
        return Collections.unmodifiableCollection(spectators);
    }

    public void removeSpectator(GamePlayer gamePlayer) {
        spectators.remove(gamePlayer);

        Player player = gamePlayer.player();

        showToGameWorld(player);
        Main.getInstance().getGameManager().resetPlayerHubState(player);

        player.sendMessage(Component.translatable("rush.spectatorModeQuit"));
    }

    public boolean isProtected(Player player) {
        return protectedPlayers.contains(player);
    }

    public void addProtection(Player player) {
        final int protectionTime = Main.getInstance().getRespawnProtectionTime();

        if (protectionTime <= 0)
            return;

        if (recorder != null) {
            final Location loc = player.getLocation();
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

        final AtomicInteger elapsed = new AtomicInteger(0);
        final BukkitTask task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (!protectedPlayers.contains(player)) {
                return;
            }

            final int remaining = protectionTime - elapsed.getAndIncrement();

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

    public static void resetPlayerHealth(Player player) {
        final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);

        if (maxHealth != null) {
            maxHealth.removeModifier(NamespacedKey.minecraft("extra_hearts"));
        }

        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    public void setPlayerReady(GameCombatant participant, boolean ready) {
        playersReady.put(participant, ready);
        checkStartCondition();
    }

    public void checkStartCondition() {
        if (state == GameState.WAITING) {
            if (areEnoughTeamsFull()) {
                if (lobbyCountdown == null) {
                    lobbyCountdown = new GameLobbyCountdown(this, false);
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
        final long fullReadyTeams = teams.values().stream()
                .filter(t -> t.getPlayers().stream()
                        .filter(e -> Boolean.TRUE.equals(playersReady.get(e)))
                        .count() >= getPlayersPerTeam())
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
            lobbyCountdown = new GameLobbyCountdown(this, true);
            lobbyCountdown.setCounter(15);
            lobbyCountdown.start();
        }
    }

    public void autoStart() {
        if (state == GameState.WAITING) {
            final List<GameCombatant> unassigned = freePlayers.stream()
                    .filter(p -> teams.values().stream().noneMatch(t -> t.isInTeam(p)))
                    .collect(Collectors.toList());

            if (!unassigned.isEmpty()) {
                final int maxActiveTeams = maxPlayers / getPlayersPerTeam();
                final List<Team> sortedTeams = teams.values().stream()
                        .sorted(Comparator.<Team, Boolean>comparing(t -> t.getPlayers().isEmpty())
                                .thenComparingInt(t -> t.getPlayers().size()))
                        .limit(maxActiveTeams)
                        .collect(Collectors.toList());

                for (GameCombatant participant : unassigned) {
                    if (sortedTeams.isEmpty())
                        break;

                    final Team smallestTeam = sortedTeams.get(0);

                    if (smallestTeam.getPlayers().size() < smallestTeam.getMaxPlayers()) {
                        smallestTeam.addPlayer(participant);
                    }
                }

                checkStartCondition();
            }
        }
    }

    private int getOvertimeSeconds() {
        return gameRoom.getConfig().overtimeDuration() * 60;
    }

    public boolean isOvertime() {
        return gameTime >= getOvertimeSeconds();
    }

    public String getFormattedTime() {
        return String.format("%02d:%02d", gameTime / 60, gameTime % 60);
    }

    public void incrementGameTime() {
        gameTime++;
        if (gameTime == getOvertimeSeconds()) {
            broadcastMessage(Component.translatable("rush.overtime"));
            if (recorder != null) {
                recorder.recordPhaseChange("OVERTIME");
            }
            playOvertimeMusic();
        }
    }

    private void playOvertimeMusic() {
        if (overtimeMusicTask != null)
            return;

        for (GameCombatant participant : getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                RushSounds.OVERTIME_INTRO.play(gp.player());
            }
        }

        overtimeMusicTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (GameCombatant participant : getPlayers()) {
                if (participant instanceof GamePlayer gp) {
                    RushSounds.OVERTIME_LOOP.play(gp.player());
                }
            }
        }, 160L, 800L);
    }

    public boolean isBlockInForbiddenZone(Location location) {
        if (isOvertime() || islandAssignment == null) {
            return false;
        }

        // skip island platforms because they are never part of the forbidden corridor
        if (isBlockOnIsland(location)) {
            return false;
        }

        List<Team> activeTeamList = islandAssignment.stream()
                .filter(t -> t != null && !t.getPlayers().isEmpty())
                .collect(Collectors.toList());

        // skip in favor of 2-teams feature-exclusive
        if (activeTeamList.size() != 2) {
            return false;
        }

        final Team teamA = activeTeamList.get(0);
        final Team teamB = activeTeamList.get(1);

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
        int speedOffset = Main.getInstance().getConfig().getInt("villagerSpeedOffset");
        int regularOffset = Main.getInstance().getConfig().getInt("villagerRegularOffset", speedOffset - 1);
        int radius = Main.getInstance().getConfig().getInt("merchantProtectionRadius");
        int islandY = gameRoom.getIslandY();

        // Merchants span islandY+1 (feet) to islandY+2 (head); skip all iteration if Y
        // is out of range
        if (by < islandY + 1 - radius || by > islandY + 2 + radius)
            return false;

        for (int islandIndex = 0; islandIndex < islands.size(); islandIndex++) {
            Island island = islands.get(islandIndex);
            int[] dir = Island.Layout.ISLAND_DIRECTIONS[islandIndex];
            int perpX = dir[1];
            int perpZ = -dir[0];
            int baseX = island.getX() + dir[0] * regularOffset;
            int baseZ = island.getZ() + dir[1] * regularOffset;

            for (int spread : Island.Layout.MERCHANT_SPREADS) {
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

        final int islandY = gameRoom.getIslandY();

        final int regularOffset = Main.getInstance().getConfig().getInt("villagerRegularOffset",
                Main.getInstance().getConfig().getInt("villagerSpeedOffset") - 1);
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
        List<Island> raw = gameRoom.getIslands();
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
                .filter(t -> !t.getPlayers().isEmpty())
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

            if (gameRoom.getConfig().overtimeStart()) {
                gameTime = getOvertimeSeconds();
                broadcastMessage(Component.translatable("rush.overtime"));
                for (GameCombatant participant : getPlayers()) {
                    if (participant instanceof GamePlayer gp) {
                        RushSounds.BORDER_SHRINKING.play(gp.player());
                    }
                }
                playOvertimeMusic();
            }

            computeIslandAssignment();
            loadIslandsAndSetSpawns();

            for (int i = 0; i < islandAssignment.size(); i++) {
                final Team team = islandAssignment.get(i);

                if (team != null) {
                    team.placeBed(i);
                }
            }

            if (gameRoom.getConfig().extraHearts()) {
                Set<Team.Color> takenColors = teams.values().stream()
                        .map(Team::getColor)
                        .collect(Collectors.toSet());
                List<Team.Color> extraColors = randomExtraBedColors(takenColors);
                for (int i = 0; i < islandAssignment.size(); i++) {
                    if (islandAssignment.get(i) == null) {
                        placeExtraBed(i, extraColors);
                    }
                }
            }

            for (GameCombatant participant : getPlayers()) {
                final Team team = getPlayerTeam(participant);

                if (team != null) {
                    if (participant instanceof GamePlayer gp) {
                        gp.player().getInventory().clear();
                        gp.player().getEnderChest().clear();
                        gp.player().setGameMode(GameMode.SURVIVAL);
                    }

                    teleportToTeamSpawn(participant, team);
                    equipEntity(participant, team);
                }
            }

            startGenerators();
            startCompassTracker();
            startMannequinVoidCheck();

            cycle.onGameStart();

            recorder = new ReplayRecorder(this, worldName);

            Main.getInstance().getGameManager().onGameRoomStarted(gameRoom);
        }
    }

    private void loadIslandsAndSetSpawns() {
        World gameWorld = gameRoom.getWorld();
        islands = gameRoom.getIslands();
        int islandY = gameRoom.getIslandY();

        for (int i = 0; i < islandAssignment.size() && i < islands.size(); i++) {
            final Team team = islandAssignment.get(i);

            if (team == null)
                continue;

            final Island island = islands.get(i);
            final Location spawnLoc = new Location(gameWorld, island.getX(), islandY + 2, island.getZ());

            team.setSpawnLocation(spawnLoc);
        }
    }

    public static List<Team.Color> randomExtraBedColors(Collection<Team.Color> taken) {
        List<Team.Color> available = new ArrayList<>(List.of(Team.Color.values()));
        available.removeAll(taken);
        Collections.shuffle(available);
        return available;
    }

    private void placeExtraBed(int islandIndex, List<Team.Color> extraColors) {
        if (islands == null || islandIndex >= islands.size())
            return;
        final World gameWorld = gameRoom.getWorld();
        if (gameWorld == null)
            return;
        Team.placeIslandBed(gameWorld, islands.get(islandIndex), islandIndex,
                gameRoom.getIslandY(), extraColors.get(islandIndex % extraColors.size()));
    }

    public void onExtraBedDestroyed(Player destroyer) {
        rewardDestroyer(destroyer);
        broadcastMessage(Component.translatable("rush.neutral_bed_destroyed",
                Component.text(destroyer.getName())));

        if (recorder != null) {
            recorder.recordBedDestroy("NEUTRAL", destroyer.getUniqueId());
        }
    }

    private void rewardDestroyer(Player destroyer) {
        if (destroyer == null)
            return;

        final PlayerLevelManager levelManager = Main.getInstance().getPlayerLevelManager();
        if (levelManager != null) {
            levelManager.addXP(destroyer.getUniqueId(), Math.round(30 * coefficient));
        }

        final PlayerStatistic stat = getPlayerStatistic(destroyer);
        stat.setCurrentDestroyedBeds(stat.getCurrentDestroyedBeds() + 1);

        final Team sourceTeam = getPlayerTeam(new GamePlayer(destroyer));
        if (sourceTeam != null) {
            sourceTeam.setBedsDestroyed(sourceTeam.getBedsDestroyed() + 1);
            applyExtraHearts(sourceTeam);
        }
    }

    private void applyExtraHearts(Team sourceTeam) {
        if (gameRoom == null || !gameRoom.getConfig().extraHearts())
            return;

        if (gameRoom.getConfig().maxTeams() <= 2)
            return;

        final int maxHearts = Main.getInstance().getConfig().getInt("maxBedsGrantHearts");
        final double bonusHealth = Math.min(sourceTeam.getBedsDestroyed() * 4.0, maxHearts * 2.0);
        final boolean capped = sourceTeam.getBedsDestroyed() * 4.0 >= maxHearts * 2.0
                && (sourceTeam.getBedsDestroyed() - 1) * 4.0 < maxHearts * 2.0;

        for (GameCombatant participant : sourceTeam.getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                final Player player = gp.player();
                final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) {
                    maxHealth.removeModifier(NamespacedKey.minecraft("extra_hearts"));
                    maxHealth.addModifier(
                            new AttributeModifier(NamespacedKey.minecraft("extra_hearts"), bonusHealth,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                }
                player.sendMessage(Component.translatable(capped ? "rush.extra_hearts_max" : "rush.extra_hearts"));
            }
        }
    }

    private void teleportToTeamSpawn(GameCombatant combattant, Team team) {
        Location spawn = team.getSpawnLocation();

        if (team.getBedLocation() != null && !team.isBedDestroyed()) {
            final Location bedLoc = team.getBedLocation();
            spawn = new Location(bedLoc.getWorld(), bedLoc.getX() + 0.5, bedLoc.getY() + 1, bedLoc.getZ() + 0.5);
        }

        if (spawn != null) {
            combattant.teleport(spawn);
        }
    }

    public void equipEntity(GameCombatant combatant, Team team) {
        equipWithTeamArmor(combatant.equipment(), team.getColor().getColor());
    }

    public static void equipWithTeamArmor(EntityEquipment equipment, Color color) {
        final ItemStack[] armorAndTool = createTeamArmorAndTool(color);
        equipment.setHelmet(armorAndTool[0]);
        equipment.setLeggings(armorAndTool[1]);
        equipment.setBoots(armorAndTool[2]);
        equipment.setItem(EquipmentSlot.HAND, armorAndTool[3]);
    }

    public static ItemStack[] createTeamArmorAndTool(Color color) {
        final ItemStack helmet = ItemBuilder.of(Material.LEATHER_HELMET)
                .color(color)
                .addEnchant(Enchantment.PROTECTION, 1, true)
                .build();
        final ItemStack leggings = ItemBuilder.of(Material.LEATHER_LEGGINGS)
                .color(color)
                .addEnchant(Enchantment.PROTECTION, 1, true)
                .build();
        final ItemStack boots = ItemBuilder.of(Material.LEATHER_BOOTS)
                .color(color)
                .addEnchant(Enchantment.PROTECTION, 1, true)
                .build();
        final ItemStack pickaxe = ItemBuilder.of(Material.WOODEN_PICKAXE)
                .name(i18n.txt("rush.item_pickaxe"))
                .addEnchant(Enchantment.EFFICIENCY, 1, true)
                .build();
        return new ItemStack[] { helmet, leggings, boots, pickaxe };
    }

    public void onPlayerDeath(GameCombatant victim, Player bukkitKiller) {
        final Team playerTeam = getPlayerTeam(victim);

        if (victim instanceof GamePlayer gp) {
            gp.player().getInventory().clear();
        } else {
            final EntityEquipment equipment = victim.equipment();
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

        if (victim instanceof GamePlayer gp) {
            final Player player = gp.player();
            player.setRespawnLocation(respawnLocation);

            final GameKillTracker.KillResult result = killTracker.resolveKill(player, bukkitKiller);
            killer = result.killer();
            assists = result.assists();

            final PlayerStatistic playerStat = getPlayerStatistic(player);
            playerStat.setCurrentDeaths(playerStat.getCurrentDeaths() + 1);
        }

        if (killer != null) {
            final PlayerStatistic killerStat = getPlayerStatistic(killer);
            killerStat.setCurrentKills(killerStat.getCurrentKills() + 1);
            killerStat.setCurrentScore(killerStat.getCurrentScore() + 10);

            for (Player assist : assists) {
                final PlayerStatistic assistStat = getPlayerStatistic(assist);
                assistStat.setCurrentAssists(assistStat.getCurrentAssists() + 1);
                assistStat.setCurrentScore(assistStat.getCurrentScore() + 5);
            }
        }

        final PlayerLevelManager levelManager = Main.getInstance().getPlayerLevelManager();
        if (levelManager != null && killer != null) {
            levelManager.addXP(killer.getUniqueId(), Math.round(15 * coefficient));
            for (Player assist : assists) {
                levelManager.addXP(assist.getUniqueId(), Math.round(8 * coefficient));
            }
        }

        broadcastKillMessage(victim, playerTeam, killer, assists);

        if (victim instanceof GamePlayer deathPlayer) {
            RushSounds.PLAYER_DIED.play(deathPlayer.player());
        }

        if (recorder != null && victim instanceof GamePlayer dp) {
            recorder.recordDeath(dp.uniqueId());
        }

        final boolean bedDestroyed = playerTeam != null && playerTeam.isBedDestroyed();

        if (bedDestroyed) {
            if (victim instanceof GamePlayer gp) {
                addSpectator(gp);
            } else {
                playerTeam.removePlayer(victim);
            }

            if (playerTeam != null) {
                if (playerTeam.getPlayers().isEmpty()) {
                    for (GameCombatant p : getPlayers()) {
                        if (p instanceof GamePlayer tp) {
                            RushSounds.TEAM_ELIMINATED.play(tp.player());
                        }
                    }
                } else {
                    for (GameCombatant p : playerTeam.getPlayers()) {
                        if (p instanceof GamePlayer tp) {
                            RushSounds.TEAMMATE_ELIMINATED.play(tp.player());
                        }
                    }
                }
            }

            checkGameOver();
        }
    }

    private void broadcastKillMessage(GameCombatant victim, Team victimTeam, Player killer, List<Player> assists) {
        TextColor victimColor = victimTeam != null
                ? victimTeam.getColor().getTextColor()
                : NamedTextColor.GRAY;

        if (killer == null) {
            broadcastMessage(Component.translatable("rush.kill_no_killer",
                    Component.text(victim.name()).color(victimColor)));
            return;
        }

        final Team killerTeam = getPlayerTeam(new GamePlayer(killer));
        final TextColor killerColor = killerTeam != null
                ? killerTeam.getColor().getTextColor()
                : NamedTextColor.GRAY;
        final List<Component> killerNames = new ArrayList<>();

        killerNames.add(Component.text(killer.getName()).color(killerColor));

        for (Player assist : assists) {
            killerNames.add(Component.text(assist.getName()).color(killerColor));
        }

        final Component killersDisplay = Component
                .join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)), killerNames);
        final TextComponent victimDisplay = Component.text(victim.name()).color(victimColor);
        broadcastMessage(Component.translatable("rush.kill_with_killer", victimDisplay, killersDisplay));
    }

    public void onBedDestroyed(Team team, Player destroyer) {
        team.setBedDestroyed(true);

        if (recorder != null) {
            recorder.recordBedDestroy(team.getColor().name(), destroyer != null ? destroyer.getUniqueId() : null);
        }

        String destroyerName = destroyer != null ? destroyer.getName() : "TNT";
        Team destroyerTeam = destroyer != null ? getPlayerTeam(new GamePlayer(destroyer)) : null;
        String destroyerTeamName = destroyerTeam != null ? destroyerTeam.getColor().name() : "";

        broadcastMessage(Component.translatable("rush.bed_destroyed_broadcast",
                Component.text(destroyerName), Component.text(destroyerTeamName),
                Component.text(team.getColor().name())));

        for (GameCombatant participant : getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                Player player = gp.player();
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            }
        }

        rewardDestroyer(destroyer);

        for (GameCombatant participant : team.getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                gp.player().sendMessage(Component.translatable("rush.bed_destroyed"));
            }
        }

        checkGameOver();
    }

    private void checkGameOver() {
        final List<Team> teamsWithBeds = teams.values().stream()
                .filter(t -> !t.getPlayers().isEmpty() && !t.isBedDestroyed())
                .collect(Collectors.toList());
        final List<Team> teamsWithoutBedsButAlive = teams.values().stream()
                .filter(t -> t.isBedDestroyed() && !t.getPlayers().isEmpty())
                .collect(Collectors.toList());

        if (teamsWithBeds.size() == 1 && teamsWithoutBedsButAlive.isEmpty()) {
            endGame(teamsWithBeds.get(0));
        } else if (teamsWithBeds.isEmpty()) {
            final List<Team> teamsWithPlayers = teams.values().stream()
                    .filter(t -> !t.getPlayers().isEmpty())
                    .collect(Collectors.toList());
            if (teamsWithPlayers.size() <= 1) {
                endGame(teamsWithPlayers.isEmpty() ? null : teamsWithPlayers.get(0));
            }
        }
    }

    private void endGame(Team winner) {
        state = GameState.STOPPED;

        if (winner != null) {
            for (GameCombatant participant : getPlayers()) {
                if (participant instanceof GamePlayer gp) {
                    gp.player().showTitle(Title.title(
                            Component.translatable("rush.win", Component.text(winner.getColor().name())),
                            Component.empty()));
                }
            }
            for (GamePlayer spectator : spectators) {
                spectator.player().showTitle(
                        Title.title(Component.translatable("rush.win", Component.text(winner.getColor().name())),
                                Component.empty()));
            }

            updateWinStreaks(winner);
        }

        if (overtimeMusicTask != null) {
            overtimeMusicTask.cancel();
            overtimeMusicTask = null;
        }

        for (GameCombatant participant : getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                final Player player = gp.player();
                RushSounds.WIN_CELEBRATE.play(player);
                RushSounds.GAME_END_MUSIC.play(player);
            }
        }
        for (GamePlayer spectator : spectators) {
            final Player player = spectator.player();
            RushSounds.WIN_CELEBRATE.play(player);
            RushSounds.GAME_END_MUSIC.play(player);
        }

        clearAllEnderChests();
        sendGameSummary();

        PlayerLevelManager endLevelManager = Main.getInstance().getPlayerLevelManager();
        if (endLevelManager != null) {
            for (Player player : playerStats.keySet()) {
                boolean won = winner != null && winner.equals(getPlayerTeam(new GamePlayer(player)));
                if (won) {
                    endLevelManager.addXP(player.getUniqueId(), Math.round(200 * coefficient));
                }
            }
        }

        for (Team team : teams.values()) {
            for (GameCombatant participant : team.getPlayers()) {
                switch (participant) {
                    case GamePlayer gp -> gp.player().setGameMode(GameMode.SPECTATOR);
                    case GameMannequin mm -> mm.remove();
                }
            }
        }

        cycle.onGameEnd();
        updateLeaderboardHolograms();

        ReplayFile pendingReplay = null;
        if (recorder != null) {
            List<String> participantNames = playerStats.keySet().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            Map<String, String> teamColorsByPlayerUuid = new HashMap<>();
            for (Map.Entry<UUID, Team.Color> entry : playerTeamColors.entrySet()) {
                teamColorsByPlayerUuid.put(entry.getKey().toString(), entry.getValue().name());
            }
            for (Team team : teams.values()) {
                for (GameCombatant participant : team.getPlayers()) {
                    if (participant instanceof GameMannequin) {
                        teamColorsByPlayerUuid.put(participant.uniqueId().toString(), team.getColor().name());
                    }
                }
            }
            pendingReplay = recorder.stop(gameRoom.getId(), winner != null ? winner.getColor().name() : null,
                    gameRoom.getHostName(), participantNames,
                    gameRoom.getConfig().mapType().name(),
                    gameRoom.getConfig().islandType().name(),
                    gameRoom.getConfig().maxTeams(),
                    gameRoom.getConfig().extraHearts(),
                    teamColorsByPlayerUuid);
            recorder = null;
        }
        Main.getInstance().getGameManager().onGameRoomEnded(gameRoom);
        if (pendingReplay != null) {
            sendArchivalPrompt(pendingReplay);
        }
    }

    private void sendArchivalPrompt(ReplayFile replayFile) {
        Player host = Bukkit.getPlayer(gameRoom.getHostUUID());
        if (host == null)
            return;

        Main.getInstance().getGameManager().storePendingArchive(gameRoom.getHostUUID(), replayFile);

        Component yes = Component.translatable("rush.archive_yes")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/rusharchive yes"));

        Component no = Component.translatable("rush.archive_no")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/rusharchive no"));

        host.sendMessage(Component.translatable("rush.archive_prompt")
                .color(NamedTextColor.YELLOW)
                .append(yes)
                .append(Component.space())
                .append(no));
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

        for (GameCombatant participant : getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                Player player = gp.player();
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
        for (GameCombatant participant : getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                gp.player().sendMessage(message);
            }
        }
        for (GamePlayer spectator : spectators) {
            spectator.player().sendMessage(message);
        }
    }

    public void forceStop() {
        state = GameState.STOPPED;

        runningTasks.forEach(BukkitTask::cancel);
        runningTasks.clear();
        stopGenerators();

        for (GameCombatant participant : getPlayers()) {
            removePlayer(participant);

            if (lobby != null) {
                participant.teleport(lobby);
            }

            participant.equipment().clear();
            participant.equipment().setArmorContents(null);

            if (participant instanceof GamePlayer gp) {
                Player player = gp.player();
                player.setGameMode(GameMode.ADVENTURE);
                resetPlayerHealth(player);
                player.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());
            }
        }

        for (GamePlayer spectator : new ArrayList<>(spectators)) {
            removeSpectator(spectator);
            Player player = spectator.player();

            if (lobby != null) {
                player.teleport(lobby);
            }

            player.getInventory().setItem(0, TeamSelectionGUI.createBannerItem());
        }

        for (Team team : teams.values()) {
            team.reset();
        }

        clearAllEnderChests();
        clearGameState();

        cycle.onGameEnd();

        state = GameState.WAITING;
    }

    private void startCompassTracker() {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            List<GameCompassTracker.Candidate> candidates = teams.values().stream()
                    .flatMap(t -> t.getPlayers().stream()
                            .filter(GamePlayer.class::isInstance)
                            .map(e -> {
                                Player p = ((GamePlayer) e).player();
                                return new GameCompassTracker.Candidate(
                                        UUID.nameUUIDFromBytes(t.getColor().name().getBytes()),
                                        p.getLocation().getX(),
                                        p.getLocation().getY(),
                                        p.getLocation().getZ());
                            }))
                    .toList();

            for (Team team : teams.values()) {
                UUID teamId = UUID.nameUUIDFromBytes(team.getColor().name().getBytes());
                for (GameCombatant participant : team.getPlayers()) {
                    if (!(participant instanceof GamePlayer gp))
                        continue;
                    Player holder = gp.player();
                    if (holder.getInventory().getItemInMainHand().getType() != Material.COMPASS)
                        continue;

                    GameCompassTracker.Candidate nearest = GameCompassTracker.findNearestEnemy(
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

    private void startMannequinVoidCheck() {
        final int islandY = gameRoom.getIslandY();
        final double voidThreshold = islandY - Main.getInstance().getVoidThreshold();

        final BukkitTask task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (Team team : teams.values()) {
                for (GameCombatant participant : new ArrayList<>(team.getPlayers())) {
                    if (!(participant instanceof GameMannequin mm) || mm.dead())
                        continue;
                    if (mm.location().getY() < voidThreshold) {
                        mm.setFallDistance(0);
                        handleMannequinDeath(mm.mannequin(), null);
                    }
                }
            }
        }, 0L, 2L);

        runningTasks.add(task);
    }

    public void handleMannequinDeath(Mannequin mannequin, Player killer) {
        if (!mannequinsBeingRescued.add(mannequin.getUniqueId())) {
            return;
        }

        GameMannequin gm = new GameMannequin(mannequin);
        mannequin.setHealth(mannequin.getAttribute(Attribute.MAX_HEALTH).getValue());
        onPlayerDeath(gm, killer);

        final Team team = getPlayerTeam(gm);
        if (team == null || team.isBedDestroyed()) {
            mannequinsBeingRescued.remove(mannequin.getUniqueId());
            return;
        }

        final Location bedLoc = team.getBedLocation();
        final Location spawn = bedLoc != null
                ? new Location(bedLoc.getWorld(), bedLoc.getX() + 0.5, bedLoc.getY() + 1, bedLoc.getZ() + 0.5)
                : team.getSpawnLocation();

        if (spawn == null) {
            mannequinsBeingRescued.remove(mannequin.getUniqueId());
            return;
        }

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            mannequinsBeingRescued.remove(mannequin.getUniqueId());
            if (mannequin.isDead())
                return;
            mannequin.teleport(spawn);
            equipEntity(gm, team);
            addMannequinProtection(mannequin);
        }, 1L);
    }

    private void addMannequinProtection(Mannequin mannequin) {
        final int protectionTicks = Main.getInstance().getRespawnProtectionTime() * 20;
        if (protectionTicks <= 0)
            return;

        mannequin.setInvulnerable(true);
        mannequin.setVisibleByDefault(true);

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (mannequin.isDead())
                return;
            mannequin.setInvulnerable(false);
            mannequin.setVisibleByDefault(true);
        }, protectionTicks);
    }

    private void startGenerators() {
        final int islandY = gameRoom.getIslandY();
        final World gameWorld = gameRoom.getWorld();

        for (int i = 0; i < islandAssignment.size(); i++) {
            Team team = islandAssignment.get(i);

            List<Location> chestLocations;
            if (team != null) {
                team.placeEnderChests(i);
                chestLocations = team.getEnderChestLocations();
            } else if (islands != null && i < islands.size() && gameWorld != null) {
                Island island = islands.get(i);
                int[] dir = Island.Layout.ISLAND_DIRECTIONS[i];
                int perpX = dir[1];
                chestLocations = GameManager.placeIslandEnderChests(
                        gameWorld,
                        island.getX(), island.getZ(),
                        islandY,
                        dir, perpX, 12,
                        Team.facingTowardsCenter(i), 2);
            } else {
                continue;
            }

            for (Location chestLocation : chestLocations) {
                for (Generator.Type type : Generator.Type.values()) {
                    Generator spawner = new Generator(this, type, chestLocation);
                    generators.add(spawner);

                    BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                            Main.getInstance(),
                            spawner,
                            spawner.getType().getSpawnIntervalTicks(),
                            spawner.getType().getSpawnIntervalTicks());
                    spawnerTasks.add(task);
                }
            }
        }
    }

    private void stopGenerators() {
        spawnerTasks.forEach(BukkitTask::cancel);
        spawnerTasks.clear();
        generators.clear();
    }

    private void updateActionBar() {
        if (gameRoom != null) {
            GameRoom.sendReadyActionBar(gameRoom);
        }
    }

    private void updatePlayerList() {
        for (GameCombatant participant : freePlayers) {
            if (participant instanceof GamePlayer gp) {
                final Boolean isReady = playersReady.get(participant);
                gp.player().playerListName(
                        Component.text(participant.name()).color(isReady ? NamedTextColor.GREEN : NamedTextColor.RED));
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

    public List<GameCombatant> getPlayers() {
        final List<GameCombatant> allPlayers = new ArrayList<>(freePlayers);
        for (Team team : teams.values()) {
            allPlayers.addAll(team.getPlayers());
        }
        return allPlayers;
    }

    public Team getPlayerTeam(GameCombatant participant) {
        for (Team team : teams.values()) {
            if (team.isInTeam(participant)) {
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
        for (GameCombatant participant : getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                Player player = gp.player();
                final PlayerStatistic stats = getPlayerStatistic(player);
                final Team playerTeam = getPlayerTeam(new GamePlayer(player));

                if (playerTeam != null && playerTeam.equals(winner)) {
                    stats.setWinStreak(stats.getWinStreak() + 1);
                } else {
                    stats.setWinStreak(0);
                }
            }
        }
    }

    /**
     * Removes all items from ender chests of all game participants and spectators.
     */
    private void clearAllEnderChests() {
        for (GameCombatant p : getPlayers())
            if (p instanceof GamePlayer gp)
                gp.player().getEnderChest().clear();
        for (GamePlayer sp : spectators)
            sp.player().getEnderChest().clear();
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

        stopGenerators();

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
