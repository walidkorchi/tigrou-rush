package io.github.rush.game;

import io.github.rush.Main;
import io.github.rush.objects.Island;
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
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
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
    private int timeLeft = 0;
    @Getter
    private final int maxPlayers = 8;
    @Getter
    private final int minPlayers = 2;
    @Getter
    private final int minTeams = 2;

    private GameLobbyCountdown lobbyCountdown;
    private final Map<Entity, Boolean> playersReady = new HashMap<>();

    public Game(String name) {
        this.state = GameState.WAITING;
        this.cycle = new GameCycle(this);
        this.timeLeft = Main.getInstance().getMaxLength();

        String lobbyWorld = Main.getInstance().getConfig().getString("lobbyWorld");
        World world = Bukkit.getWorld(lobbyWorld);

        this.lobby = world != null ? world.getSpawnLocation() : null;

        initializeTeams();
    }

    private void initializeTeams() {
        List<TeamColor> colors = List.of(
                TeamColor.RED, TeamColor.BLUE, TeamColor.GREEN, TeamColor.YELLOW);

        for (TeamColor color : colors) {
            Team team = new Team(color.name(), color, 4);
            teams.put(color.name(), team);
        }
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

        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);

        player.getInventory().clear();
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.displayName(Component.text("§cQuitter le spectator"));
        compass.setItemMeta(meta);
        player.getInventory().setItem(0, compass);

        for (Player online : Bukkit.getOnlinePlayers()) {
            player.hidePlayer(Main.getInstance(), online);
        }

        Location spawn = Main.getInstance().getSpectatorSpawn();
        if (spawn != null) {
            player.teleport(spawn);
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

        for (Player online : Bukkit.getOnlinePlayers()) {
            player.showPlayer(Main.getInstance(), online);
        }

        player.getInventory().clear();

        Location lobbyLoc = Main.getInstance().getMainLobby();
        if (lobbyLoc != null) {
            player.teleport(lobbyLoc);
        }

        player.sendMessage(Component.text("§aVous avez quitté le mode spectateur."));
    }

    public boolean isProtected(Player player) {
        return protectedPlayers.contains(player);
    }

    public void addProtection(Player player) {
        int protectionTime = Main.getInstance().getRespawnProtectionTime();
        if (protectionTime <= 0)
            return;

        protectedPlayers.add(player);

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld().equals(player.getWorld())) {
                online.hidePlayer(Main.getInstance(), player);
                player.hidePlayer(Main.getInstance(), online);
            }
        }

        final int remainingTime = protectionTime;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (!protectedPlayers.contains(player)) {
                return;
            }

            int currentTime = Main.getInstance().getRespawnProtectionTime() - remainingTime + 1;
            if (currentTime > 0 && currentTime <= protectionTime) {
                player.sendActionBar(Component.text("§aProtection: " + currentTime + "s"));
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

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld().equals(player.getWorld())) {
                online.showPlayer(Main.getInstance(), player);
                player.showPlayer(Main.getInstance(), online);
            }
        }
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
            long readyCount = getPlayersReadyCount();

            if (readyCount >= minPlayers && getTeamCount() >= minTeams) {
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

    public void start() {
        if (state == GameState.WAITING) {
            state = GameState.RUNNING;
            Main.getInstance().setGameStarted(true);

            loadIslandsAndSetSpawns();

            final TeamColor[] teamOrder = TeamColor.firstN(4);

            for (int i = 0; i < teamOrder.length; i++) {
                final Team team = teams.get(teamOrder[i].name());

                if (team != null) {
                    team.placeBed(i);
                }
            }

            for (Entity entity : getPlayers()) {
                final Team team = getPlayerTeam(entity);

                if (team != null) {
                    if (entity instanceof Player player) {
                        player.getInventory().clear();
                        player.setGameMode(GameMode.SURVIVAL);
                    }

                    teleportToTeamSpawn(entity, team);
                    equipEntity(entity, team);
                }
            }

            startResourceSpawners();

            cycle.onGameStart();
        }
    }

    private void loadIslandsAndSetSpawns() {
        Main plugin = Main.getInstance();
        World gameWorld = Bukkit.getWorld(plugin.getGameWorld());

        if (gameWorld == null) {
            plugin.getLogger().warning("Game world not found, cannot load islands");
            return;
        }

        if (!plugin.isIslandsLoaded()) {
            plugin.loadSchematicsSync();
        }

        final List<Island> islands = plugin.getIslands();
        final TeamColor[] teamOrder = TeamColor.firstN(4);

        for (int i = 0; i < teamOrder.length && i < islands.size(); i++) {
            final Team team = teams.get(teamOrder[i].name());

            if (team == null)
                continue;

            final Island island = islands.get(i);
            final Location spawnLoc = new Location(gameWorld, island.getX(), Main.getISLAND_Y() + 2, island.getZ());

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
        equipment.setLeggings(armorAndTool[2]);
        equipment.setBoots(armorAndTool[3]);
        equipment.setItem(EquipmentSlot.HAND, armorAndTool[4]);
    }

    private ItemStack[] createTeamArmorAndTool(Color color) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta helmetMeta = (LeatherArmorMeta) helmet.getItemMeta();
        helmetMeta.setColor(color);
        helmet.setItemMeta(helmetMeta);

        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chestplate.getItemMeta();
        chestMeta.setColor(color);
        chestplate.setItemMeta(chestMeta);

        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta legsMeta = (LeatherArmorMeta) leggings.getItemMeta();
        legsMeta.setColor(color);
        leggings.setItemMeta(legsMeta);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bootsMeta = (LeatherArmorMeta) boots.getItemMeta();
        bootsMeta.setColor(color);
        boots.setItemMeta(bootsMeta);

        ItemStack pickaxe = new ItemStack(Material.WOODEN_PICKAXE);
        ItemMeta pickMeta = pickaxe.getItemMeta();
        pickMeta.displayName(Component.text("Pickaxe"));
        pickaxe.setItemMeta(pickMeta);

        return new ItemStack[] { helmet, chestplate, leggings, boots, pickaxe };
    }

    public void onPlayerDeath(Entity entity, Player killer) {
        final Team playerTeam = getPlayerTeam(entity);
        final EntityEquipment inventory = getPlayerInventory(entity);

        inventory.clear();
        inventory.setArmorContents(null);

        Location respawnLocation = lobby;

        if (playerTeam != null && playerTeam.getBedLocation() != null && !playerTeam.isBedDestroyed()) {
            respawnLocation = playerTeam.getBedLocation();
        } else if (playerTeam != null) {
            respawnLocation = playerTeam.getSpawnLocation();
        }

        if (entity instanceof Player player) {
            player.setRespawnLocation(respawnLocation);

            if (killer != null) {
                PlayerStatistic killerStat = playerStats.get(killer);
                if (killerStat != null) {
                    killerStat.setCurrentKills(killerStat.getCurrentKills() + 1);
                    killerStat.setCurrentScore(killerStat.getCurrentScore() + 10);
                }
            }

            PlayerStatistic playerStat = playerStats.get(entity);
            if (playerStat != null) {
                playerStat.setCurrentDeaths(playerStat.getCurrentDeaths() + 1);
            }
        }
    }

    public void onBedDestroyed(Team team, Player destroyer) {
        team.setBedDestroyed(true);

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

        if (team.getPlayers().isEmpty() && destroyerTeam != null) {
            for (Entity entity : destroyerTeam.getPlayers()) {
                if (entity instanceof Player player) {
                    player.getAttribute(Attribute.MAX_HEALTH).addModifier(
                            new AttributeModifier(NamespacedKey.minecraft("extra_hearts"), 4.0,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                    player.sendMessage(Component.text("§a+2 Cœurs permanents!"));
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

        if (teamsWithBeds.size() <= 1) {
            endGame(teamsWithBeds.isEmpty() ? null : teamsWithBeds.get(0));
        }
    }

    private void endGame(Team winner) {
        state = GameState.STOPPED;
        Main.getInstance().setGameStarted(false);

        if (winner != null) {
            String winMessage = "Victoire de l'équipe " + winner.getColor().name();
            for (Entity player : getPlayers()) {
                if (player instanceof Player) {
                    player.showTitle(Title.title(Component.text(winMessage), Component.empty()));
                }
            }
        }

        cycle.onGameEnd();

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), this::resetGame, 400L);
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
            if (entity instanceof Player p) {
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);
                resetPlayerHealth(p);
            }
        }

        for (Player spectator : new ArrayList<>(spectators)) {
            removeSpectator(spectator);
            if (lobby != null) {
                spectator.teleport(lobby);
            }
        }

        for (Team team : teams.values()) {
            team.reset();
        }

        freePlayers.clear();
        playersReady.clear();
        playerStats.clear();
        spectators.clear();
        protectedPlayers.clear();

        cycle.onGameEnd();
    }

    private void resetGame() {
        for (Entity entity : getPlayers()) {
            removePlayer(entity);

            if (lobby != null) {
                entity.teleport(lobby);
            }

            if (entity instanceof Player p) {
                resetPlayerHealth(p);
            }
        }

        for (Team team : teams.values()) {
            team.reset();
        }

        freePlayers.clear();
        playersReady.clear();
        playerStats.clear();
        runningTasks.forEach(BukkitTask::cancel);
        runningTasks.clear();

        stopResourceSpawners();

        state = GameState.WAITING;
    }

    private void startResourceSpawners() {
        TeamColor[] teamOrder = TeamColor.firstN(4);

        for (int i = 0; i < teamOrder.length; i++) {
            Team team = teams.get(teamOrder[i].name());

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
        NamedTextColor color = readyCount >= minPlayers ? NamedTextColor.GREEN : NamedTextColor.RED;
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
}
