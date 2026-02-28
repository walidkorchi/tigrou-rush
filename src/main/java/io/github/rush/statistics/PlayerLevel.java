package io.github.rush.statistics;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

@Entity
@Table(name = "player_levels")
@Getter
@NoArgsConstructor
public class PlayerLevel {

    private static final int MAX_LEVEL = 150;

    @Id
    private UUID uuid;

    private int level = 0;
    private int currentXP = 0;
    private int totalXP = 0;

    public PlayerLevel(UUID uuid) {
        this.uuid = uuid;
    }

    public PlayerLevel(OfflinePlayer player) {
        this.uuid = player.getUniqueId();
    }

    public int getXPForNextLevel() {
        // TODO: add this logic
        return 100;
    }

    public String getTierIcon() {
        if (level >= MAX_LEVEL) {
            return "♕";
        } else if (level >= 5) {
            return "✦";
        }
        return "☆";
    }

    public String getTierColor() {
        if (level >= MAX_LEVEL) {
            return "§c[§61§e5§a0§b❉§d]";
        }
        if (level >= 145) {
            return "§b";
        }
        if (level >= 140) {
            return "§c";
        }
        if (level >= 135) {
            return "§c";
        }
        if (level >= 130) {
            return "§e";
        }
        if (level >= 125) {
            return "§e";
        }
        if (level >= 120) {
            return "§7";
        }
        if (level >= 115) {
            return "§7";
        }
        if (level >= 110) {
            return "§b";
        }
        if (level >= 105) {
            return "§4";
        }
        if (level >= 100) {
            return "§1";
        }
        if (level >= 95) {
            return "§e";
        }
        if (level >= 90) {
            return "§3";
        }
        if (level >= 85) {
            return "§3";
        }
        if (level >= 80) {
            return "§b";
        }
        if (level >= 75) {
            return "§9";
        }
        if (level >= 70) {
            return "§6";
        }
        if (level >= 65) {
            return "§f";
        }
        if (level >= 60) {
            return "§c";
        }
        if (level >= 55) {
            return "§f";
        }
        if (level >= 50) {
            return "§c[§65§e0§a❉§b]";
        }
        if (level >= 45) {
            return "§5";
        }
        if (level >= 40) {
            return "§9";
        }
        if (level >= 35) {
            return "§d";
        }
        if (level >= 30) {
            return "§4";
        }
        if (level >= 25) {
            return "§3";
        }
        if (level >= 20) {
            return "§2";
        }
        if (level >= 15) {
            return "§b";
        }
        if (level >= 10) {
            return "§6";
        }
        if (level >= 5) {
            return "§f";
        }
        return "§8";
    }

    public String getFormattedLevel() {
        String tierColor = getTierColor();
        String icon = getTierIcon();

        if (level >= 50 && level < MAX_LEVEL) {
            return tierColor + level + icon;
        } else if (level >= MAX_LEVEL) {
            return tierColor + icon;
        }

        return tierColor + String.valueOf(level) + icon;
    }

    public void addXP(int xp) {
        // TODO: add this logic
        this.totalXP += xp;
        this.currentXP += xp;
    }

    public void removeXP(int xp) {
        // TODO: add this logic
        this.totalXP = Math.max(0, this.totalXP - xp);
        this.currentXP = Math.max(0, this.currentXP - xp);
    }

    public void setLevel(int level) {
        this.level = Math.min(level, MAX_LEVEL);
    }

    public void setCurrentXP(int currentXP) {
        this.currentXP = Math.max(0, currentXP);
    }

    public void setTotalXP(int totalXP) {
        this.totalXP = Math.max(0, totalXP);
    }
}
