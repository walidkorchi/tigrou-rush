package io.github.rush.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.material.Bed;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

import com.google.common.collect.ImmutableMap;

import io.github.rush.Main;
import io.github.rush.game.GameCycle;
import io.github.rush.statistics.PlayerStatistic;
import io.github.rush.utils.ChatWriter;
import io.github.rush.utils.Utils;
import lombok.Data;

@Data
public class Game {

    private String name = null;
    private GameState state = null;
    private GameCycle cycle = null;
    private YamlConfiguration config = null;
    private Scoreboard scoreboard = null;
    private GameLobbyCountdown gameLobbyCountdown = null;

    private boolean isOver = false;
    private boolean isStopping = false;
    private List<BukkitTask> runningTasks = null;

    private HashMap<String, Team> teams = null;
    private List<Player> freePlayers = null;
    private List<Team> playingTeams = null;
    private Map<Player, Player> playerDamages = null;
    private Map<Player, PlayerSettings> playerSettings = null;
    private List<ResourceSpawner> resourceSpawners = null;
    private HashMap<Player, PlayerStorage> playerStorages = null;
    private Map<Player, RespawnProtectionRunnable> respawnProtections = null;

    private int time = 1000;
    private int length = 0;
    private int timeLeft = 0;
    private int minPlayers = 0;
    private Location lobby = null;

    public Game(String name) {
        super();

        this.name = name;
        this.runningTasks = new ArrayList<BukkitTask>();

        this.freePlayers = new ArrayList<Player>();
        this.resourceSpawners = new ArrayList<ResourceSpawner>();
        this.teams = new HashMap<String, Team>();
        this.playingTeams = new ArrayList<Team>();

        this.state = GameState.STOPPED;

        this.timeLeft = Main.getInstance().getMaxLength();
        this.isOver = false;

        this.length = Main.getInstance().getMaxLength();

        this.cycle = new GameCycle(this);
    }

