package io.github.rush.game;

import io.github.rush.Main;
import io.github.rush.statistics.PlayerStatistic;
import io.github.rush.utils.ChatWriter;
import io.github.rush.utils.Utils;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class GameCycle {

    private boolean endGameRunning = false;
    private Game game = null;

    public GameCycle(Game game) {
        this.game = game;
    }

    public void checkGameOver() {
        if (!Main.getInstance().isEnabled()) {
            return;
        }

        Team winner = this.getGame().isOver();
        if (winner != null) {
            if (!this.isEndGameRunning()) {
                this.runGameOver(winner);
            }
        }
    }

    public Game getGame() {
        return game;
    }

    public boolean isEndGameRunning() {
        return this.endGameRunning;
    }

    public void setEndGameRunning(boolean running) {
        this.endGameRunning = running;
    }

    public void onGameOver(GameOverTask task) {
        if (task.getCounter() == 0) {
            task.cancel();
        }

        task.decCounter();
    }

    public void onGameStart() {
    }

    public void onPlayerRespawn(PlayerRespawnEvent pre, Player player) {
        Team team = this.getGame().getPlayerTeam(player);

        // reset damager
        this.getGame().setPlayerDamager(player, null);

        if (this.getGame().isSpectator(player)) {
            Collection<Team> teams = this.getGame().getTeams().values();
            pre.setRespawnLocation(
                    ((Team) teams.toArray()[Utils.randInt(0, teams.size() - 1)]).getSpawnLocation());
            return;
        }

        if (team.isDead(this.getGame())) {
            PlayerStorage storage = this.getGame().getPlayerStorage(player);

            if (Main.getInstance().statisticsEnabled()) {
                PlayerStatistic statistic = Main.getInstance().getPlayerStatisticManager().getStatistic(player);
                statistic.setCurrentLoses(statistic.getCurrentLoses() + 1);
            }

            if (Main.getInstance().spectationEnabled()) {
                if (storage != null && storage.getLeft() != null) {
                    pre.setRespawnLocation(team.getSpawnLocation());
                }

                this.getGame().toSpectator(player);
            } else {

                if (!Main.getInstance().toMainLobby()) {
                    if (storage != null) {
                        if (storage.getLeft() != null) {
                            pre.setRespawnLocation(storage.getLeft());
                        }
                    }
                } else {
                    if (this.getGame().getMainLobby() != null) {
                        pre.setRespawnLocation(this.getGame().getMainLobby());
                    } else {
                        if (storage != null) {
                            if (storage.getLeft() != null) {
                                pre.setRespawnLocation(storage.getLeft());
                            }
                        }
                    }
                }

                this.getGame().playerLeave(player, false);
            }

        } else {
            if (Main.getInstance().getRespawnProtectionTime() > 0) {
                RespawnProtectionRunnable protection = this.getGame().addProtection(player);
                protection.runProtection();
            }
            pre.setRespawnLocation(team.getSpawnLocation());
        }

        new BukkitRunnable() {

            @Override
            public void run() {
                GameCycle.this.checkGameOver();
            }
        }.runTaskLater(Main.getInstance(), 20L);

    }

    public void onPlayerLeave(Player player) {
        // teleport to join location
        PlayerStorage storage = this.getGame().getPlayerStorage(player);

        if (Main.getInstance().toMainLobby()) {
            if (Main.getInstance().isHologramsEnabled()
                    && Main.getInstance().getHolographicInteractor() != null
                    && this.getGame().getMainLobby().getWorld() == player.getWorld()) {
                Main.getInstance().getHolographicInteractor().updateHolograms(player);
            }

            player.teleport(this.getGame().getMainLobby());
        } else {
            if (Main.getInstance().isHologramsEnabled()
                    && Main.getInstance().getHolographicInteractor() != null
                    && storage.getLeft() == player.getWorld()) {
                Main.getInstance().getHolographicInteractor().updateHolograms(player);
            }

            player.teleport(storage.getLeft());
        }

        if (this.getGame().getState() == GameState.RUNNING && !this.getGame().isStopping()
                && !this.getGame().isSpectator(player)) {
            this.checkGameOver();
        }
    }

    private void runGameOver(Team winner) {
        this.setEndGameRunning(true);

        final int delay = Main.getInstance().getConfig().getInt("gameoverdelay", 5);
        new GameOverTask(this, delay, winner).runTaskTimer(Main.getInstance(), 0L, 20L);
    }

    public void onPlayerDies(Player player, Player killer) {
        if (this.isEndGameRunning()) {
            return;
        }

        PlayerStatistic diePlayer = null;
        PlayerStatistic killerPlayer = null;

        Iterator<SpecialItem> itemIterator = this.game.getSpecialItems().iterator();

        while (itemIterator.hasNext()) {
            SpecialItem item = itemIterator.next();

            if (!(item instanceof RescuePlatform)) {
                continue;
            }

            RescuePlatform rescue = (RescuePlatform) item;
            if (rescue.getOwner().equals(player)) {
                itemIterator.remove();
            }
        }

        Team deathTeam = this.getGame().getPlayerTeam(player);
        if (Main.getInstance().statisticsEnabled()) {
            diePlayer = Main.getInstance().getPlayerStatisticManager().getStatistic(player);

            boolean onlyOnBedDestroy = Main.getInstance().getBooleanConfig("statistics.bed-destroyed-kills",
                    false);
            boolean teamIsDead = deathTeam.isDead(this.getGame());

            if ((onlyOnBedDestroy && teamIsDead) || !onlyOnBedDestroy) {
                diePlayer.setCurrentDeaths(diePlayer.getCurrentDeaths() + 1);
                diePlayer.setCurrentScore(diePlayer.getCurrentScore() + Main
                        .getInstance().getIntConfig("statistics.scores.die", 0));
            }

            if (killer != null) {
                if ((onlyOnBedDestroy && teamIsDead) || !onlyOnBedDestroy) {
                    killerPlayer = Main.getInstance().getPlayerStatisticManager().getStatistic(killer);
                    if (killerPlayer != null) {
                        killerPlayer.setCurrentKills(killerPlayer.getCurrentKills() + 1);
                        killerPlayer.setCurrentScore(killerPlayer.getCurrentScore() + Main
                                .getInstance().getIntConfig("statistics.scores.kill", 10));
                    }
                }
            }

            // dispatch reward commands directly
            if (Main.getInstance().getBooleanConfig("rewards.enabled", false) && killer != null
                    && ((onlyOnBedDestroy && teamIsDead) || !onlyOnBedDestroy)) {
                List<String> commands = Main.getInstance().getConfig()
                        .getStringList("rewards.player-kill");
                Main.getInstance().dispatchRewardCommands(commands,
                        ImmutableMap.of("{player}", killer.getName(), "{score}",
                                String
                                        .valueOf(Main.getInstance().getIntConfig("statistics.scores.kill", 10))));
            }
        }

        if (killer == null) {
            for (Player aPlayer : this.getGame().getPlayers()) {
                if (aPlayer.isOnline()) {
                    aPlayer.sendMessage(
                            ChatWriter.pluginMessage(
                                    ChatColor.GOLD + player.getName() + " died"));
                }
            }

            this.sendTeamDeadMessage(deathTeam);
            this.checkGameOver();
            return;
        }

        Team killerTeam = this.getGame().getPlayerTeam(killer);
        if (killerTeam == null) {
            for (Player aPlayer : this.getGame().getPlayers()) {
                if (aPlayer.isOnline()) {
                    aPlayer.sendMessage(
                            ChatWriter.pluginMessage(
                                    ChatColor.GOLD + player.getName() + " died"));
                }
            }
            this.sendTeamDeadMessage(deathTeam);
            this.checkGameOver();
            return;
        }

        String hearts = "";
        DecimalFormat format = new DecimalFormat("#");
        double health = ((double) killer.getHealth()) / ((double) killer.getMaxHealth())
                * ((double) killer.getHealthScale());
        if (!Main.getInstance().getBooleanConfig("hearts-in-halfs", true)) {
            format = new DecimalFormat("#.#");
            health = health / 2;
        }

        // display hearts on death
        hearts = "[" + ChatColor.RED + "\u2764" + format.format(health) + ChatColor.GOLD + "]";

        for (Player aPlayer : this.getGame().getPlayers()) {
            if (aPlayer.isOnline()) {
                aPlayer.sendMessage(
                        ChatWriter.pluginMessage(ChatColor.GOLD + killer.getName() + "[" + hearts + "] killed " + player.getName()));
            }
        }

        if (deathTeam.isDead(this.getGame())) {
            killer.playSound(killer.getLocation(), SoundMachine.get("LEVEL_UP", "ENTITY_PLAYER_LEVELUP"),
                    Float.valueOf("1.0"), Float.valueOf("1.0"));
        }
        this.sendTeamDeadMessage(deathTeam);
        this.checkGameOver();
    }

    private void sendTeamDeadMessage(Team deathTeam) {
        if (deathTeam.getPlayers().size() == 1 && deathTeam.isDead(this.getGame())) {
            for (Player aPlayer : this.getGame().getPlayers()) {
                if (aPlayer.isOnline()) {
                    aPlayer.sendMessage(
                            ChatWriter.pluginMessage(
                                    ChatColor.RED + "Team " + deathTeam.getDisplayName() + " is dead!"));
                }
            }
        }
    }
}
