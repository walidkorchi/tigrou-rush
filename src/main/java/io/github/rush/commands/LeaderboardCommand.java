package io.github.rush.commands;

import io.github.rush.Main;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import com.maximde.hologramlib.hologram.TextHologram;

import java.util.*;

import static net.kyori.adventure.text.Component.text;
import net.kyori.adventure.text.format.NamedTextColor;

@NullMarked
public class LeaderboardCommand {

    public enum LeaderboardType {
        KILLS("kills", "Top Kills", "kills"),
        WINS("wins", "Top Victoires", "victoires"),
        LEVEL("level", "Top Niveau", "niveau"),
        DEATHS("deaths", "Top Morts", "morts");

        private final String arg;
        private final String title;
        private final String suffix;

        LeaderboardType(String arg, String title, String suffix) {
            this.arg = arg;
            this.title = title;
            this.suffix = suffix;
        }

        public String getArg() {
            return arg;
        }

        public String getTitle() {
            return title;
        }

        public String getSuffix() {
            return suffix;
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

    private final Map<Location, LeaderboardHologram> holograms = new HashMap<>();

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("leaderboard")
                .then(Commands.literal("kills")
                        .executes(ctx -> spawnLeaderboard(ctx, LeaderboardType.KILLS)))
                .then(Commands.literal("wins")
                        .executes(ctx -> spawnLeaderboard(ctx, LeaderboardType.WINS)))
                .then(Commands.literal("level")
                        .executes(ctx -> spawnLeaderboard(ctx, LeaderboardType.LEVEL)))
                .then(Commands.literal("deaths")
                        .executes(ctx -> spawnLeaderboard(ctx, LeaderboardType.DEATHS)))
                .then(Commands.literal("remove")
                        .executes(ctx -> removeNearest(ctx)));
    }

    private int spawnLeaderboard(CommandContext<CommandSourceStack> ctx, LeaderboardType type) {
        CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("This command must be run by a player.", NamedTextColor.RED));
            return 0;
        }

        if (Main.getInstance().getHologramManager() == null) {
            player.sendMessage(text("HologramLib n'est pas initialisé.", NamedTextColor.RED));
            return 0;
        }

        Location loc = player.getLocation().clone().add(0, 2, 0);

        // Remove existing hologram at this location if any
        LeaderboardHologram existing = holograms.remove(loc);
        if (existing != null) {
            Main.getInstance().getHologramManager().remove(existing.getHologram());
        }

        LeaderboardHologram lbHologram = new LeaderboardHologram(type, loc);
        lbHologram.spawn();
        holograms.put(loc, lbHologram);

        player.sendMessage(text("Leaderboard " + type.getTitle() + " placé!", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int removeNearest(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("This command must be run by a player.", NamedTextColor.RED));
            return 0;
        }

        Location playerLoc = player.getLocation();
        LeaderboardHologram nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Map.Entry<Location, LeaderboardHologram> entry : holograms.entrySet()) {
            double dist = entry.getKey().distanceSquared(playerLoc);
            if (dist < nearestDist && dist < 25) { // Within 5 blocks
                nearestDist = dist;
                nearest = entry.getValue();
            }
        }

        if (nearest != null) {
            Main.getInstance().getHologramManager().remove(nearest.getHologram());
            holograms.values().remove(nearest);
            player.sendMessage(text("Leaderboard supprimé.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(text("Aucun leaderboard trouvé à proximité.", NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    public void updateAllHolograms() {
        for (LeaderboardHologram lbHologram : holograms.values()) {
            lbHologram.update();
        }
    }

    public Map<Location, LeaderboardHologram> getHolograms() {
        return holograms;
    }

    public static class LeaderboardHologram {
        private final LeaderboardType type;
        private final Location location;
        private TextHologram hologram;

        public LeaderboardHologram(LeaderboardType type, Location location) {
            this.type = type;
            this.location = location;
        }

        public void spawn() {
            hologram = new TextHologram("lb_" + UUID.randomUUID().toString().substring(0, 8))
                    .setMiniMessageText(buildContent())
                    .setBillboard(org.bukkit.entity.Display.Billboard.CENTER)
                    .setShadow(true)
                    .setScale(1.0F, 1.0F, 1.0F)
                    .setTextOpacity((byte) 255)
                    .setBackgroundColor(org.bukkit.Color.fromARGB(80, 0, 0, 0).asARGB())
                    .setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER)
                    .setViewRange(0.3);

            Main.getInstance().getHologramManager().spawn(hologram, location);
        }

        public void update() {
            if (hologram != null) {
                hologram.setMiniMessageText(buildContent());
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
                    case 1 -> "<gold>";
                    case 2 -> "<gray>";
                    case 3 -> "<#CD7F32>"; // Bronze
                    default -> "<white>";
                };

                String medal = switch (rank) {
                    case 1 -> "<gold>1.</gold> ";
                    case 2 -> "<gray>2.</gray> ";
                    case 3 -> "<#CD7F32>3.</#CD7F32> ";
                    default -> rank + ". ";
                };

                sb.append(color).append(medal).append(entry.getKey())
                  .append(" <dark_gray>-</dark_gray> ").append(entry.getValue())
                  .append(" ").append(type.getSuffix()).append("</color>\n");
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
            Map<String, Integer> data = new HashMap<>();

            var statManager = Main.getInstance().getPlayerStatisticManager();
            var levelManager = Main.getInstance().getPlayerLevelManager();

            // Get online players' stats
            for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                String name = player.getName();

                try {
                    switch (type) {
                        case KILLS -> {
                            var stats = statManager.loadStatistic(uuid);
                            data.put(name, stats.getKills());
                        }
                        case WINS -> {
                            var stats = statManager.loadStatistic(uuid);
                            data.put(name, stats.getWins());
                        }
                        case LEVEL -> {
                            var level = levelManager.loadPlayerLevel(uuid);
                            data.put(name, level.getLevel());
                        }
                        case DEATHS -> {
                            var stats = statManager.loadStatistic(uuid);
                            data.put(name, stats.getDeaths());
                        }
                    }
                } catch (Exception e) {
                    data.put(name, 0);
                }
            }

            return data.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .toList();
        }

        public TextHologram getHologram() {
            return hologram;
        }

        public Location getLocation() {
            return location;
        }
    }
}
