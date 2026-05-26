package io.github.rush;

import io.github.rush.commands.CommandManager;
import io.github.rush.utils.i18n;
import io.github.rush.storage.ConfigManager;
import io.github.rush.entities.*;
import io.github.rush.events.*;
import io.github.rush.objects.*;
import io.github.rush.game.*;
import io.github.rush.replay.ReplayManager;
import io.github.rush.replay.ReplayStorage;
import io.github.rush.storage.PlayerSettingsManager;
import io.github.rush.storage.*;
import io.github.rush.storage.PlayerLevelManager.PlayerLevel;
import io.github.rush.abstracts.Generator;

import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.hologram.HologramManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import fr.mrmicky.fastboard.FastBoard;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.item.ItemManager;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import lombok.Getter;

public class Main extends JavaPlugin {

    @Getter
    private static Main instance = null;
    @Getter
    private PlayerStatisticManager playerStatisticManager = null;
    @Getter
    private PlayerLevelManager playerLevelManager = null;
    @Getter
    private GameManager gameManager = null;
    @Getter
    private ScoreboardManager scoreboardManager = null;
    @Getter
    private TablistManager tablistManager = null;
    @Getter
    private PlayerSettingsManager playerSettingsManager = null;
    @Getter
    private HologramManager hologramManager = null;
    @Getter
    private CommandManager commandManager = null;
    @Getter
    private ConfigManager configManager = null;
    @Getter
    private ReplayStorage replayStorage = null;
    @Getter
    private ReplayManager replayManager = null;
    @Getter
    private ItemManager craftEngineItemManager;

    private BukkitCraftEngine craftEngine;
    private File craftEngineDataFolder;
    private final Map<Player, FastBoard> playerBoards = new HashMap<>();

    public String getCraftEngineDataFolder() {
        if (craftEngineDataFolder == null) {
            return getDataFolder().getParent() + "/CraftEngine";
        }
        return craftEngineDataFolder.getAbsolutePath();
    }

    public net.momirealms.craftengine.core.entity.player.Player adaptCraftPlayer(org.bukkit.entity.Player player) {
        return BukkitAdaptor.adapt(player);
    }

    public FastBoard getFastBoard(Player player) {
        return playerBoards.get(player);
    }

    public void setFastBoard(Player player, FastBoard board) {
        playerBoards.put(player, board);
    }

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);

        Generator.Type.loadFromConfig(configManager.getConfig());
        MerchantType.loadFromConfig(configManager.getConfig());

        i18n.register(this);

        scoreboardManager = new ScoreboardManager(this);
        tablistManager = new TablistManager(this);
        playerSettingsManager = new PlayerSettingsManager(this);
        playerStatisticManager = new PlayerStatisticManager(this);
        playerLevelManager = new PlayerLevelManager(this);
        gameManager = new GameManager(this);
        replayStorage = new ReplayStorage(getDataFolder().toPath().resolve("replays"));
        replayManager = new ReplayManager(this);

        craftEngineDataFolder = Bukkit.getPluginManager().getPlugin("CraftEngine").getDataFolder();
        craftEngine = BukkitCraftEngine.instance();
        craftEngineItemManager = craftEngine.itemManager();

        HologramLib.getManager().ifPresentOrElse(
                manager -> hologramManager = manager,
                () -> getLogger().severe(i18n.log("internal.main.hologram_init_failed")));

        Bukkit.getPluginManager().registerEvents(new GameRules(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerActivity(this), this);
        Bukkit.getPluginManager().registerEvents(new TNT(this), this);

        commandManager = new CommandManager();
        commandManager.registerAll(this);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commandManager::onCommands);

        // persist author particles animation + holograms between server restarts
        commandManager.getAuthorCommand().restoreExistingAuthors();
        commandManager.getLeaderboardCommand().restoreLeaderboards();

        final TranslationStore.StringBased<MessageFormat> mainStore = TranslationStore
                .messageFormat(Key.key("rush", "main"));

        try {
            mainStore.registerAll(Locale.FRANCE,
                    ResourceBundle.getBundle("io.github.rush.i18n.Bundle", Locale.FRANCE), true);
            GlobalTranslator.translator().addSource(mainStore);
            getLogger().info(i18n.log("internal.main.i18n_registered"));
        } catch (Exception e) {
            getLogger().warning(i18n.log("internal.main.i18n_register_failed", e.getMessage()));
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            scoreboardManager.updateAll();
        }, 0L, 1L);

        PlayerLevel.loadRankImages();
        if (PlayerLevel.isRanksLoaded()) {
            tablistManager.updateAll();
        } else {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                PlayerLevel.loadRankImages();
                tablistManager.updateAll();
            }, 1L);
        }
    }

    public Integer getRespawnProtectionTime() {
        return this.getConfig().getInt("respawn-protection");
    }

    public int getVoidThreshold() {
        return this.getConfig().getInt("void-threshold", 20);
    }

    public String getHubWorld() {
        return getConfig().getString("lobbyWorld");
    }

    public Location getMainLobby() {
        final FileConfiguration config = getConfig();
        if (!config.contains("lobby-spawn"))
            return null;

        final World world = Bukkit.getWorld(config.getString("lobby-spawn.world"));
        if (world == null)
            return null;

        return new Location(
                world,
                config.getDouble("lobby-spawn.x"),
                config.getDouble("lobby-spawn.y"),
                config.getDouble("lobby-spawn.z"),
                (float) config.getDouble("lobby-spawn.yaw"),
                (float) config.getDouble("lobby-spawn.pitch"));
    }

    @Override
    public void onDisable() {
        getLogger().info(i18n.log("internal.main.shutdown_start"));

        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info(i18n.log("internal.main.shutdown_tasks_cancelled"));

        if (gameManager != null) {
            for (GameRoom room : new ArrayList<>(gameManager.getAllGameRooms()))
                gameManager.removeGameRoom(room.getId());
            getLogger().info(i18n.log("internal.main.shutdown_rooms_cleaned"));
        }

        if (scoreboardManager != null) {
            for (Player player : new ArrayList<>(playerBoards.keySet())) {
                scoreboardManager.removeScoreboard(player);
            }
            scoreboardManager.removeAllScoreboards();
            getLogger().info(i18n.log("internal.main.shutdown_scoreboards_removed"));
        }

        if (commandManager != null && commandManager.getAuthorCommand() != null) {
            commandManager.getAuthorCommand().cleanupAll();
            getLogger().info(i18n.log("internal.main.shutdown_author_holograms_cleaned"));
        }

        if (commandManager != null && commandManager.getLeaderboardCommand() != null) {
            commandManager.getLeaderboardCommand().cleanupAll();
            getLogger().info(i18n.log("internal.main.shutdown_leaderboard_holograms_cleaned"));
        }

        playerBoards.clear();

        if (playerSettingsManager != null) {
            playerSettingsManager.close();
        }
        if (playerStatisticManager != null) {
            playerStatisticManager.close();
        }
        if (playerLevelManager != null) {
            playerLevelManager.close();
        }

        getLogger().info(i18n.log("internal.main.shutdown_complete"));
    }
}
