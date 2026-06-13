package io.github.rush.objects;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.entities.GamePlayer;
import io.github.rush.game.GameRoom;
import io.github.rush.abstracts.Team;
import net.kyori.adventure.text.Component;
import io.github.rush.utils.RushLogger;

public class TNT implements Listener {

    // TODO: make this YML configurable
    private static final Set<Material> EXPLOSION_BREAKABLE = Set.of(
            Material.SANDSTONE, Material.END_STONE);

    private final Main plugin;

    public TNT(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();
        final Block block = event.getClickedBlock();

        if (item != null
                && item.getType() != Material.TNT
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && block != null
                && block.getType() == Material.TNT
                && event.useInteractedBlock() != Result.DENY) {
            block.setType(Material.AIR);
            spawnTNT(block.getLocation(), player);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) {
            if (event.getCause() == DamageCause.FALL) {
                event.setDamage(getAdjustedFallDamage(event));
            } else if (event.getCause() == DamageCause.BLOCK_EXPLOSION) {
                event.setDamage(getAdjustedTNTDamage(event));
            }
        }
    }

    private double getAdjustedFallDamage(EntityDamageEvent event) {
        return event.getDamage() / plugin.getConfig().getDouble("fallFromFlyDamage");
    }

    private double getAdjustedTNTDamage(EntityDamageEvent event) {
        return event.getDamage() / plugin.getConfig().getDouble("TNTDamage");
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntityType() == EntityType.PLAYER && event.getDamager() instanceof TNTPrimed)
            if (isPlayerIRG((Player) event.getEntity()))
                event.setDamage(getAdjustedTNTDamage((EntityDamageByEntityEvent) event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof TNTPrimed primed)
            handleExplosion(event, primed.getSource(), event.getLocation(), event.getYield(), event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event, null, event.getBlock().getLocation(), event.getYield(), event.blockList());
    }

    private void handleExplosion(Event event, Entity source, Location location,
            float yield, List<Block> blockList) {
        final Iterator<Block> blockIterator = blockList.iterator();

        while (blockIterator.hasNext()) {
            final Block block = blockIterator.next();
            final Material blockType = block.getType();

            if (block.getState() instanceof Bed) {
                blockIterator.remove();
                handleBedDestruction(block, source);
                continue;
            }

            if (blockType == Material.TNT) {
                block.setType(Material.AIR);
                spawnTNT(block.getLocation(), source);
                blockIterator.remove();
                continue;
            }

            // edge case: protect island blocks but let player-placed blocks break
            if (!EXPLOSION_BREAKABLE.contains(blockType)) {
                blockIterator.remove();
            }
        }
    }

    private void handleBedDestruction(Block bedBlock, Entity source) {
        if (!(bedBlock.getState() instanceof Bed bed))
            return;

        final GameRoom room = Main.getInstance().getGameManager().getGameRoomByWorld(bedBlock.getWorld().getName());

        if (room == null || !room.isRunning())
            return;
        else if (room.getConfig().extraHearts())
            return;

        final Team.Color bedColor = Team.Color.valueOf(bed.getColor().name());

        if (bedColor == null)
            return;

        final Game game = room.getGame();
        final Team team = game.getTeam(bedColor.name());
        final Player tntSource = resolveTntSource(source);

        // [extraHearts] edge case: satisfied if extra bed with no team assgined to it
        if (team == null) {
            removeBedBlocks(bedBlock);
            if (tntSource != null)
                game.onExtraBedDestroyed(tntSource);
            return;
        }

        if (team.isBedDestroyed())
            return;

        if (tntSource != null) {
            Team sourceTeam = game.getPlayerTeam(new GamePlayer(tntSource));
            if (sourceTeam != null && sourceTeam == team)
                return;
        }

        removeBedBlocks(bedBlock);
        game.onBedDestroyed(team, tntSource);
    }

    /**
     * Removes both halves of the bed (foot and head) to prevent bed item drop
     */
    public static void removeBedBlocks(Block bedBlock) {
        org.bukkit.block.data.type.Bed bedData = (org.bukkit.block.data.type.Bed) bedBlock.getBlockData();
        Block otherHalf;

        if (bedData.getPart() == org.bukkit.block.data.type.Bed.Part.FOOT)
            otherHalf = bedBlock.getRelative(bedData.getFacing());
        else
            otherHalf = bedBlock.getRelative(bedData.getFacing().getOppositeFace());

        bedBlock.setType(Material.AIR, false);
        otherHalf.setType(Material.AIR, false);
    }

    private Player resolveTntSource(Entity source) {
        if (source instanceof Player) {
            return (Player) source;
        } else if (source instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player) {
                return (Player) tnt.getSource();
            }
        }
        return null;
    }

    public TNTPrimed spawnTNT(Location location, Entity source) {
        final TNTPrimed tnt = location.getWorld().spawn(location.add(0.5, 0.25, 0.5), TNTPrimed.class);

        tnt.setVelocity(new Vector(0, 0.25, 0));
        tnt.teleport(location);
        tnt.setIsIncendiary(false);
        tnt.setFuseTicks(plugin.getConfig().getInt("explodeTicks"));
        tnt.setYield((float) plugin.getConfig().getDouble("radius"));
        tnt.setCustomNameVisible(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                final String timeLeft = new DecimalFormat("0.0").format(tnt.getFuseTicks() / 20.0);

                tnt.customName(Component.text(timeLeft.concat("s"))); // i.e: 5s

                if (!tnt.isValid() || tnt.getFuseTicks() <= 0)
                    cancel();
            }
        }.runTaskTimer(plugin, 0, 1);

        if (source != null) {
            try {
                setTntSource(tnt, source);
            } catch (ReflectiveOperationException event) {
                RushLogger.warn("Cannot set the source for " + tnt + " (" + event.getClass().getName() + "): "
                        + event.getMessage());
            }
        }

        return tnt;
    }

    // TODO: substitute NMS legacy code
    public static void setTntSource(TNTPrimed tnt, Entity source) throws ReflectiveOperationException {
        if (hasSetSourceMethod()) {
            tnt.setSource(source);
            return;
        }

        // Old Bukkit versions support
        final Method tntGetHandle = tnt.getClass().getDeclaredMethod("getHandle");
        final Method entityGetHandle = source.getClass().getDeclaredMethod("getHandle");

        final Object craftTnt = tntGetHandle.invoke(tnt);
        final Object craftEntity = entityGetHandle.invoke(source);

        final Field sourceField = craftTnt.getClass().getDeclaredField("source");

        sourceField.setAccessible(true);
        sourceField.set(craftTnt, craftEntity);
    }

    private static boolean hasSetSourceMethod() {
        try {
            TNTPrimed.class.getMethod("setSource", Entity.class);
            return true;
        } catch (NoSuchMethodException event) {
            return false;
        }
    }

    /**
     * Checks if the rush game has started and the player is in the rush world.
     */
    public boolean isPlayerIRG(Player player) {
        final Game game = plugin.getGameManager().getGameForPlayer(player);
        return game != null && game.getState() == io.github.rush.game.GameState.RUNNING;
    }
}
