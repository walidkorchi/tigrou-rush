package io.github.rush.statistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.momirealms.craftengine.bukkit.font.BukkitFontManager;
import net.momirealms.craftengine.core.font.BitmapImage;
import net.momirealms.craftengine.core.util.Key;

import org.bukkit.OfflinePlayer;

import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "player_levels")
@Getter
@NoArgsConstructor
public class PlayerLevel {

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
            BukkitFontManager fontManager = BukkitFontManager
                    .instance();
            if (fontManager == null)
                return;
            Key key = Key
                    .of("tland:level_ranks");
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

    /**
     * Returns the MiniMessage tag for this player's rank, or "§7Non classé" if
     * unranked.
     */
    public String getFormattedRank() {
        return getRankTag(rankIndex);
    }

    /**
     * Returns the MiniMessage tag for the given rank index, or "§7Non classé" if
     * unranked.
     */
    public static String getRankTag(int rankIndex) {
        if (rankIndex < 0)
            return "<gray>Non classé</gray>";
        if (ranksLoaded && rankMiniMessageTags != null) {
            int prestige = rankIndex / 12; // 0=Bronze, 1=Silver, 2=Gold
            int inPrestige = rankIndex % 12;
            int gem = inPrestige / 3; // 0=Emerald, 1=Amethyst, 2=Diamond, 3=Ruby
            int level = inPrestige % 3; // 0=I, 1=II, 2=III
            int row = prestige * 4 + gem;
            int col = level;
            if (row < rankMiniMessageTags.length && col < rankMiniMessageTags[row].length) {
                return rankMiniMessageTags[row][col];
            }
        }
        return "<gray>" + getPrestigeName(rankIndex) + " " + getGemName(rankIndex) + " " + getLevelInRank(rankIndex)
                + "</gray>";
    }

    // --- Instance progress helpers ---

    /** XP accumulated since entering current rank (or totalXP if unranked). */
    public long getProgressInRank() {
        if (rankIndex < 0)
            return totalXP;
        return totalXP - getRankThreshold(rankIndex);
    }

    /** XP needed to reach next rank; 0 if at max rank. */
    public long getXPToNextRank() {
        if (rankIndex >= 35)
            return 0;
        return getRankThreshold(rankIndex + 1) - totalXP;
    }

    /** Total XP range for the current rank segment (used for progress bar). */
    public long getXPForCurrentRange() {
        if (rankIndex < 0)
            return FIRST_RANK_XP;
        if (rankIndex >= 35)
            return 1;
        return getRankThreshold(rankIndex + 1) - getRankThreshold(rankIndex);
    }

    // --- Mutation ---

    public void addXP(long xp) {
        this.totalXP += xp;
        int newRank = getRankIndex(this.totalXP);
        if (newRank > this.rankIndex) {
            this.rankIndex = newRank;
        } else if (newRank < this.rankIndex) {
            this.rankIndex = newRank;
        }
    }

    public void setTotalXP(long totalXP) {
        this.totalXP = Math.max(0, totalXP);
        this.rankIndex = getRankIndex(this.totalXP);
    }
}