    public boolean start(CommandSender sender) {
        if (this.state != GameState.WAITING) {
            sender.sendMessage(
                    ChatWriter
                            .pluginMessage(ChatColor.RED + "Cannot start game - not in waiting state"));
            return false;
        }

        // BedwarsGameStartEvent startEvent = new BedwarsGameStartEvent(this);
        // Main.getInstance().getServer().getPluginManager().callEvent(startEvent);

        // if (startEvent.isCancelled()) {
        // return false;
        // }

        this.isOver = false;
        for (Player aPlayer : this.getPlayers()) {
            if (aPlayer.isOnline()) {
                aPlayer.sendMessage(
                        ChatWriter
                                .pluginMessage(ChatColor.GREEN + "Game starting!"));
            }
        }

        // load shop categories again (if shop was changed)
        this.loadItemShopCategories();
        this.runningTasks.clear();
        this.cleanUsersInventory();
        this.clearProtections();
        this.moveFreePlayersToTeam();
        this.makeTeamsReady();

        this.cycle.onGameStart();
        this.startResourceSpawners();

        // Update world time before game starts
        // this.getRegion().getWorld().setTime(this.time);

        this.teleportPlayersToTeamSpawn();

        this.state = GameState.RUNNING;

        for (Player player : this.getPlayers()) {
            this.setPlayerGameMode(player);
            this.setPlayerVisibility(player);
        }

        this.updateScoreboard();
        this.displayRecord();
        this.startTimerCountdown();
        this.updateSigns();

        if (Main.getInstance().getConfig().getBoolean("global-messages", true)) {
            for (Player aPlayer : Main.getInstance().getServer().getOnlinePlayers()) {
                aPlayer.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN
                        + "Game started!"));
            }
            Main.getInstance().getServer().getConsoleSender()
                    .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN
                            + "Game started!"));
        }

        // BedwarsGameStartedEvent startedEvent = new BedwarsGameStartedEvent(this);
        // Main.getInstance().getServer().getPluginManager().callEvent(startedEvent);

        return true;
    }

    private void teleportPlayersToTeamSpawn() {
        for (Team team : this.teams.values()) {
            for (Player player : team.getPlayers()) {
                if (!player.getWorld().equals(team.getSpawnLocation().getWorld())) {
                    this.getPlayerSettings(player).setTeleporting(true);
                }

                player.setVelocity(new Vector(0, 0, 0));
                player.setFallDistance(0.0F);
                player.teleport(team.getSpawnLocation());
                if (this.getPlayerStorage(player) != null) {
                    this.getPlayerStorage(player).clean();
                }
            }
        }
    }

    public boolean playerLeave(Player p, boolean kicked) {
        this.getPlayerSettings(p).setTeleporting(true);

        Team team = this.getPlayerTeam(p);
        PlayerStatistic statistic = Main.getInstance().getPlayerStatisticManager().getStatistic(p);

        if (this.isSpectator(p)) {
            if (!this.getCycle().isEndGameRunning()) {
                for (Player player : this.getPlayers()) {
                    if (player.equals(p)) {
                        continue;
                    }

                    player.showPlayer(Main.getInstance(), p);
                    p.showPlayer(Main.getInstance(), player);
                }
            }
        } else {
            if (this.state == GameState.RUNNING && !this.getCycle().isEndGameRunning()) {
                // player gets killed after leaving
                if (!team.isDead(this) && !p.isDead()) {
                    statistic.setCurrentDeaths(statistic.getCurrentDeaths() + 1);
                    statistic.setCurrentScore(statistic.getCurrentScore() + Main.getInstance()
                            .getConfig().getInt("statistics.scores.die", 0));
                    if (this.getPlayerDamager(p) != null) {
                        PlayerStatistic killerPlayer = Main.getInstance().getPlayerStatisticManager()
                                .getStatistic(this.getPlayerDamager(p));
                        killerPlayer.setCurrentKills(killerPlayer.getCurrentKills() + 1);
                        killerPlayer.setCurrentScore(killerPlayer.getCurrentScore() + Main.getInstance()
                                .getConfig().getInt("statistics.scores.kill", 10));
                    }
                    statistic.setCurrentLoses(statistic.getCurrentLoses() + 1);
                    statistic.setCurrentScore(statistic.getCurrentScore() + Main.getInstance()
                            .getConfig().getInt("statistics.scores.lose", 0));
                }
            }
        }

        if (this.isProtected(p)) {
            this.removeProtection(p);
        }

        this.playerDamages.remove(p);
        if (team != null && Main.getInstance().getGameManager().getGameOfPlayer(p) != null
                && !Main.getInstance().getGameManager().getGameOfPlayer(p).isSpectator(p)) {
            if (kicked) {
                for (Player aPlayer : this.getPlayers()) {
                    if (aPlayer.isOnline()) {
                        aPlayer.sendMessage(
                                ChatWriter.pluginMessage(ChatColor.RED + Main
                                        ._l(aPlayer, "ingame.player.kicked", ImmutableMap.of("player",
                                                Game.getPlayerWithTeamString(p, team, ChatColor.RED)
                                                        + ChatColor.RED))));
                    }
                }
            } else {
                for (Player aPlayer : this.getPlayers()) {
                    if (aPlayer.isOnline()) {
                        aPlayer.sendMessage(
                                ChatWriter.pluginMessage(
                                        ChatColor.RED + Main
                                                ._l(aPlayer, "ingame.player.left", ImmutableMap.of("player",
                                                        Game.getPlayerWithTeamString(p, team, ChatColor.RED)
                                                                + ChatColor.RED))));
                    }
                }
            }
            team.removePlayer(p);
        }

        Main.getInstance().getGameManager().removeGamePlayer(p);

        if (this.freePlayers.contains(p)) {
            this.freePlayers.remove(p);
        }

        if (Main.getInstance().getHolographicInteractor() != null && Main.getInstance()
                .getHolographicInteractor().getType().equalsIgnoreCase("HolographicDisplays")) {
            Main.getInstance().getHolographicInteractor().updateHolograms(p);
        }

        if (Main.getInstance().getConfig().getBoolean("statistics.show-on-game-end", true)) {
            Main.getInstance().getServer().dispatchCommand(p, "bw stats");
        }

        Main.getInstance().getPlayerStatisticManager().storeStatistic(statistic);
        // Main.getInstance().getPlayerStatisticManager().unloadStatistic(p);

        PlayerStorage storage = this.playerStorages.get(p);
        storage.clean();
        storage.restore();

        this.playerSettings.remove(p);
        this.updateScoreboard();

        try {
            p.setScoreboard(Main.getInstance().getScoreboardManager().getMainScoreboard());
        } catch (Exception e) {
            Main.getInstance().getBugsnag().notify(e);
        }

        this.removeNewItemShop(p);

        if (p.isOnline()) {
            if (kicked) {
                p.sendMessage(
                        ChatWriter.pluginMessage(ChatColor.RED + "You were kicked from the game"));
            } else {
                p.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "You left the game"));
            }
        }

        this.cycle.onPlayerLeave(p);
        this.updateSigns();
        this.playerStorages.remove(p);
        return true;
    }

    public void clearProtections() {
        for (RespawnProtectionRunnable protection : this.respawnProtections.values()) {
            try {
                protection.cancel();
            } catch (Exception ex) {
                Main.getInstance().getBugsnag().notify(ex);
            }
        }

        this.respawnProtections.clear();
    }

    public void addPlayerSettings(Player player) {
        this.playerSettings.put(player, new PlayerSettings(player));
    }

    public static String getPlayerWithTeamString(Player player, Team team, ChatColor before) {
        if (Main.getInstance().getBooleanConfig("teamname-in-chat", true)) {
            return player.getDisplayName() + before + " (" + team.getChatColor() + team.getDisplayName()
                    + before + ")";
        }

        return player.getDisplayName() + before;
    }

    public boolean playerJoins(final Player p) {
        if (this.state == GameState.STOPPED) {
            return false;
        }

        // BedwarsPlayerJoinEvent joiningEvent = new BedwarsPlayerJoinEvent(this, p);
        // Main.getInstance().getServer().getPluginManager().callEvent(joiningEvent);

        Main.getInstance().getGameManager().addGamePlayer(p, this);
        Main.getInstance().getPlayerStatisticManager().getStatistic(p);

        // add damager and set it to null
        this.playerDamages.put(p, null);

        // add player settings
        this.addPlayerSettings(p);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player playerInGame : Game.this.getPlayers()) {
                    playerInGame.hidePlayer(Main.getInstance(), p);
                    p.hidePlayer(Main.getInstance(), playerInGame);
                }
            }

        }.runTaskLater(Main.getInstance(), 5L);

        if (this.state == GameState.RUNNING) {
            this.toSpectator(p);
            this.displayMapInfo(p);
        } else {
            PlayerStorage storage = this.addPlayerStorage(p);

            storage.store();
            storage.clean();

            final Location location = this.getPlayerTeleportLocation(p);

            if (!p.getLocation().equals(location)) {
                this.getPlayerSettings(p).setTeleporting(true);
                p.teleport(location);
            }

            storage.loadLobbyInventory(this);

            new BukkitRunnable() {
                @Override
                public void run() {
                    Game.this.setPlayerGameMode(p);
                    Game.this.setPlayerVisibility(p);
                }
            }.runTaskLater(Main.getInstance(), 15L);

            for (Player aPlayer : this.getPlayers()) {
                if (aPlayer.isOnline()) {
                    aPlayer.sendMessage(
                            ChatWriter.pluginMessage(ChatColor.GREEN + p.getDisplayName() + " joined the game"));
                }
            }

            this.freePlayers.add(p); // auto-balance teams players
            this.displayRecord(p);

            if (this.isStartable()) {
                if (this.gameLobbyCountdown == null) {
                    this.gameLobbyCountdown = new GameLobbyCountdown(this);
                    this.gameLobbyCountdown.runTaskTimer(Main.getInstance(), 20L, 20L);
                }
            } else {
                if (!this.hasEnoughPlayers()) {
                    int playersNeeded = this.getMinPlayers() - this.getPlayerAmount();
                    for (Player aPlayer : this.getPlayers()) {
                        if (aPlayer.isOnline()) {
                            aPlayer.sendMessage(ChatWriter
                                    .pluginMessage(
                                            ChatColor.GREEN + "More players needed: " + playersNeeded));
                        }
                    }
                } else if (!this.hasEnoughTeams()) {
                    for (Player aPlayer : this.getPlayers()) {
                        if (aPlayer.isOnline()) {
                            aPlayer.sendMessage(ChatWriter
                                    .pluginMessage(ChatColor.RED + "More teams needed"));
                        }
                    }
                }
            }
        }

        this.updateScoreboard();
        this.updateSigns();

        return true;

    }

    public boolean hasEnoughTeams() {
        int teamsWithPlayers = 0;
        for (Team team : this.getTeams().values()) {
            if (team.getPlayers().size() > 0) {
                teamsWithPlayers++;
            }
        }

        return (teamsWithPlayers > 1 || (teamsWithPlayers == 1 && this.getFreePlayers().size() >= 1)
                || (teamsWithPlayers == 0 && this.getFreePlayers().size() >= 2));
    }

    public boolean hasEnoughPlayers() {
        return this.getPlayers().size() >= this.getMinPlayers();
    }

    public boolean isStartable() {
        return (this.hasEnoughPlayers() && this.hasEnoughTeams());
    }

    public PlayerSettings getPlayerSettings(Player player) {
        return this.playerSettings.get(player);
    }

    private void displayRecord() {
        for (Player player : this.getPlayers()) {
            this.displayRecord(player);
        }
    }

    private void displayRecord(Player player) {
        boolean displayHolders = Main
                .getInstance().getBooleanConfig("store-game-records-holder", true);

        if (displayHolders && this.getRecordHolders().size() > 0) {
            StringBuilder holders = new StringBuilder();

            for (String holder : this.recordHolders) {
                if (holders.length() == 0) {
                    holders.append(ChatColor.WHITE + holder);
                } else {
                    holders.append(ChatColor.GOLD + ", " + ChatColor.WHITE + holder);
                }
            }

            player
                    .sendMessage(ChatWriter.pluginMessage(
                            "Record: " + this.getFormattedRecord() + " | Holders: " + holders.toString()));
        } else {
            player.sendMessage(ChatWriter.pluginMessage(
                    "Record: " + this.getFormattedRecord()));
        }
    }

    public void setPlayerVisibility(Player player) {
        ArrayList<Player> players = new ArrayList<Player>();
        players.addAll(this.getPlayers());

        Main main = Main.getInstance();

        if (this.state == GameState.RUNNING) {
            if (this.isSpectator(player)) {
                if (player.getGameMode().equals(GameMode.SURVIVAL)) {
                    for (Player playerInGame : players) {
                        playerInGame.hidePlayer(main, player);
                        player.showPlayer(main, playerInGame);
                    }
                } else {
                    for (Player teamPlayer : this.getTeamPlayers()) {
                        teamPlayer.hidePlayer(main, player);
                        player.showPlayer(main, teamPlayer);
                    }
                    for (Player freePlayer : this.getFreePlayers()) {
                        freePlayer.showPlayer(main, player);
                        player.showPlayer(main, freePlayer);
                    }
                }
            } else {
                for (Player playerInGame : players) {
                    playerInGame.showPlayer(main, player);
                    player.showPlayer(main, playerInGame);
                }
            }
        } else {
            for (Player playerInGame : players) {
                if (!playerInGame.equals(player)) {
                    playerInGame.showPlayer(main, player);
                    player.showPlayer(main, playerInGame);
                }
            }
        }
    }

    public Player getPlayerDamager(Player p) {
        return this.playerDamages.get(p);
    }

    public boolean isProtected(Player player) {
        return (this.respawnProtections.containsKey(player) && this.getState() == GameState.RUNNING);
    }

    public void addResourceSpawner(ResourceSpawner rs) {
        this.resourceSpawners.add(rs);
    }

    public boolean isFull() {
        return (this.getMaxPlayers() <= this.getPlayerAmount());
    }

    public HashMap<String, Team> getTeams() {
        return this.teams;
    }

    private void makeTeamsReady() {
        this.playingTeams.clear();

        for (Team team : this.teams.values()) {
            team.getScoreboardTeam()
                    .setAllowFriendlyFire(Main.getInstance().getConfig().getBoolean("friendlyfire"));
            if (team.getPlayers().size() == 0) {
                this.dropTargetBlock(team.getHeadTarget());
            } else {
                this.playingTeams.add(team);
            }
        }

        this.updateScoreboard();
    }

    private void dropTargetBlock(Block targetBlock) {
        if (targetBlock.getType().equals(Material.BED_BLOCK)) {
            Block bedHead;
            Block bedFeet;
            Bed bedBlock = (Bed) targetBlock.getState().getData();

            if (!bedBlock.isHeadOfBed()) {
                bedFeet = targetBlock;
                bedHead = Utils.getBedNeighbor(bedFeet);
            } else {
                bedHead = targetBlock;
                bedFeet = Utils.getBedNeighbor(bedHead);
            }

            bedFeet.setType(Material.AIR);
        } else {
            targetBlock.setType(Material.AIR);
        }
    }

    public void setPlayerGameMode(Player player) {
        if (this.isSpectator(player)) {
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setGameMode(GameMode.SPECTATOR);

        } else {
            if (this.getState().equals(GameState.RUNNING)) {
                player.setGameMode(GameMode.SURVIVAL);
            } else if (this.getState().equals(GameState.WAITING)) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    private void moveFreePlayersToTeam() {
        for (Player player : this.freePlayers) {
            Team lowest = this.getLowestTeam();
            lowest.addPlayer(player);
        }

        this.freePlayers = new ArrayList<Player>();
        this.updateScoreboard();
    }

    private Team getLowestTeam() {
        Team lowest = null;
        for (Team team : this.teams.values()) {
            if (lowest == null) {
                lowest = team;
                continue;
            }

            if (team.getPlayers().size() < lowest.getPlayers().size()) {
                lowest = team;
            }
        }

        return lowest;
    }

    public Location getPlayerTeleportLocation(Player player) {
        if (this.isSpectator(player) && !(this.getCycle().isEndGameRunning())) {
            return ((Team) this.teams.values().toArray()[Utils.randInt(0, this.teams.size() - 1)])
                    .getSpawnLocation();
        }

        if (this.getPlayerTeam(player) != null && !(this.getCycle().isEndGameRunning()
                && Main.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true))) {
            return this.getPlayerTeam(player).getSpawnLocation();
        }

        return this.getLobby();
    }

    public void toSpectator(Player player) {
        final Player p = player;

        if (!this.freePlayers.contains(player)) {
            this.freePlayers.add(player);
        }

        PlayerStorage storage = this.getPlayerStorage(player);
        if (storage != null) {
            storage.clean();
        } else {
            storage = this.addPlayerStorage(player);
            storage.store();
            storage.clean();
        }

        final Location location = this.getPlayerTeleportLocation(p);

        if (!p.getLocation().getWorld().equals(location.getWorld())) {
            this.getPlayerSettings(p).setTeleporting(true);
        }

        new BukkitRunnable() {

            @Override
            public void run() {
                Game.this.setPlayerGameMode(p);
                Game.this.setPlayerVisibility(p);
            }

        }.runTaskLater(Main.getInstance(), 15L);

        final ItemStack item = new ItemStack(Material.SLIME_BALL, 1);
        final ItemMeta im = item.getItemMeta();

        im.setDisplayName("Leave Game");
        item.setItemMeta(im);
        p.getInventory().setItem(8, item);

        ItemStack teleportPlayer = new ItemStack(Material.COMPASS, 1);
        im = teleportPlayer.getItemMeta();
        im.setDisplayName("Spectate");
        teleportPlayer.setItemMeta(im);
        p.getInventory().setItem(0, teleportPlayer);
        p.updateInventory();
        this.updateScoreboard();
    }

    private void updateLobbyScoreboard() {
        this.scoreboard.clearSlot(DisplaySlot.SIDEBAR);

        Objective obj = this.scoreboard.getObjective("lobby");
        if (obj != null) {
            obj.unregister();
        }

        obj = this.scoreboard.registerNewObjective("lobby", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(this.formatLobbyScoreboardString("&eBEDWARS"));

        List<String> rows = Main.getInstance().getConfig()
                .getStringList("lobby-scoreboard.content");
        int rowMax = rows.size();
        if (rows == null || rows.isEmpty()) {
            return;
        }

        for (String row : rows) {
            if (row.trim().equals("")) {
                for (int i = 0; i <= rowMax; i++) {
                    row = row + " ";
                }
            }

            Score score = obj.getScore(this.formatLobbyScoreboardString(row));
            score.setScore(rowMax);
            rowMax--;
        }

        for (Player player : this.getPlayers()) {
            player.setScoreboard(this.scoreboard);
        }
    }

    public void updateScoreboard() {
        if (this.state == GameState.WAITING) {
            this.updateLobbyScoreboard();
            return;
        }

        Objective obj = this.scoreboard.getObjective("display");
        if (obj == null) {
            obj = this.scoreboard.registerNewObjective("display", "dummy");
        }

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(this.formatScoreboardTitle());

        for (Team t : this.teams.values()) {
            this.scoreboard.resetScores(this.formatScoreboardTeam(t, false));
            this.scoreboard.resetScores(this.formatScoreboardTeam(t, true));

            boolean teamDead = (t.isDead(this) && this.getState() == GameState.RUNNING) ? true : false;
            Score score = obj.getScore(this.formatScoreboardTeam(t, teamDead));
            score.setScore(t.getPlayers().size());
        }

        for (Player player : this.getPlayers()) {
            player.setScoreboard(this.scoreboard);
        }
    }

    public void loadItemShopCategories() {
        this.shopCategories = MerchantCategory.loadCategories(Main.getInstance().getShopConfig());
        this.orderedShopCategories = this.loadOrderedItemShopCategories();
    }

    private void cleanUsersInventory() {
        for (PlayerStorage storage : this.playerStorages.values()) {
            storage.clean();
        }
    }

    public void stopWorkers() {
        for (BukkitTask task : this.runningTasks) {
            try {
                task.cancel();
            } catch (Exception ex) {
                Main.getInstance().getBugsnag().notify(ex);
                // already cancelled
            }
        }

        this.runningTasks.clear();
    }

    public RespawnProtectionRunnable addProtection(Player player) {
        final RespawnProtectionRunnable rpr = new RespawnProtectionRunnable(this, player,
                Main.getInstance().getRespawnProtectionTime());

        this.respawnProtections.put(player, rpr);

        return rpr;
    }

    public boolean isSpectator(Player player) {
        return (this.getState() == GameState.RUNNING && this.freePlayers.contains(player));
    }

    public GameState getState() {
        return this.state;
    }

    public GameCycle getCycle() {
        return this.cycle;
    }

    public int getMaxPlayers() {
        int max = 0;

        for (Team t : this.teams.values()) {
            max += t.getMaxPlayers();
        }

        return max;
    }

    public PlayerStorage getPlayerStorage(Player p) {
        return this.playerStorages.get(p);
    }

    public PlayerStorage addPlayerStorage(Player p) {
        PlayerStorage storage = new PlayerStorage(p);
        this.playerStorages.put(p, storage);

        return storage;
    }

    public void removeProtection(Player player) {
        final RespawnProtectionRunnable rpr = this.respawnProtections.get(player);

        if (rpr != null) {
            try {
                rpr.cancel();
            } catch (Exception ex) {
                Main.getInstance().getBugsnag().notify(ex);
                // isn't running, ignore
            }

            this.respawnProtections.remove(player);
        }
    }

    public void setPlayerDamager(Player p, Player damager) {
        this.playerDamages.remove(p);
        this.playerDamages.put(p, damager);
    }

    public boolean isOverSet() {
        return this.isOver;
    }

    public Team getPlayerTeam(Player p) {
        for (Team team : this.getTeams().values()) {
            if (team.isInTeam(p)) {
                return team;
            }
        }

        return null;
    }

    public boolean isInGame(Player p) {
        for (Team t : this.teams.values()) {
            if (t.isInTeam(p)) {
                return true;
            }
        }

        return this.freePlayers.contains(p);
    }

    public void kickAllPlayers() {
        for (Player p : this.getPlayers()) {
            this.playerLeave(p, false);
        }
    }

    public ArrayList<Player> getTeamPlayers() {
        final ArrayList<Player> players = new ArrayList<>();

        for (Team team : this.teams.values()) {
            players.addAll(team.getPlayers());
        }

        return players;
    }

    public void addTeam(String name, TeamColor color, int maxPlayers) {
        org.bukkit.scoreboard.Team newTeam = this.scoreboard.registerNewTeam(name);
        newTeam.setDisplayName(name);
        newTeam.setPrefix(color.getTextColor().toString());

        Team theTeam = new Team(name, color, maxPlayers, newTeam);
        this.teams.put(name, theTeam);
    }

    public void addTeam(Team team) {
        org.bukkit.scoreboard.Team newTeam = this.scoreboard.registerNewTeam(team.getName());
        newTeam.setDisplayName(team.getName());
        newTeam.setPrefix(team.getChatColor().toString());

        team.setScoreboardTeam(newTeam);

        this.teams.put(team.getName(), team);
    }

    public void broadcastSound(Sound sound, float volume, float pitch) {
        for (Player p : this.getPlayers()) {
            if (p.isOnline()) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        }
    }

    public void broadcastSound(Sound sound, float volume, float pitch, List<Player> players) {
        for (Player p : players) {
            if (p.isOnline()) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        }
    }

    public void openSpectatorCompass(Player player) {
        if (!this.isSpectator(player)) {
            return;
        }

        int teamplayers = this.getTeamPlayers().size();
        int nom = (teamplayers % 9 == 0) ? 9 : (teamplayers % 9);
        int size = teamplayers + (9 - nom);
        Inventory compass = Bukkit
                .createInventory(null, size, "Spectator");
        for (Team t : this.getTeams().values()) {
            for (Player p : t.getPlayers()) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.displayName(net.kyori.adventure.text.Component.text(t.getChatColor() + p.getDisplayName()));
                meta.lore(java.util.Arrays
                        .asList(net.kyori.adventure.text.Component.text(t.getChatColor() + t.getDisplayName())));
                meta.setOwner(p.getName());
                head.setItemMeta(meta);

                compass.addItem(head);
            }
        }

        player.openInventory(compass);
    }

    private String formatLobbyScoreboardString(String str) {
        String finalStr = str;

        finalStr = finalStr.replace("$gamename$", this.name);
        finalStr = finalStr.replace("$players$", String.valueOf(this.getPlayerAmount()));
        finalStr = finalStr.replace("$maxplayers$", String.valueOf(this.getMaxPlayers()));

        return ChatColor.translateAlternateColorCodes('&', finalStr);
    }

    public static String bedExistString() {
        return "\u2714";
    }

    public static String bedLostString() {
        return "\u2718";
    }

    private String formatScoreboardTeam(Team team, boolean destroyed) {
        String format = null;

        if (team == null) {
            return "";
        }

        if (destroyed) {
            format = "&c$status$ $team$";
        } else {
            format = "&a$status$ $team$";
        }

        format = format.replace("$status$", (destroyed) ? Game.bedLostString() : Game.bedExistString());
        format = format.replace("$team$", team.getChatColor() + team.getName());

        return ChatColor.translateAlternateColorCodes('&', format);
    }

    private String formatScoreboardTitle() {
        String format = Main.getInstance()
                .getStringConfig("scoreboard.format-title");

        // replaces
        format = format.replace("$game$", this.name);
        format = format.replace("$time$", this.getFormattedTimeLeft());

        return ChatColor.translateAlternateColorCodes('&', format);
    }

    private String getFormattedTimeLeft() {
        int min = 0;
        int sec = 0;
        String minStr = "";
        String secStr = "";

        min = (int) Math.floor(this.timeLeft / 60);
        sec = this.timeLeft % 60;

        minStr = (min < 10) ? "0" + String.valueOf(min) : String.valueOf(min);
        secStr = (sec < 10) ? "0" + String.valueOf(sec) : String.valueOf(sec);

        return minStr + ":" + secStr;
    }

    public Team isOver() {
        if (this.isOver || this.state != GameState.RUNNING) {
            return null;
        }

        final ArrayList<Player> players = this.getTeamPlayers();
        final ArrayList<Team> teams = new ArrayList<>();

        if (players.size() == 0 || players.isEmpty()) {
            return null;
        }

        for (Player player : players) {
            final Team playerTeam = this.getPlayerTeam(player);

            if (teams.contains(playerTeam)) {
                continue;
            }

            if (!player.isDead()) {
                teams.add(playerTeam);
            } else if (!playerTeam.isDead(this)) {
                teams.add(playerTeam);
            }
        }

        if (teams.size() == 1) {
            return teams.get(0);
        } else {
            return null;
        }
    }

    public ArrayList<Player> getPlayers() {
        ArrayList<Player> players = new ArrayList<>();

        players.addAll(this.freePlayers);

        for (Team team : this.teams.values()) {
            players.addAll(team.getPlayers());
        }

        return players;
    }

    public int getPlayerAmount() {
        return this.getPlayers().size();
    }

    public Location getLobby() {
        return this.lobby;
    }
}
