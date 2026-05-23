package io.github.rush.statistics;

import io.github.rush.Main;
import io.github.rush.database.DatabaseManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.Getter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerLevelManager {

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
            }

            var lb = plugin.getCommandManager() != null ? plugin.getCommandManager().getLeaderboardCommand() : null;
            if (lb != null) lb.updateAllHolograms();
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

    /** Returns top 10 players sorted by totalXP desc, with rankIndex as the value. */
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
                if (name == null || name.isEmpty()) name = uuid.toString().substring(0, 8);
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
