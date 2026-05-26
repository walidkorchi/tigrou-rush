package io.github.rush.replay;

import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameParticipant;
import io.github.rush.game.GamePlayer;
import io.github.rush.game.GameMannequin;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReplayRecorder implements Listener {

    static final UUID GLOBAL = new UUID(0L, 0L);

    private final Game game;
    private final String worldName;
    private final long startMs = System.currentTimeMillis();
    private final Map<UUID, List<ReplayAction>> actionMap = new HashMap<>();
    private BukkitTask movementTask;

    public ReplayRecorder(Game game, String worldName) {
        this.game = game;
        this.worldName = worldName;

        Bukkit.getPluginManager().registerEvents(this, Main.getInstance());

        movementTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::sampleMovement, 0L, 2L);
    }

    private long elapsed() {
        return System.currentTimeMillis() - startMs;
    }

    private List<ReplayAction> actionsFor(UUID uuid) {
        return actionMap.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    private void sampleMovement() {
        long ts = elapsed();

        for (GameParticipant participant : game.getPlayers()) {
            if (participant instanceof GamePlayer gp) {
                Player player = gp.player();
                final Location loc = player.getLocation();
                final PlayerInventory inv = player.getInventory();
                final String mainHand = inv.getItemInMainHand().getType().name();
                final String offHand = inv.getItemInOffHand().getType().name();

                actionsFor(player.getUniqueId()).add(
                        new MoveAction(ts, player.getUniqueId(),
                                loc.getX(), loc.getY(), loc.getZ(),
                                loc.getYaw(), loc.getPitch(),
                                mainHand, offHand, player.isSneaking()));
            } else if (participant instanceof GameMannequin gm) {
                Mannequin mannequin = gm.mannequin();
                final Location loc = mannequin.getLocation();
                String mainHand = "AIR";
                String offHand = "AIR";
                final EntityEquipment equip = mannequin.getEquipment();

                if (equip != null) {
                    ItemStack mh = equip.getItemInMainHand();
                    if (mh != null)
                        mainHand = mh.getType().name();
                    ItemStack oh = equip.getItemInOffHand();
                    if (oh != null)
                        offHand = oh.getType().name();
                }

                actionsFor(mannequin.getUniqueId()).add(
                        new MoveAction(ts, mannequin.getUniqueId(),
                                loc.getX(), loc.getY(), loc.getZ(),
                                loc.getYaw(), loc.getPitch(),
                                mainHand, offHand, false));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().getWorld().getName().equals(worldName))
            return;

        final Block block = event.getBlock();

        actionsFor(event.getPlayer().getUniqueId()).add(new BlockChangeAction(elapsed(),
                block.getX(), block.getY(), block.getZ(), worldName,
                block.getType().name(),
                event.getBlockReplacedState().getType().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.getPlayer().getWorld().getName().equals(worldName))
            return;

        final Block block = event.getBlock();

        actionsFor(event.getPlayer().getUniqueId()).add(new BlockChangeAction(elapsed(),
                block.getX(), block.getY(), block.getZ(), worldName,
                "AIR",
                block.getType().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!event.getPlayer().getWorld().getName().equals(worldName))
            return;

        final String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        final long ts = elapsed();
        final UUID uuid = event.getPlayer().getUniqueId();

        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            actionsFor(GLOBAL).add(new ChatAction(ts, uuid, text));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmSwing(PlayerArmSwingEvent event) {
        if (!event.getPlayer().getWorld().getName().equals(worldName))
            return;

        final UUID uuid = event.getPlayer().getUniqueId();

        actionsFor(uuid).add(new ArmSwingAction(elapsed(), uuid, event.getHand()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim))
            return;
        if (!victim.getWorld().getName().equals(worldName))
            return;

        UUID attackerUuid = null;

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player attacker) {
                attackerUuid = attacker.getUniqueId();
            } else if (byEntity.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player attacker) {
                attackerUuid = attacker.getUniqueId();
            }
        }

        actionsFor(victim.getUniqueId()).add(new DamageAction(
                elapsed(), victim.getUniqueId(), attackerUuid,
                event.getCause().name(), event.getFinalDamage()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!event.getPlayer().getWorld().getName().equals(worldName))
            return;

        final Player player = event.getPlayer();
        final Item item = event.getItemDrop();
        final Location loc = item.getLocation();
        final Vector vel = item.getVelocity();

        actionsFor(player.getUniqueId()).add(new DropItemAction(
                elapsed(), player.getUniqueId(),
                item.getItemStack().getType().name(),
                item.getItemStack().getAmount(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(),
                vel.getX(), vel.getY(), vel.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof TNTPrimed tnt) {
            if (!tnt.getWorld().getName().equals(worldName))
                return;
            actionsFor(GLOBAL).add(new TntIgniteAction(
                    elapsed(), tnt.getX(), tnt.getY(), tnt.getZ(), tnt.getFuseTicks()));
        }
    }

    public void recordDeath(UUID uuid) {
        actionsFor(uuid).add(new DeathAction(elapsed(), uuid));
    }

    public void recordRespawn(UUID uuid, double x, double y, double z) {
        actionsFor(uuid).add(new RespawnAction(elapsed(), uuid, x, y, z));
    }

    public void recordBedDestroy(String teamColorName, UUID destroyerUuid) {
        actionsFor(GLOBAL).add(new BedDestroyAction(elapsed(), teamColorName, destroyerUuid));
    }

    public void recordPhaseChange(String phaseName) {
        actionsFor(GLOBAL).add(new PhaseAction(elapsed(), phaseName));
    }

    public ReplayFile stop(String sessionId, String winnerTeamColorName, String hostName,
            List<String> participantNames, String mapTypeName,
            String islandTypeName, int maxTeams, boolean extraHearts,
            Map<String, String> teamColorsByPlayerUuid) {

        HandlerList.unregisterAll(this);

        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }

        final long durationMs = elapsed();
        final ReplayHeader header = new ReplayHeader(sessionId, hostName, startMs, durationMs,
                winnerTeamColorName, participantNames, mapTypeName,
                islandTypeName, maxTeams, extraHearts, teamColorsByPlayerUuid);
        return new ReplayFile(header, new HashMap<>(actionMap));
    }
}
