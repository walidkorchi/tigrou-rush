package io.github.rush.statistics;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

@Entity
@Table(name = "player_statistics")
@Getter
@NoArgsConstructor
public class PlayerStatistic {

    @Id
    @Getter
    @Setter
    private UUID uuid;

    @Setter
    @Getter
    private int currentDeaths;

    @Setter
    private int currentDestroyedBeds;

    @Setter
    @Getter
    private int currentKills;

    @Setter
    private int currentLoses;

    @Setter
    @Getter
    private int currentScore;

    @Setter
    private int currentWins;

    @Setter
    private int currentAssists;

    @Setter
    @Getter
    private int deaths;

    @Setter
    private int destroyedBeds;

    @Setter
    @Getter
    private int kills;

    @Setter
    private int loses;

    @Setter
    private int assists;

    private String name = "";

    @Setter
    private int score;

    @Setter
    private int wins;

    @Setter
    @Getter
    private int winStreak;

    public PlayerStatistic(UUID uuid) {
        this.uuid = uuid;
    }

    public PlayerStatistic(OfflinePlayer player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    public void addCurrentValues() {
        this.deaths = this.deaths + this.currentDeaths;
        this.currentDeaths = 0;
        this.destroyedBeds = this.destroyedBeds + this.currentDestroyedBeds;
        this.currentDestroyedBeds = 0;
        this.kills = this.kills + this.currentKills;
        this.currentKills = 0;
        this.loses = this.loses + this.currentLoses;
        this.currentLoses = 0;
        this.assists = this.assists + this.currentAssists;
        this.currentAssists = 0;
        this.score = this.score + this.currentScore;
        this.currentScore = 0;
        this.wins = this.wins + this.currentWins;
        this.currentWins = 0;
    }

    public double getCurrentKD() {
        int totalDeaths = this.getDeaths() + this.getCurrentDeaths();
        int totalKills = this.getKills() + this.getCurrentKills();

        if (totalDeaths == 0) {
            return totalKills;
        } else if (totalKills == 0) {
            return 0.0;
        }

        return Math.round((double) totalKills / totalDeaths * 100.0) / 100.0;
    }

    public double getKD() {
        if (this.getDeaths() == 0) {
            return this.getKills();
        } else if (this.getKills() == 0) {
            return 0.0;
        }

        return Math.round((double) this.getKills() / this.getDeaths() * 100.0) / 100.0;
    }
}
