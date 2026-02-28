package io.github.rush.game;

import io.github.rush.Main;
import io.github.rush.statistics.PlayerStatistic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class Game {

    private final String name;
    private GameState state;
    private GameCycle cycle;
    private Location lobby;

    private final List<BukkitTask> runningTasks = new ArrayList<>();
    private final List<BukkitTask> spawnerTasks = new ArrayList<>();
    private final List<ResourceSpawner> resourceSpawners = new ArrayList<>();

    private final Map<String, Team> teams = new HashMap<>();
    private final List<Player> freePlayers = new ArrayList<>();
    private final Map<Player, PlayerStatistic> playerStats = new HashMap<>();

    private int timeLeft = 0;
    private final int maxPlayers = 8;
    private final int minPlayers = 2;
    private final int minTeams = 2;

    private GameLobbyCountdown lobbyCountdown;
    private final Map<Player, Boolean> playerReady = new HashMap<>();

    public Game(String name) {
        this.name = name;
        this.state = GameState.WAITING;
        this.cycle = new GameCycle(this);
        this.timeLeft = Main.getInstance().getMaxLength();

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

    public boolean addPlayer(Player player) {
        if (state == GameState.RUNNING) {
            return false;
        }

        playerReady.put(player, false);
        freePlayers.add(player);

        giveLobbyItems(player);
        updatePlayerList();

        return true;
    }

    public void joinTeam(Player player, TeamColor color) {
        Team team = teams.get(color.name());
        if (team == null) {
            return;
        }

        for (Team existingTeam : teams.values()) {
            if (existingTeam.isInTeam(player)) {
                existingTeam.removePlayer(player);
                break;
            }
        }

        team.addPlayer(player);
        if (freePlayers.contains(player)) {
            freePlayers.remove(player);
        }

        updatePlayerList();
    }

    public void leaveTeam(Player player) {
        for (Team team : teams.values()) {
            if (team.isInTeam(player)) {
                team.removePlayer(player);
                break;
            }
        }

        if (!freePlayers.contains(player)) {
            freePlayers.add(player);
        }

        playerReady.put(player, false);
        updatePlayerList();
    }

    public boolean isPlayerReady(Player player) {
        Boolean ready = playerReady.get(player);
        return ready != null && ready;
    }

    public void removePlayer(Player player) {
        freePlayers.remove(player);
        playerReady.remove(player);

        for (Team team : teams.values()) {
            if (team.isInTeam(player)) {
                team.removePlayer(player);
                break;
            }
        }

        updatePlayerList();
    }

    public void setPlayerReady(Player player, boolean ready) {
        playerReady.put(player, ready);
        checkStartCondition();
    }

    private void giveLobbyItems(Player player) {
        player.getInventory().clear();

        ItemStack redWool = new ItemStack(Material.RED_WOOL);
        ItemMeta redMeta = redWool.getItemMeta();
        redMeta.displayName(Component.text("Prêt").color(NamedTextColor.RED));
        redWool.setItemMeta(redMeta);

        ItemStack greenWool = new ItemStack(Material.GREEN_WOOL);
        ItemMeta greenMeta = greenWool.getItemMeta();
        greenMeta.displayName(Component.text("Pas prêt").color(NamedTextColor.GREEN));
        greenWool.setItemMeta(greenMeta);

        player.getInventory().setItem(0, redWool);
        player.getInventory().setItem(1, greenWool);
    }

    public void checkStartCondition() {
        if (state != GameState.WAITING) {
            return;
        }

        long readyCount = playerReady.values().stream().filter(r -> r).count();

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

    public void autoStart() {
        if (state != GameState.WAITING) {
            return;
        }

        List<Player> unassigned = freePlayers.stream()
                .filter(p -> teams.values().stream().noneMatch(t -> t.isInTeam(p)))
                .collect(Collectors.toList());

        if (unassigned.isEmpty()) {
            return;
        }

        List<Team> sortedTeams = teams.values().stream()
                .sorted(Comparator.comparingInt(t -> t.getPlayers().size()))
                .collect(Collectors.toList());

        for (Player player : unassigned) {
            Team smallestTeam = sortedTeams.get(0);
            if (smallestTeam.getPlayers().size() < smallestTeam.getMaxPlayers()) {
                smallestTeam.addPlayer(player);
            }
        }

        checkStartCondition();
    }

    public void start() {
        if (state != GameState.WAITING) {
            return;
        }

        state = GameState.RUNNING;

        for (Team team : teams.values()) {
            if (!team.getPlayers().isEmpty()) {
                team.placeBed();
                team.spawnVillagers();
            }
        }

        for (Player player : getPlayers()) {
            Team team = getPlayerTeam(player);
            if (team != null) {
                teleportToTeamSpawn(player, team);
                equipPlayer(player);
            }
        }

        startResourceSpawners();
        cycle.onGameStart();
    }

    private void teleportToTeamSpawn(Player player, Team team) {
        Location spawn = team.getSpawnLocation();
        if (spawn != null) {
            player.teleport(spawn);
        }
    }

    private void equipPlayer(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null)
            return;

        org.bukkit.Color color = team.getColor().getColor();

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

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
        player.getInventory().setItem(0, pickaxe);

        player.updateInventory();
    }

    public void onPlayerDeath(Player player, Player killer) {
        Team playerTeam = getPlayerTeam(player);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setRespawnLocation(playerTeam != null ? playerTeam.getSpawnLocation() : lobby);

        if (killer != null) {
            PlayerStatistic killerStat = playerStats.get(killer);
            if (killerStat != null) {
                killerStat.setCurrentKills(killerStat.getCurrentKills() + 1);
                killerStat.setCurrentScore(killerStat.getCurrentScore() + 10);
            }
        }

        PlayerStatistic playerStat = playerStats.get(player);
        if (playerStat != null) {
            playerStat.setCurrentDeaths(playerStat.getCurrentDeaths() + 1);
        }
    }

    public void onBedDestroyed(Team team) {
        team.setBedDestroyed(true);

        for (Player player : team.getPlayers()) {
            player.sendMessage(Component.text("§cVotre lit a été détruit!"));
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

        if (winner != null) {
            String winMessage = "Victoire de l'équipe " + winner.getColor().name();
            for (Player player : getPlayers()) {
                player.showTitle(net.kyori.adventure.title.Title.title(
                        Component.text(winMessage),
                        Component.empty()));
            }
        }

        cycle.onGameEnd();

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), this::resetGame, 400L);
    }

    private void resetGame() {
        for (Player player : getPlayers()) {
            removePlayer(player);
            if (lobby != null) {
                player.teleport(lobby);
            }
        }

        for (Team team : teams.values()) {
            team.reset();
        }

        freePlayers.clear();
        playerReady.clear();
        playerStats.clear();
        runningTasks.forEach(BukkitTask::cancel);
        runningTasks.clear();
        stopResourceSpawners();

        state = GameState.WAITING;
    }

    private void startResourceSpawners() {
        for (Team team : teams.values()) {
            if (team.getPlayers().isEmpty()) {
                continue;
            }

            team.placeEnderChests();

            for (Location chestLocation : team.getEnderChestLocations()) {
                for (ResourceType type : ResourceType.values()) {
                    ResourceSpawner spawner = new ResourceSpawner(this, type, chestLocation);
                    resourceSpawners.add(spawner);

                    BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                            Main.getInstance(),
                            spawner,
                            spawner.getIntervalTicks(),
                            spawner.getIntervalTicks());
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
        String gameWorld = Main.getInstance().getGameWorld();
        if (gameWorld == null)
            return;

        long readyCount = playerReady.values().stream().filter(r -> r).count();
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
        for (Player player : getPlayers()) {
            player.sendMessage(Component.text(message));
        }
    }

    private void updatePlayerList() {
        for (Player player : freePlayers) {
            Boolean ready = playerReady.get(player);
            boolean isReady = ready != null && ready;
            player.playerListName(
                    Component.text(player.getName()).color(isReady ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
    }

    public int getTeamCount() {
        return (int) teams.values().stream()
                .filter(t -> !t.getPlayers().isEmpty())
                .count();
    }

    public List<Player> getPlayers() {
        List<Player> allPlayers = new ArrayList<>(freePlayers);
        for (Team team : teams.values()) {
            allPlayers.addAll(team.getPlayers());
        }
        return allPlayers;
    }

    public Team getPlayerTeam(Player player) {
        for (Team team : teams.values()) {
            if (team.isInTeam(player)) {
                return team;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public Location getLobby() {
        return lobby;
    }

    public void setLobby(Location lobby) {
        this.lobby = lobby;
    }

    public GameCycle getCycle() {
        return cycle;
    }

    public Map<String, Team> getTeams() {
        return teams;
    }

    public Team getTeam(String name) {
        return teams.get(name);
    }

    public List<Player> getFreePlayers() {
        return freePlayers;
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
