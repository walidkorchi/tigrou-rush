package io.github.rush.objects;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.List;

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
import io.github.rush.game.GamePlayer;
import io.github.rush.game.GameRoom;
import io.github.rush.game.Team;
import io.github.rush.game.TeamColor;
import net.kyori.adventure.text.Component;

public class TNT implements Listener {

    private final Main plugin;

    public TNT(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();
        final Block block = event.getClickedBlock();

        if (item != null) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null
                    && block.getType() == Material.TNT
                    && event.useInteractedBlock() != Result.DENY) {
                block.setType(Material.AIR);
                spawnTNT(block.getLocation(), player);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled())
            return;
        if (event.getEntityType() == EntityType.PLAYER) {
            if (event.getCause() == DamageCause.FALL) {
                event.setDamage(event.getDamage() / plugin.getConfig().getDouble("fallDamage"));
            } else if (event.getCause() == DamageCause.BLOCK_EXPLOSION) {
                event.setDamage(event.getDamage() / plugin.getConfig().getDouble("TNTDamage"));
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled())
            return;
        if (event.getEntityType() == EntityType.PLAYER && isTNT(event.getDamager())) {
            if (isPlayerIRG((Player) event.getEntity())) {
                event.setDamage(event.getDamage() / plugin.getConfig().getDouble("TNTDamage"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        final Entity entity = event.getEntity();

        if (isTNT(entity)) {
            final Entity source = entity instanceof TNTPrimed ? ((TNTPrimed) entity).getSource() : null;

            handleExplosion(event, source, event.getLocation(), event.getYield(), event.blockList());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event, null, event.getBlock().getLocation(), event.getYield(), event.blockList());
    }

    public boolean isTNT(Entity entity) {
        return entity instanceof TNTPrimed;
    }

    private void handleExplosion(Event event, Entity source, Location location,
            float yield, List<Block> blockList) {
        final Iterator<Block> blockIterator = blockList.iterator();
        while (blockIterator.hasNext()) {
            final Block block = blockIterator.next();
            final Material blockType = block.getType();

            if (isBed(block)) {
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

            // Protect island blocks and remove non-island blocks from explosion
            blockIterator.remove();
        }
    }

    private boolean isBed(Block block) {
        BlockState state = block.getState();
        return state instanceof Bed;
    }

    private void handleBedDestruction(Block bedBlock, Entity source) {
        GameRoom room = Main.getInstance().getGameManager()
                .getGameRoomByWorld(bedBlock.getWorld().getName());
        if (room == null || !room.isRunning()) {
            return;
        }

        Game game = room.getGame();

        BlockState state = bedBlock.getState();
        if (!(state instanceof Bed bed)) {
            return;
        }

        TeamColor bedColor = getTeamColorFromBed(bed);

        if (bedColor == null) {
            return;
        }

        Team team = game.getTeam(bedColor.name());

        // Extra bed with no team — only relevant when extraHearts is enabled
        if (team == null) {
            if (room.getConfig().extraHearts()) {
                removeBedBlocks(bedBlock);
                Player tntSource = resolveTntSource(source);
                if (tntSource != null) {
                    game.onExtraBedDestroyed(tntSource);
                }
            }
            return;
        }

        if (team.isBedDestroyed()) {
            return;
        }

        Player tntSource = resolveTntSource(source);

        if (tntSource != null) {
            Team sourceTeam = game.getPlayerTeam(new GamePlayer(tntSource));
            if (sourceTeam != null && sourceTeam == team) {
                return;
            }
        }

        // Remove both halves of the bed (foot and head) to prevent item drops
        removeBedBlocks(bedBlock);

        game.onBedDestroyed(team, tntSource);
    }

    public static void removeBedBlocks(Block bedBlock) {
        org.bukkit.block.data.type.Bed bedData = (org.bukkit.block.data.type.Bed) bedBlock.getBlockData();
        Block otherHalf;
        if (bedData.getPart() == org.bukkit.block.data.type.Bed.Part.FOOT) {
            otherHalf = bedBlock.getRelative(bedData.getFacing());
        } else {
            otherHalf = bedBlock.getRelative(bedData.getFacing().getOppositeFace());
        }
        bedBlock.setType(Material.AIR, false);
        if (otherHalf.getType().name().endsWith("_BED")) {
            otherHalf.setType(Material.AIR, false);
        }
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

    private TeamColor getTeamColorFromBed(Bed bed) {
        try {
            return TeamColor.valueOf(bed.getColor().name());
        } catch (IllegalArgumentException e) {
            return null;
        }
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

                if (!tnt.isValid() || tnt.getFuseTicks() <= 0) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);

        if (source != null) {
            try {
                setTntSource(tnt, source);
            } catch (ReflectiveOperationException event) {
                plugin.getLogger().warning(
                        "Cannot set the source for " + tnt + " (" + event.getClass().getName() + "): "
                                + event.getMessage());
            }
        }
        return tnt;
    }

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
     *
     * @param player the player to check
     */
    public boolean isPlayerIRG(Player player) {
        final Game game = plugin.getGameManager().getGameForPlayer(player);
        return game != null && game.getState() == io.github.rush.game.GameState.RUNNING;
    }
}
