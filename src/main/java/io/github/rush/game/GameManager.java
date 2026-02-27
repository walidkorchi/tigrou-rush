package io.github.rush.game;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.google.common.collect.ImmutableMap;

import io.github.rush.Main;
import io.github.rush.utils.ChatWriter;

public class GameManager {
    public static String gamesPath = "games";
    private ArrayList<Game> games = null;
    private Map<Player, Game> gamePlayer = null;

    public GameManager() {
        this.games = new ArrayList<Game>();
        this.gamePlayer = new HashMap<Player, Game>();
    }

    public Game addGame(String name) {
        Game existing = this.getGame(name);
        if (existing != null) {
            return null;
        }

        Game newGame = new Game(name);
        this.games.add(newGame);
        return newGame;
    }

    public void addGamePlayer(Player player, Game game) {
        if (this.gamePlayer.containsKey(player)) {
            this.gamePlayer.remove(player);
        }

        this.gamePlayer.put(player, game);
    }

    public Game getGame(String name) {
        for (Game game : this.games) {
            if (game.getName().equals(name)) {
                return game;
            }
        }

        return null;
    }

    public void loadGame(File configFile) {
        try {

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
            String name = cfg.get("name").toString();

            if (name.isEmpty()) {
                return;
            }

            Game game = new Game(name);
            game.setConfig(cfg);

            Map<String, Object> teams = new HashMap<String, Object>();
            Map<String, Object> spawner = new HashMap<String, Object>();
            String targetMaterialObj = null;

            if (cfg.contains("teams")) {
                teams = cfg.getConfigurationSection("teams").getValues(false);
            }

            if (cfg.contains("spawner")) {
                if (cfg.isConfigurationSection("spawner")) {
                    spawner = cfg.getConfigurationSection("spawner").getValues(false);

                    for (Object obj : spawner.values()) {
                        if (!(obj instanceof ResourceSpawner)) {
                            continue;
                        }

                        ResourceSpawner rs = (ResourceSpawner) obj;
                        rs.setGame(game);
                        game.addResourceSpawner(rs);
                    }
                }

                if (cfg.isList("spawner")) {
                    for (Object rs : cfg.getList("spawner")) {
                        if (!(rs instanceof ResourceSpawner)) {
                            continue;
                        }

                        ResourceSpawner rsp = (ResourceSpawner) rs;
                        rsp.setGame(game);
                        game.addResourceSpawner(rsp);
                    }
                }
            }

            for (Object obj : teams.values()) {
                if (!(obj instanceof Team)) {
                    continue;
                }

                game.addTeam((Team) obj);
            }

            this.games.add(game);

        } catch (Exception e) {
            Main.getInstance().getBugsnag().notify(ex);
            Main.getInstance().getServer().getConsoleSender()
                    .sendMessage(ChatWriter.pluginMessage(ChatColor.RED + Main
                            ._l(Main.getInstance().getServer().getConsoleSender(),
                                    "errors.gameloaderror",
                                    ImmutableMap.of("game", configFile.getParentFile().getName()))));
        }
    }

    public void loadGames() {
        String path = Main.getInstance().getDataFolder() + File.separator + GameManager.gamesPath;
        File file = new File(path);

        if (!file.exists()) {
            return;
        }

        File[] files = file.listFiles(new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });

        if (files.length > 0) {
            for (File dir : files) {
                File[] configFiles = dir.listFiles();
                for (File cfg : configFiles) {
                    if (!cfg.isFile()) {
                        continue;
                    }

                    if (cfg.getName().equals("game.yml")) {
                        this.loadGame(cfg);
                    }
                }
            }
        }

        for (Game g : this.games) {
            if (!g.run(Main.getInstance().getServer().getConsoleSender())) {
                Main.getInstance().getServer().getConsoleSender()
                        .sendMessage(ChatWriter.pluginMessage(ChatColor.RED + Main
                                ._l(Main.getInstance().getServer().getConsoleSender(),
                                        "errors.gamenotloaded")));
            }
        }
    }

    public void reloadGames() {
        this.unloadGames();

        this.gamePlayer.clear();
        this.loadGames();
    }

    public Game getGameOfPlayer(Player player) {
        return this.gamePlayer.get(player);
    }

    public void removeGame(Game game) {
        if (game == null) {
            return;
        }

        File configs = new File(Main.getInstance().getDataFolder() + File.separator
                + GameManager.gamesPath + File.separator + game.getName());

        if (configs.exists()) {
            configs.delete();
        }

        this.games.remove(game);
    }

    public void removeGamePlayer(Player player) {
        this.gamePlayer.remove(player);
    }

    public void unloadGame(Game game) {
        if (game.getState() != GameState.STOPPED) {
            game.stop();
        }

        game.setState(GameState.STOPPED);
        game.setScoreboard(Main.getInstance().getScoreboardManager().getNewScoreboard());

        try {
            game.kickAllPlayers();
        } catch (Exception e) {
            Main.getInstance().getBugsnag().notify(e);
            e.printStackTrace();
        }
        game.resetRegion();
        game.updateSigns();
    }

    public void unloadGames() {
        for (Game g : this.games) {
            this.unloadGame(g);
        }

        this.games.clear();
    }
}
