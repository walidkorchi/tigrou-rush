package io.github.rush.commands;

import io.github.rush.Main;
import io.github.rush.utils.i18n;
import io.github.rush.utils.RushLogger;
import io.papermc.paper.command.brigadier.Commands;
import lombok.Getter;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
import io.github.rush.storage.ConfigManager;
import io.github.rush.storage.PlayerLevelManager;
import io.github.rush.storage.PlayerLevelManager.PlayerLevel;
import io.github.rush.storage.PlayerStatisticManager;
import org.jspecify.annotations.NullMarked;

import com.maximde.hologramlib.hologram.TextHologram;

import java.util.*;

import net.kyori.adventure.text.Component;

@NullMarked
public class LeaderboardCommand {

    public enum LeaderboardType {
        KILLS("kills", "Top Kills", "kills"),
        WINS("wins", "Top Victoires", "victoires"),
        LEVEL("level", "Top Niveau", "niveau"),
        WINSTREAK("winstreak", "Top Win Streak", "victoires consécutives");

        @Getter
        private final String arg;
        @Getter
        private final String title;
        @Getter
        private final String suffix;

        LeaderboardType(String arg, String title, String suffix) {
            this.arg = arg;
            this.title = title;
            this.suffix = suffix;
        }

        public static LeaderboardType fromString(String arg) {
            for (LeaderboardType type : values()) {
                if (type.arg.equalsIgnoreCase(arg)) {
                    return type;
                }
            }

            return null;
        }

    }

