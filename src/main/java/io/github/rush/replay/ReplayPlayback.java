package io.github.rush.replay;

import io.github.rush.Main;
import io.github.rush.game.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ReplayPlayback {

    private static final UUID GLOBAL = new UUID(0L, 0L);
    private static final int DISTANCE_HEIGHT_LIMIT = 12;
    private static final double[] SPEED_STEPS = {0.25, 0.5, 1.0, 2.0, 3.0, 4.0};

    private final ReplayFile file;
    private final World world;
    private final List<Player> viewers = new ArrayList<>();
    private final List<Mannequin> mannequins = new ArrayList<>();
    private final Map<UUID, Mannequin> mannequinByPlayer = new HashMap<>();
    private final Map<UUID, Integer> frameIndices = new HashMap<>();
    private final Set<UUID> deadPlayers = new HashSet<>();
    private final Map<UUID, UUID> followerTargets = new HashMap<>();

    private long playheadMs = 0;
    private boolean isPaused = true;
    private double speedMultiplier = 1.0;
    private BukkitTask tickTask;

    public ReplayPlayback(ReplayFile file, World world) {
        this.file = file;
        this.world = world;
        spawnMannequins();
        tickTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::tick, 1L, 1L);
    }

    // -------------------------------------------------------------------------
    // Mannequin setup
    // -------------------------------------------------------------------------

    private void spawnMannequins() {
        Map<String, String> teamColors = file.header().teamColorsByPlayerUuid();

        for (Map.Entry<UUID, List<ReplayAction>> entry : file.actions().entrySet()) {
            UUID uuid = entry.getKey();
            if (uuid.equals(GLOBAL)) continue;

            MoveAction firstMove = entry.getValue().stream()
                    .filter(a -> a instanceof MoveAction)
                    .map(a -> (MoveAction) a)
                    .findFirst()
                    .orElse(null);
            if (firstMove == null) continue;

            Location loc = new Location(world,
                    firstMove.x(), firstMove.y(), firstMove.z(),
                    firstMove.yaw(), firstMove.pitch());

            Mannequin mannequin = (Mannequin) world.spawnEntity(loc, EntityType.MANNEQUIN);
            mannequin.setAI(false);
            mannequin.setGravity(false);
            mannequin.setSilent(true);
            mannequin.setInvulnerable(true);

            Color color = resolveColor(teamColors != null ? teamColors.get(uuid.toString()) : null);
            applyArmor(mannequin, color);

            mannequins.add(mannequin);
            mannequinByPlayer.put(uuid, mannequin);
        }
    }

    private Color resolveColor(String teamColorName) {
        if (teamColorName == null) return Color.WHITE;
        try {
            return TeamColor.valueOf(teamColorName).getColor();
        } catch (IllegalArgumentException e) {
            return Color.WHITE;
        }
    }

    private void applyArmor(Mannequin mannequin, Color color) {
        var equip = mannequin.getEquipment();
        equip.setHelmet(coloredLeather(Material.LEATHER_HELMET, color));
        equip.setLeggings(coloredLeather(Material.LEATHER_LEGGINGS, color));
        equip.setBoots(coloredLeather(Material.LEATHER_BOOTS, color));
    }

    private ItemStack coloredLeather(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Tick loop
    // -------------------------------------------------------------------------

    private void tick() {
        if (isPaused) return;

        playheadMs += (long) (50 * speedMultiplier);

        for (Map.Entry<UUID, List<ReplayAction>> entry : file.actions().entrySet()) {
            UUID uuid = entry.getKey();
            List<ReplayAction> actions = entry.getValue();
            int idx = frameIndices.getOrDefault(uuid, 0);

            while (idx < actions.size() && actions.get(idx).timestamp() <= playheadMs) {
                dispatchAction(uuid, actions.get(idx));
                idx++;
            }

            frameIndices.put(uuid, idx);
        }

        for (Player viewer : viewers) {
            UUID targetUuid = followerTargets.get(viewer.getUniqueId());
            if (targetUuid == null) continue;
            Mannequin mannequin = mannequinByPlayer.get(targetUuid);
            if (mannequin == null) continue;
            Location mLoc = mannequin.getLocation();
            if (mLoc.getY() > world.getMinHeight() + 1) {
                viewer.teleport(mLoc);
            }
        }
    }

    private void dispatchAction(UUID uuid, ReplayAction action) {
        if (action instanceof MoveAction move) {
            if (deadPlayers.contains(uuid)) return;
            Mannequin mannequin = mannequinByPlayer.get(uuid);
            if (mannequin != null) {
                mannequin.teleport(new Location(world,
                        move.x(), move.y(), move.z(),
                        move.yaw(), move.pitch()));
            }
        } else if (action instanceof BlockChangeAction block) {
            world.getBlockAt(block.x(), block.y(), block.z())
                 .setType(Material.valueOf(block.newMaterial()), false);
        } else if (action instanceof DeathAction) {
            deadPlayers.add(uuid);
            Mannequin mannequin = mannequinByPlayer.get(uuid);
            if (mannequin != null) {
                Location loc = mannequin.getLocation();
                mannequin.teleport(new Location(world, loc.getX(), world.getMinHeight(), loc.getZ()));
            }
        } else if (action instanceof RespawnAction respawn) {
            deadPlayers.remove(uuid);
            Mannequin mannequin = mannequinByPlayer.get(uuid);
            if (mannequin != null) {
                mannequin.teleport(new Location(world, respawn.x(), respawn.y(), respawn.z()));
            }
        } else if (action instanceof PhaseAction phase) {
            if ("OVERTIME".equals(phase.phaseName())) {
                for (Player viewer : viewers) {
                    viewer.showTitle(Title.title(
                            Component.text("§c§lOVERTIME!"),
                            Component.text("§7Les restrictions de placement sont levées!")));
                }
            }
        } else if (action instanceof BedDestroyAction bed) {
            String destroyerName = "Inconnu";
            if (bed.destroyerUuid() != null) {
                Player online = Bukkit.getPlayer(bed.destroyerUuid());
                if (online != null) {
                    destroyerName = online.getName();
                } else {
                    String cached = Bukkit.getOfflinePlayer(bed.destroyerUuid()).getName();
                    if (cached != null) destroyerName = cached;
                }
            }
            String msg = "§c" + destroyerName + " §7a détruit le lit de l'équipe §c" + bed.teamColorName();
            for (Player viewer : viewers) {
                viewer.sendMessage(Component.text(msg));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Playback controls
    // -------------------------------------------------------------------------

    public void togglePause() {
        isPaused = !isPaused;
        for (Player viewer : viewers) {
            viewer.getInventory().setItem(
                    ReplayViewerInventory.SLOT_PAUSE_RESUME,
                    ReplayViewerInventory.buildPauseResume(isPaused));
        }
    }

    public boolean isPaused() {
        return isPaused;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(double multiplier) {
        this.speedMultiplier = multiplier;
    }

    public long getPlayheadMs() {
        return playheadMs;
    }

    public void setPlayheadMs(long ms) {
        this.playheadMs = ms;
    }

    public long getDurationMs() {
        return file.header().durationMs();
    }

    public void stepSpeedUp() {
        for (int i = 0; i < SPEED_STEPS.length - 1; i++) {
            if (speedMultiplier == SPEED_STEPS[i]) {
                speedMultiplier = SPEED_STEPS[i + 1];
                updateSpeedItems();
                return;
            }
        }
    }

    public void stepSpeedDown() {
        for (int i = SPEED_STEPS.length - 1; i > 0; i--) {
            if (speedMultiplier == SPEED_STEPS[i]) {
                speedMultiplier = SPEED_STEPS[i - 1];
                updateSpeedItems();
                return;
            }
        }
    }

    private void updateSpeedItems() {
        for (Player viewer : viewers) {
            viewer.getInventory().setItem(ReplayViewerInventory.SLOT_SPEED_DOWN,
                    ReplayViewerInventory.buildSpeedDown(speedMultiplier));
            viewer.getInventory().setItem(ReplayViewerInventory.SLOT_SPEED_UP,
                    ReplayViewerInventory.buildSpeedUp(speedMultiplier));
        }
    }

    public Map<UUID, Mannequin> getMannequinByPlayer() {
        return Collections.unmodifiableMap(mannequinByPlayer);
    }

    public UUID getFollowTarget(UUID viewerUuid) {
        return followerTargets.get(viewerUuid);
    }

    public void setFollowTarget(UUID viewerUuid, UUID targetUuid) {
        followerTargets.put(viewerUuid, targetUuid);
    }

    public void clearFollowTarget(UUID viewerUuid) {
        followerTargets.remove(viewerUuid);
    }

    public void seek(long targetMs) {
        targetMs = Math.max(0, Math.min(targetMs, file.header().durationMs()));
        if (targetMs >= playheadMs) {
            playheadMs = targetMs;
            for (Map.Entry<UUID, List<ReplayAction>> entry : file.actions().entrySet()) {
                UUID uuid = entry.getKey();
                List<ReplayAction> actions = entry.getValue();
                int idx = frameIndices.getOrDefault(uuid, 0);
                while (idx < actions.size() && actions.get(idx).timestamp() <= playheadMs) {
                    dispatchAction(uuid, actions.get(idx));
                    idx++;
                }
                frameIndices.put(uuid, idx);
            }
        } else {
            List<ReplayAction> allBlockActions = new ArrayList<>();
            for (List<ReplayAction> actions : file.actions().values()) {
                for (ReplayAction a : actions) {
                    if (a instanceof BlockChangeAction) allBlockActions.add(a);
                }
            }
            for (BlockRestore r : ReplaySeek.computeRestores(allBlockActions, playheadMs, targetMs)) {
                world.getBlockAt(r.x(), r.y(), r.z()).setType(Material.valueOf(r.material()), false);
            }
            playheadMs = targetMs;
            frameIndices.clear();
            repositionMannequins(targetMs);
        }
    }

    private void repositionMannequins(long targetMs) {
        deadPlayers.clear();
        for (Map.Entry<UUID, List<ReplayAction>> entry : file.actions().entrySet()) {
            UUID uuid = entry.getKey();
            List<ReplayAction> actions = entry.getValue();

            int idx = 0;
            while (idx < actions.size() && actions.get(idx).timestamp() <= targetMs) {
                idx++;
            }
            frameIndices.put(uuid, idx);

            if (uuid.equals(GLOBAL)) continue;
            Mannequin mannequin = mannequinByPlayer.get(uuid);
            if (mannequin == null) continue;

            boolean alive = true;
            double lastX = Double.NaN, lastY = Double.NaN, lastZ = Double.NaN;
            float lastYaw = 0, lastPitch = 0;

            for (int i = 0; i < idx; i++) {
                ReplayAction a = actions.get(i);
                if (a instanceof MoveAction m) {
                    lastX = m.x(); lastY = m.y(); lastZ = m.z();
                    lastYaw = m.yaw(); lastPitch = m.pitch();
                    alive = true;
                } else if (a instanceof DeathAction) {
                    alive = false;
                } else if (a instanceof RespawnAction r) {
                    lastX = r.x(); lastY = r.y(); lastZ = r.z();
                    lastYaw = 0; lastPitch = 0;
                    alive = true;
                }
            }

            if (!Double.isNaN(lastX)) {
                if (alive) {
                    mannequin.teleport(new Location(world, lastX, lastY, lastZ, lastYaw, lastPitch));
                } else {
                    deadPlayers.add(uuid);
                    mannequin.teleport(new Location(world, lastX, world.getMinHeight(), lastZ));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Viewer management
    // -------------------------------------------------------------------------

    public void addViewer(Player player) {
        viewers.add(player);
        int islandY = world.getMaxHeight() - DISTANCE_HEIGHT_LIMIT;
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(new Location(world, 0.5, islandY + 10, 0.5, 0f, -30f));
        ReplayViewerInventory.give(player, isPaused, speedMultiplier);
        player.sendMessage(Component.text("§7Replay de §f" + file.header().hostName() + "§7. Clic droit sur §e▶ Reprendre §7pour lancer la lecture."));
    }

    public boolean removeViewer(Player player) {
        viewers.remove(player);
        followerTargets.remove(player.getUniqueId());
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
        Location lobby = Main.getInstance().getMainLobby();
        if (lobby == null) {
            lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(lobby);
        Main.getInstance().getGameManager().restoreHubInventory(player);
        return viewers.isEmpty();
    }

    public boolean isViewerPresent(Player player) {
        return viewers.contains(player);
    }

    public boolean isEmpty() {
        return viewers.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getSessionId() {
        return file.header().sessionId();
    }

    public World getWorld() {
        return world;
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    public void cleanup() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (Mannequin mannequin : new ArrayList<>(mannequins)) {
            mannequin.remove();
        }
        mannequins.clear();
        mannequinByPlayer.clear();
        Main.getInstance().getGameManager().destroyReplayWorld(world);
    }
}
