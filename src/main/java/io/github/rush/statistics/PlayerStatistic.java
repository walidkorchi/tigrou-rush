package io.github.rush.statistics;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
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
    private UUID uuid;

    private int currentDeaths = 0;
    private int currentDestroyedBeds = 0;
    private int currentKills = 0;
    private int currentLoses = 0;
    private int currentScore = 0;
    private int currentWins = 0;
    private int currentAssists = 0;
    
    @Setter(AccessLevel.NONE)
    private int deaths = 0;
    @Setter(AccessLevel.NONE)
    private int destroyedBeds = 0;
    @Setter(AccessLevel.NONE)
    private int kills = 0;
    @Setter(AccessLevel.NONE)
    private int loses = 0;
    @Setter(AccessLevel.NONE)
    private int assists = 0;
    
    private String name = "";
    
    @Setter(AccessLevel.NONE)
    private int score = 0;
    
    @Setter(AccessLevel.NONE)
    private int wins = 0;

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

    public int getCurrentGames() {
        return this.getCurrentWins() + this.getCurrentLoses();
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

    public int getGames() {
        return this.getWins() + this.getLoses();
    }

    public UUID getId() {
        return this.uuid;
    }

    public void setId(UUID uuid) {
        this.uuid = uuid;
    }

    public void setCurrentScore(int score) {
        this.currentScore = score;
    }

    public void setCurrentKills(int kills) {
        this.currentKills = kills;
    }

    public void setCurrentDeaths(int deaths) {
        this.currentDeaths = deaths;
    }

    public void setCurrentWins(int wins) {
        this.currentWins = wins;
    }

    public void setCurrentLoses(int loses) {
        this.currentLoses = loses;
    }

    public void setCurrentAssists(int assists) {
        this.currentAssists = assists;
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