    @Getter
    private final Map<Location, LeaderboardHologram> holograms = new HashMap<>();

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("leaderboard")
                .then(Commands.literal("kills")
                        .executes(ctx -> spawnLeaderboard(ctx, LeaderboardType.KILLS)))
                .then(Commands.literal("wins")
                        .executes(ctx -> spawnLeaderboard(ctx, LeaderboardType.WINS)))
                .then(Commands.literal("level")
                        .executes(ctx -> spawnLeaderboard(ctx, LeaderboardType.LEVEL)))
                .then(Commands.literal("winstreak")
                        .executes(ctx -> spawnLeaderboard(ctx, LeaderboardType.WINSTREAK)))
                .then(Commands.literal("remove")
                        .executes(ctx -> removeNearest(ctx)));
    }

    private int spawnLeaderboard(CommandContext<CommandSourceStack> ctx, LeaderboardType type) {
        return CommandManager.requirePlayer(ctx, player -> {
            if (Main.getInstance().getHologramManager() == null) {
                player.sendMessage(Component.translatable("rush.hologram_not_initialized"));
                return 0;
            }

            Location loc = player.getLocation().clone().add(0, 2, 0);

            LeaderboardHologram existing = holograms.remove(loc);
            if (existing != null) {
                Main.getInstance().getHologramManager().remove(existing.getHologram());
            }

            LeaderboardHologram lbHologram = new LeaderboardHologram(type, loc);
            lbHologram.spawn();
            holograms.put(loc, lbHologram);
            saveLeaderboards();

            player.sendMessage(Component.translatable("rush.hologram_placed", Component.text(type.getTitle())));
            return Command.SINGLE_SUCCESS;
        });
    }

    private int removeNearest(CommandContext<CommandSourceStack> ctx) {
        return CommandManager.requirePlayer(ctx, player -> {
            Location playerLoc = player.getLocation();
            LeaderboardHologram nearest = null;
            double nearestDist = Double.MAX_VALUE;

            for (Map.Entry<Location, LeaderboardHologram> entry : holograms.entrySet()) {
                double dist = entry.getKey().distanceSquared(playerLoc);
                if (dist < nearestDist && dist < 25) {
                    nearestDist = dist;
                    nearest = entry.getValue();
                }
            }

            if (nearest != null) {
                Main.getInstance().getHologramManager().remove(nearest.getHologram());
                holograms.values().remove(nearest);
                saveLeaderboards();
                player.sendMessage(Component.translatable("rush.hologram_removed"));
            } else {
                player.sendMessage(Component.translatable("rush.hologram_not_found"));
            }

            return Command.SINGLE_SUCCESS;
        });
    }

    public void updateAllHolograms() {
        for (LeaderboardHologram lbHologram : holograms.values()) {
            lbHologram.update();
        }
    }

    private void saveLeaderboards() {
        ConfigManager configManager = Main.getInstance().getConfigManager();
        if (configManager == null)
            return;

        FileConfiguration config = configManager.getConfig();
        List<Map<String, Object>> list = new ArrayList<>();

        for (Map.Entry<Location, LeaderboardHologram> entry : holograms.entrySet()) {
            Location loc = entry.getKey();
            if (loc.getWorld() == null)
                continue;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("world", loc.getWorld().getName());
            data.put("x", loc.getX());
            data.put("y", loc.getY());
            data.put("z", loc.getZ());
            data.put("type", entry.getValue().getType().name());
            list.add(data);
        }

        config.set("leaderboards", list);
        configManager.saveConfig();
    }

    public void restoreLeaderboards() {
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            ConfigManager configManager = Main.getInstance().getConfigManager();
            if (configManager == null)
                return;

            FileConfiguration config = configManager.getConfig();
            if (!config.contains("leaderboards"))
                return;

            List<Map<?, ?>> saved = config.getMapList("leaderboards");
            List<Map<String, Object>> valid = new ArrayList<>();

            for (Map<?, ?> data : saved) {
                String worldName = (String) data.get("world");
                double x = ((Number) data.get("x")).doubleValue();
                double y = ((Number) data.get("y")).doubleValue();
                double z = ((Number) data.get("z")).doubleValue();
                String typeName = (String) data.get("type");

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    RushLogger.warn("Leaderboard restore: world '" + worldName + "' not found, skipping.");
                    continue;
                }

                LeaderboardType type;
                try {
                    type = LeaderboardType.valueOf(typeName);
                } catch (IllegalArgumentException e) {
                    RushLogger.warn("Leaderboard restore: unknown type '" + typeName + "', skipping.");
                    continue;
                }

                Location loc = new Location(world, x, y, z);
                LeaderboardHologram lbh = new LeaderboardHologram(type, loc);
                lbh.spawn();
                holograms.put(loc, lbh);

                Map<String, Object> validEntry = new LinkedHashMap<>();
                validEntry.put("world", worldName);
                validEntry.put("x", x);
                validEntry.put("y", y);
                validEntry.put("z", z);
                validEntry.put("type", typeName);
                valid.add(validEntry);
            }

            // Prune stale entries (e.g. worlds that no longer exist)
            if (valid.size() != saved.size()) {
                config.set("leaderboards", valid);
                configManager.saveConfig();
            }

            RushLogger.info(i18n.log("internal.command.leaderboard.restored", holograms.size()));
        }, 1L);
    }

    /**
     * Cleans up all leaderboard holograms.
     * Called during plugin shutdown.
     */
    public void cleanupAll() {
        if (Main.getInstance().getHologramManager() != null) {
            for (LeaderboardHologram lbHologram : holograms.values()) {
                if (lbHologram.getHologram() != null) {
                    Main.getInstance().getHologramManager().remove(lbHologram.getHologram());
                }
            }
        }
        holograms.clear();
    }

    public static class LeaderboardHologram {
        @Getter
        private final LeaderboardType type;
        @Getter
        private final Location location;
        @Getter
        private TextHologram hologram;

        public LeaderboardHologram(LeaderboardType type, Location location) {
            this.type = type;
            this.location = location;
        }

        public void spawn() {
            hologram = new TextHologram("lb_" + UUID.randomUUID().toString().substring(0, 8))
                    .setMiniMessageText(buildContent())
                    .setBillboard(Billboard.CENTER)
                    .setShadow(true)
                    .setScale(1.0F, 1.0F, 1.0F)
                    .setTextOpacity((byte) 255)
                    .setBackgroundColor(Color.fromARGB(80, 0, 0, 0).asARGB())
                    .setAlignment(TextAlignment.CENTER)
                    .setViewRange(0.3);

            Main.getInstance().getHologramManager().spawn(hologram, location);
        }

        public void update() {
            if (hologram != null) {
                hologram.setMiniMessageText(buildContent());
                hologram.update();
            }
        }

        private String buildContent() {
            StringBuilder sb = new StringBuilder();
            sb.append("<gradient:#FFD700:#FF69B4><bold>").append(type.getTitle()).append("</bold></gradient>\n");
            sb.append("<dark_gray>----------------</dark_gray>\n");

            List<Map.Entry<String, Integer>> top10 = fetchTop10();

            int rank = 1;
            for (Map.Entry<String, Integer> entry : top10) {
                String color = switch (rank) {
                    case 1 -> "gold";
                    case 2 -> "gray";
                    case 3 -> "#CD7F32"; // Bronze
                    default -> "white";
                };

                String medal = switch (rank) {
                    case 1 -> "<gold>1.</gold> ";
                    case 2 -> "<gray>2.</gray> ";
                    case 3 -> "<#CD7F32>3.</#CD7F32> ";
                    default -> rank + ". ";
                };

                if (type == LeaderboardType.LEVEL) {
                    int rankIndex = entry.getValue();
                    String rankTag = PlayerLevel.getRankTag(rankIndex);
                    sb.append(medal)
                            .append("<white>").append(entry.getKey()).append("</white>")
                            .append(" <dark_gray>-</dark_gray> ")
                            .append(rankTag)
                            .append("\n");
                } else {
                    sb.append("<").append(color).append(">")
                            .append(medal).append(entry.getKey())
                            .append(" <dark_gray>-</dark_gray> ").append(entry.getValue()).append(" ")
                            .append(type.getSuffix())
                            .append("</").append(color).append(">\n");
                }
                rank++;
            }

            // Fill empty slots
            while (rank <= 10) {
                sb.append("<dark_gray>").append(rank).append(". ---</dark_gray>\n");
                rank++;
            }

            return sb.toString();
        }

        private List<Map.Entry<String, Integer>> fetchTop10() {
            final PlayerStatisticManager statManager = Main.getInstance().getPlayerStatisticManager();
            final PlayerLevelManager levelManager = Main.getInstance().getPlayerLevelManager();

            try {
                return switch (type) {
                    case KILLS -> statManager.queryTop10("kills");
                    case WINS -> statManager.queryTop10("wins");
                    case WINSTREAK -> statManager.queryTop10("winStreak");
                    case LEVEL -> levelManager.getTop10ByXP();
                };
            } catch (Exception e) {
                RushLogger.warn(i18n.log("internal.command.leaderboard.query_failed", e.getMessage()));
                return List.of();
            }
        }

    }
}
