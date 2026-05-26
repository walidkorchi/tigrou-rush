package io.github.rush.storage;

import io.github.rush.Main;
import io.github.rush.commands.LeaderboardCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.momirealms.craftengine.bukkit.font.BukkitFontManager;
import net.momirealms.craftengine.core.font.BitmapImage;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlayerLevelManager {

    @Entity
    @Table(name = "player_levels")
    @Getter
    @NoArgsConstructor
    public static class PlayerLevel {

        public static final long FIRST_RANK_XP = 10_000;
        public static final double RANK_MULTIPLIER = 1.25;
        public static final double[] PRESTIGE_NERFS = { 1.0, 0.8, 0.5 };

        private static String[][] rankMiniMessageTags = null;
        @Getter
        private static boolean ranksLoaded = false;

        @Id
        private UUID uuid;

        @Column(name = "rank_index")
        private int rankIndex = -1;

        @Column(name = "total_xp")
        private long totalXP = 0;

        public PlayerLevel(UUID uuid) {
            this.uuid = uuid;
        }

        public PlayerLevel(OfflinePlayer player) {
            this.uuid = player.getUniqueId();
        }

        public static long getRankThreshold(int rankIndex) {
            if (rankIndex < 0)
                return 0;
            if (rankIndex < 12) {
                return (long) (FIRST_RANK_XP * Math.pow(RANK_MULTIPLIER, rankIndex));
            }
            int prestige = rankIndex / 12;
            int offset = rankIndex % 12;
            long base = getRankThreshold(prestige * 12 - 1);
            double effMultiplier = 1 + (RANK_MULTIPLIER - 1) * PRESTIGE_NERFS[prestige];
            return (long) (base * Math.pow(effMultiplier, offset + 1));
        }

        public static int getRankIndex(long totalXP) {
            if (totalXP < FIRST_RANK_XP)
                return -1;
            int rank = 0;
            while (rank < 35 && getRankThreshold(rank + 1) <= totalXP)
                rank++;
            return rank;
        }

        public static String getPrestigeName(int rankIndex) {
            if (rankIndex < 0)
                return "";
            return switch (rankIndex / 12) {
                case 0 -> "Bronze";
                case 1 -> "Argent";
                case 2 -> "Or";
                default -> "";
            };
        }

        public static String getGemName(int rankIndex) {
            if (rankIndex < 0)
                return "";
            return switch ((rankIndex % 12) / 3) {
                case 0 -> "Emeraude";
                case 1 -> "Améthyste";
                case 2 -> "Diamant";
                case 3 -> "Rubis";
                default -> "";
            };
        }

        public static int getLevelInRank(int rankIndex) {
            if (rankIndex < 0)
                return 0;
            return (rankIndex % 12) % 3 + 1;
        }

        public static void loadRankImages() {
            try {
                BukkitFontManager fontManager = BukkitFontManager.instance();
                if (fontManager == null)
                    return;
                Key key = Key.of("tland:level_ranks");
                Optional<BitmapImage> opt = fontManager.bitmapImageById(key);
                if (opt.isEmpty())
                    return;
                BitmapImage bitmap = opt.get();
                String[][] tags = new String[bitmap.rows()][bitmap.columns()];
                for (int row = 0; row < bitmap.rows(); row++)
                    for (int col = 0; col < bitmap.columns(); col++)
                        tags[row][col] = bitmap.miniMessageAt(row, col);
                rankMiniMessageTags = tags;
                ranksLoaded = true;
            } catch (Exception | NoClassDefFoundError ignored) {
                // CraftEngine not yet ready; retry will be scheduled by Main
            }
        }

        public String getFormattedRank() {
            return getRankTag(rankIndex);
        }

        public static String getRankTag(int rankIndex) {
            if (rankIndex < 0)
                return "<gray>Non classé</gray>";
            if (ranksLoaded && rankMiniMessageTags != null) {
                int prestige = rankIndex / 12;
                int inPrestige = rankIndex % 12;
                int gem = inPrestige / 3;
                int level = inPrestige % 3;
                int row = prestige * 4 + gem;
                int col = level;
                if (row < rankMiniMessageTags.length && col < rankMiniMessageTags[row].length) {
                    return rankMiniMessageTags[row][col];
                }
            }
            return "<gray>" + getPrestigeName(rankIndex) + " " + getGemName(rankIndex) + " " + getLevelInRank(rankIndex)
                    + "</gray>";
        }

        public long getProgressInRank() {
            if (rankIndex < 0)
                return totalXP;
            return totalXP - getRankThreshold(rankIndex);
        }

        public long getXPToNextRank() {
            if (rankIndex >= 35)
                return 0;
            return getRankThreshold(rankIndex + 1) - totalXP;
        }

        public long getXPForCurrentRange() {
            if (rankIndex < 0)
                return FIRST_RANK_XP;
            if (rankIndex >= 35)
                return 1;
            return getRankThreshold(rankIndex + 1) - getRankThreshold(rankIndex);
        }

        public void addXP(long xp) {
            this.totalXP += xp;
            int newRank = getRankIndex(this.totalXP);
            this.rankIndex = newRank;
        }

        public void setTotalXP(long totalXP) {
            this.totalXP = Math.max(0, totalXP);
            this.rankIndex = getRankIndex(this.totalXP);
        }
    }

    private final Main plugin;
    @Getter
    private final DatabaseManager databaseManager;

    public PlayerLevelManager(Main plugin) {
        this.plugin = plugin;
        this.databaseManager = new DatabaseManager(plugin);
    }

    public void addXP(UUID uuid, long xp) {
        final PlayerLevel playerLevel = loadPlayerLevel(uuid);
        final int oldRank = playerLevel.getRankIndex();

        playerLevel.addXP(xp);

        final int newRank = playerLevel.getRankIndex();
        savePlayerLevel(playerLevel);

        if (newRank > oldRank) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                int oldPrestige = oldRank / 12;
                int newPrestige = newRank / 12;

                if (oldRank >= 0 && newPrestige > oldPrestige) {
                    triggerPrestigeCelebration(player, newRank);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }

                if (plugin.getTablistManager() != null) {
                    plugin.getTablistManager().updatePlayerListName(player);
                }
            }

            LeaderboardCommand lb = plugin.getCommandManager() != null
                    ? plugin.getCommandManager().getLeaderboardCommand()
                    : null;
            if (lb != null)
                lb.updateAllHolograms();
        }
    }

    private void triggerPrestigeCelebration(Player player, int newRank) {
        String prestigeName = PlayerLevel.getPrestigeName(newRank);
        player.showTitle(Title.title(
                Component.translatable("rush.prestige_title", Component.text(prestigeName)),
                Component.translatable("rush.prestige_subtitle"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        Bukkit.broadcast(Component.translatable("rush.prestige_broadcast",
                Component.text(player.getName()), Component.text(prestigeName)));
    }

    public void resetXP(UUID uuid) {
        final PlayerLevel playerLevel = loadPlayerLevel(uuid);
        playerLevel.setTotalXP(0);
        savePlayerLevel(playerLevel);

        final Player player = Bukkit.getPlayer(uuid);
        if (player != null && plugin.getTablistManager() != null) {
            plugin.getTablistManager().updatePlayerListName(player);
        }
    }

    public PlayerLevel loadPlayerLevel(UUID uuid) {
        final EntityManager em = databaseManager.getEntityManager();
        try {
            PlayerLevel playerLevel = em.find(PlayerLevel.class, uuid);
            if (playerLevel == null) {
                playerLevel = new PlayerLevel(uuid);
            }
            return playerLevel;
        } finally {
            em.close();
        }
    }

    public PlayerLevel getPlayerLevel(Player player) {
        return loadPlayerLevel(player.getUniqueId());
    }

    public void savePlayerLevel(PlayerLevel playerLevel) {
        final EntityManager em = databaseManager.getEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(playerLevel);
            em.getTransaction().commit();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("Failed to save player level: " + e.getMessage());
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
        } finally {
            em.close();
        }
    }

    public List<Map.Entry<String, Integer>> getTop10ByXP() {
        EntityManager em = databaseManager.getEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
            Root<PlayerLevel> levelRoot = query.from(PlayerLevel.class);

            query.multiselect(levelRoot.get("uuid"), levelRoot.<Integer>get("rankIndex"))
                    .orderBy(cb.desc(levelRoot.get("totalXP")));

            List<Object[]> rows = em.createQuery(query).setMaxResults(10).getResultList();
            List<Map.Entry<String, Integer>> result = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                UUID uuid = (UUID) row[0];
                int rankIndex = (Integer) row[1];
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null || name.isEmpty())
                    name = uuid.toString().substring(0, 8);
                result.add(Map.entry(name, rankIndex));
            }
            return result;
        } finally {
            em.close();
        }
    }

    public void close() {
        databaseManager.close();
    }
}
