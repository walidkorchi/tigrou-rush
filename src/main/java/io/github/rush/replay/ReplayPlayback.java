package io.github.rush.replay;

import io.github.rush.Main;
import io.github.rush.game.TeamColor;
import io.github.rush.objects.TNT;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import lombok.Getter;
import lombok.Setter;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

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
    private static final double[] SPEED_STEPS = { 0.25, 0.5, 1.0, 2.0, 3.0, 4.0 };

    private final ReplayFile file;
    @Getter
    private final World world;
    private final List<Player> viewers = new ArrayList<>();
    private final List<Mannequin> mannequins = new ArrayList<>();
    private final Map<UUID, Mannequin> mannequinByPlayer = new HashMap<>();
    private final Map<UUID, Integer> frameIndices = new HashMap<>();
    private final Set<UUID> deadPlayers = new HashSet<>();
    private final Map<UUID, UUID> followerTargets = new HashMap<>();
    private final Set<Entity> replayEntities = new HashSet<>();
    private final List<BukkitTask> pendingTasks = new ArrayList<>();

    @Getter @Setter
    private long playheadMs = 0;
    @Getter
    private boolean isPaused = true;
    @Getter @Setter
    private double speedMultiplier = 1.0;
    private BukkitTask tickTask;

    public ReplayPlayback(ReplayFile file, World world) {
        this.file = file;
        this.world = world;
        spawnMannequins();
        tickTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::tick, 1L, 1L);
    }

    private void spawnMannequins() {
        Map<String, String> teamColors = file.header().teamColorsByPlayerUuid();

        for (Map.Entry<UUID, List<ReplayAction>> entry : file.actions().entrySet()) {
            UUID uuid = entry.getKey();
            if (uuid.equals(GLOBAL))
                continue;

            MoveAction firstMove = entry.getValue().stream()
                    .filter(a -> a instanceof MoveAction)
                    .map(a -> (MoveAction) a)
                    .findFirst()
                    .orElse(null);
            if (firstMove == null)
                continue;

            Location loc = new Location(world,
                    firstMove.x(), firstMove.y(), firstMove.z(),
                    firstMove.yaw(), firstMove.pitch());

            Mannequin mannequin = (Mannequin) world.spawnEntity(loc, EntityType.MANNEQUIN);
            mannequin.setAI(false);
            mannequin.setGravity(false);
            mannequin.setSilent(true);
            mannequin.setInvulnerable(true);
            mannequin.setCollidable(false);

            Color color = resolveColor(teamColors != null ? teamColors.get(uuid.toString()) : null);
            applyArmor(mannequin, color);

            PlayerProfile profile = Bukkit.createProfile(uuid);
            profile.update().thenAccept(updated -> {
                if (updated != null && updated.getTextures() != null
                        && updated.getTextures().getSkin() != null) {
                    mannequin.setProfile(ResolvableProfile.resolvableProfile(updated));
                }
            });

            mannequins.add(mannequin);
            mannequinByPlayer.put(uuid, mannequin);
        }
    }

    private Color resolveColor(String teamColorName) {
        if (teamColorName == null)
            return Color.WHITE;
        try {
            return TeamColor.valueOf(teamColorName).getColor();
        } catch (IllegalArgumentException e) {
            return Color.WHITE;
        }
    }

    private void applyArmor(Mannequin mannequin, Color color) {
        EntityEquipment equip = mannequin.getEquipment();
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

    private void applyEquipment(Mannequin mannequin, String mainHand, String offHand) {
        EntityEquipment equip = mannequin.getEquipment();
        if (mainHand != null) {
            Material mat = Material.getMaterial(mainHand);
            equip.setItemInMainHand(mat != null && mat != Material.AIR ? new ItemStack(mat) : null);
        }
        if (offHand != null) {
            Material mat = Material.getMaterial(offHand);
            equip.setItemInOffHand(mat != null && mat != Material.AIR ? new ItemStack(mat) : null);
        }
    }

    private void tick() {
        if (isPaused)
            return;

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

    }

    private void dispatchAction(UUID uuid, ReplayAction action) {
        if (action instanceof MoveAction move) {
            if (deadPlayers.contains(uuid))
                return;
            Mannequin mannequin = mannequinByPlayer.get(uuid);
            if (mannequin != null) {
                double yOffset = move.sneaking() ? -0.3 : 0.0;
                mannequin.teleport(new Location(world,
                        move.x(), move.y() + yOffset, move.z(),
                        move.yaw(), move.pitch()));
                applyEquipment(mannequin, move.mainHand(), move.offHand());
                mannequin.setSneaking(move.sneaking());
            }
        } else if (action instanceof BlockChangeAction block) {
            Block b = world.getBlockAt(block.x(), block.y(), block.z());
            b.setType(Material.valueOf(block.newMaterial()), false);
            world.playSound(b.getLocation(), b.getBlockData().getSoundGroup().getPlaceSound(),
                    SoundCategory.BLOCKS, 1.0f, 1.0f);
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
                mannequin.setSneaking(false);
                mannequin.teleport(new Location(world, respawn.x(), respawn.y(), respawn.z()));
            }
        } else if (action instanceof PhaseAction phase) {
            if ("OVERTIME".equals(phase.phaseName())) {
                for (Player viewer : viewers) {
                    viewer.showTitle(Title.title(
                            Component.translatable("rush.overtime_title"),
                            Component.translatable("rush.overtime_subtitle")));
                }
            }
        } else if (action instanceof ChatAction chat) {
            String senderName = chat.uuid() != null
                    ? (Bukkit.getOfflinePlayer(chat.uuid()).getName())
                    : "Inconnu";
            if (senderName == null)
                senderName = "Inconnu";
            for (Player viewer : viewers) {
                viewer.sendMessage(Component.translatable("rush.replay_chat_message",
                        Component.text(senderName), Component.text(chat.message())));
            }
        } else if (action instanceof ArmSwingAction swing) {
            Mannequin mannequin = mannequinByPlayer.get(uuid);
            if (mannequin != null) {
                mannequin.swingHand(swing.hand());
                world.playSound(mannequin.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                        SoundCategory.PLAYERS, 1.0f, 0.9f + (float) Math.random() * 0.2f);
            }
        } else if (action instanceof DamageAction damage) {
            Mannequin mannequin = mannequinByPlayer.get(damage.victimUuid());
            if (mannequin != null && !deadPlayers.contains(damage.victimUuid())) {
                mannequin.setInvulnerable(false);
                mannequin.damage(0.001);
                mannequin.setInvulnerable(true);
                Sound sound = "FALL".equals(damage.cause())
                        ? Sound.ENTITY_PLAYER_SMALL_FALL
                        : Sound.ENTITY_PLAYER_HURT;
                world.playSound(mannequin.getLocation(), sound, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        } else if (action instanceof BedDestroyAction bed) {
            String destroyerName = "Inconnu";
            if (bed.destroyerUuid() != null) {
                Player online = Bukkit.getPlayer(bed.destroyerUuid());
                if (online != null) {
                    destroyerName = online.getName();
                } else {
                    String cached = Bukkit.getOfflinePlayer(bed.destroyerUuid()).getName();
                    if (cached != null)
                        destroyerName = cached;
                }
            }
            for (Player viewer : viewers) {
                viewer.sendMessage(Component.translatable("rush.replay_bed_destroyed",
                        Component.text(destroyerName), Component.text(bed.teamColorName())));
            }
        } else if (action instanceof DropItemAction drop) {
            Location dropLoc = new Location(world, drop.x(), drop.y(), drop.z(), drop.yaw(), 0);
            Material mat = Material.getMaterial(drop.material());
            if (mat != null && mat != Material.AIR) {
                ItemStack stack = new ItemStack(mat, drop.amount());
                Item item = world.dropItem(dropLoc, stack);
                item.setVelocity(new Vector(drop.velX(), drop.velY(), drop.velZ()));
                item.setPickupDelay(Integer.MAX_VALUE);
                item.setUnlimitedLifetime(true);
                replayEntities.add(item);
            }
        } else if (action instanceof TntIgniteAction tnt) {
            Location tntLoc = new Location(world, tnt.x(), tnt.y(), tnt.z());
            Block block = tntLoc.getBlock();
            if (block.getType() == Material.TNT) {
                block.setType(Material.AIR);
            }
            TNTPrimed primed = world.spawn(tntLoc, TNTPrimed.class);
            primed.setFuseTicks(tnt.fuseTicks());
            primed.setIsIncendiary(false);
            primed.setVelocity(new Vector(0, 0.25, 0));
            replayEntities.add(primed);

            scheduleBedDestruction(tntLoc, tnt.fuseTicks());
        }
    }

    private void scheduleBedDestruction(Location tntLoc, int fuseTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!tntLoc.isChunkLoaded())
                return;
            int radius = Main.getInstance().getConfig().getInt("radius", 4);
            int bx = tntLoc.getBlockX(), by = tntLoc.getBlockY(), bz = tntLoc.getBlockZ();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        Block b = world.getBlockAt(bx + dx, by + dy, bz + dz);
                        if (b.getType().name().endsWith("_BED")) {
                            TNT.removeBedBlocks(b);
                            world.playSound(b.getLocation(), Sound.BLOCK_WOOL_BREAK,
                                    SoundCategory.BLOCKS, 1.0f, 1.0f);
                        }
                    }
                }
            }
        }, fuseTicks);
        pendingTasks.add(task);
    }

    private void clearReplayEntities() {
        for (BukkitTask task : pendingTasks) {
            task.cancel();
        }
        pendingTasks.clear();
        for (Entity entity : replayEntities) {
            entity.remove();
        }
        replayEntities.clear();
    }

    public void togglePause() {
        isPaused = !isPaused;
        for (Player viewer : viewers) {
            viewer.getInventory().setItem(
                    ReplayViewerInventory.SLOT_PAUSE_RESUME,
                    ReplayViewerInventory.buildPauseResume(isPaused));
        }
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
            clearReplayEntities();
            List<ReplayAction> allBlockActions = new ArrayList<>();
            for (List<ReplayAction> actions : file.actions().values()) {
                for (ReplayAction a : actions) {
                    if (a instanceof BlockChangeAction)
                        allBlockActions.add(a);
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

            if (uuid.equals(GLOBAL))
                continue;
            Mannequin mannequin = mannequinByPlayer.get(uuid);
            if (mannequin == null)
                continue;

            boolean alive = true;
            double lastX = Double.NaN, lastY = Double.NaN, lastZ = Double.NaN;
            float lastYaw = 0, lastPitch = 0;
            String lastMainHand = null, lastOffHand = null;
            boolean lastSneaking = false;

            for (int i = 0; i < idx; i++) {
                ReplayAction a = actions.get(i);
                if (a instanceof MoveAction m) {
                    lastX = m.x();
                    lastY = m.y();
                    lastZ = m.z();
                    lastYaw = m.yaw();
                    lastPitch = m.pitch();
                    if (m.mainHand() != null)
                        lastMainHand = m.mainHand();
                    if (m.offHand() != null)
                        lastOffHand = m.offHand();
                    lastSneaking = m.sneaking();
                    alive = true;
                } else if (a instanceof DeathAction) {
                    alive = false;
                } else if (a instanceof RespawnAction r) {
                    lastX = r.x();
                    lastY = r.y();
                    lastZ = r.z();
                    lastYaw = 0;
                    lastPitch = 0;
                    lastSneaking = false;
                    alive = true;
                }
            }

            if (!Double.isNaN(lastX)) {
                if (alive) {
                    double yOff = lastSneaking ? -0.3 : 0.0;
                    mannequin.teleport(new Location(world, lastX, lastY + yOff, lastZ, lastYaw, lastPitch));
                    applyEquipment(mannequin, lastMainHand, lastOffHand);
                    mannequin.setSneaking(lastSneaking);
                } else {
                    deadPlayers.add(uuid);
                    mannequin.teleport(new Location(world, lastX, world.getMinHeight(), lastZ));
                }
            }
        }
    }

    public void addViewer(Player player) {
        viewers.add(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        int islandY = world.getMaxHeight() - Main.getDISTANCE_HEIGHT_LIMIT();
        player.teleport(new Location(world, 0.5, islandY + 10, 0.5, 0f, -30f));
        ReplayViewerInventory.give(player, isPaused, speedMultiplier);
        player.sendMessage(Component.translatable("rush.replay_watching_full",
                Component.text(file.header().hostName())));
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (player.isOnline()) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
        });
    }

    public boolean removeViewer(Player player) {
        viewers.remove(player);
        followerTargets.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
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

    public String getSessionId() {
        return file.header().sessionId();
    }

    public void cleanup() {
        clearReplayEntities();
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
