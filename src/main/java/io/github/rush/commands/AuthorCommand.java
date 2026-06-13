package io.github.rush.commands;

import io.github.rush.Main;
import io.github.rush.utils.i18n;
import io.github.rush.utils.RushLogger;
import io.github.rush.utils.ItemBuilder;
import io.github.rush.storage.ConfigManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;
import lombok.Getter;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.maximde.hologramlib.hologram.TextHologram;

import net.kyori.adventure.text.format.NamedTextColor;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public class AuthorCommand {

    private static final String ARMOR_STAND_TAG = "author_head";
    private static final String CONFIG_PATH = "author.location";
    private static final int SEARCH_RADIUS = 5;
    private static final double PARTICLE_RADIUS = 2;
    private static final int PARTICLE_STEPS = 40;

    private final Main plugin;
    private final Map<UUID, BukkitTask> particleTasks = new HashMap<>();
    private final Map<UUID, TextHologram> holograms = new HashMap<>();
    @Getter
    private final Map<Location, UUID> glassBlocks = new HashMap<>();

    public AuthorCommand(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the author name from plugin description (paper-plugin.yml).
     */
    private String getAuthorName() {
        List<String> authors = plugin.getPluginMeta().getAuthors();
        return authors.isEmpty() ? "Unknown" : authors.get(0);
    }

    /**
     * Called on plugin enable to restore author display from existing armor stand.
     */
    public void restoreExistingAuthors() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ConfigManager configManager = plugin.getConfigManager();
            FileConfiguration config = configManager != null ? configManager.getConfig() : null;

            // First, try to restore from saved location in config
            if (config != null && config.contains(CONFIG_PATH + ".world")) {
                UUID armorStandId = loadArmorStandId(config);
                Location glassLoc = loadLocation(config);

                if (armorStandId != null && glassLoc != null) {
                    // Check if the armor stand still exists in the world
                    World world = glassLoc.getWorld();
                    if (world != null) {
                        Entity entity = null;
                        for (Entity e : world.getEntities()) {
                            if (e.getUniqueId().equals(armorStandId) && e instanceof ArmorStand) {
                                entity = e;
                                break;
                            }
                        }

                        if (entity != null) {
                            // Re-register the armor stand
                            glassBlocks.put(glassLoc, armorStandId);

                            // Restart particles
                            playFancyParticles(armorStandId, glassLoc.add(0.5, 0.5, 0.5));

                            // Respawn hologram
                            spawnHologram(armorStandId, glassLoc.add(0, 2.2, 0));

                            RushLogger.info(i18n.log("internal.command.author.restored"));
                            return; // Only one author allowed
                        }
                    }
                }
            }

            // Scan for any author armor stand not in config (for backwards compatibility)
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof ArmorStand stand && stand.getScoreboardTags().contains(ARMOR_STAND_TAG)) {
                        UUID armorStandId = stand.getUniqueId();

                        // Skip if already restored from config
                        if (glassBlocks.containsValue(armorStandId)) {
                            continue;
                        }

                        // Find the glass block beneath this armor stand
                        Location standLoc = stand.getLocation();
                        Location glassLoc = findGlassBlockNear(world, standLoc);

                        if (glassLoc != null) {
                            // Re-register the armor stand
                            glassBlocks.put(glassLoc, armorStandId);

                            // Save to config for future restarts
                            saveAuthorLocation(glassLoc, armorStandId);

                            // Restart particles
                            playFancyParticles(armorStandId, glassLoc.add(0.5, 0.5, 0.5));

                            // Respawn hologram
                            spawnHologram(armorStandId, glassLoc.add(0, 2.2, 0));

                            RushLogger.info(i18n.log("internal.command.author.restored_scanned"));
                            return; // Only one author allowed
                        }
                    }
                }
            }
        }, 20L); // Delay 1 second to ensure worlds are fully loaded
    }

    private void saveAuthorLocation(Location glassLoc, UUID armorStandId) {
        ConfigManager configManager = plugin.getConfigManager();
        if (configManager == null)
            return;

        FileConfiguration config = configManager.getConfig();
        config.set(CONFIG_PATH + ".world", glassLoc.getWorld().getName());
        config.set(CONFIG_PATH + ".x", glassLoc.getX());
        config.set(CONFIG_PATH + ".y", glassLoc.getY());
        config.set(CONFIG_PATH + ".z", glassLoc.getZ());
        config.set(CONFIG_PATH + ".armorstand", armorStandId.toString());
        configManager.saveConfig();
    }

    private UUID loadArmorStandId(FileConfiguration config) {
        String idStr = config.getString(CONFIG_PATH + ".armorstand");
        if (idStr == null)
            return null;
        try {
            return UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Location loadLocation(FileConfiguration config) {
        String worldName = config.getString(CONFIG_PATH + ".world");
        if (worldName == null)
            return null;

        World world = plugin.getServer().getWorld(worldName);
        if (world == null)
            return null;

        double x = config.getDouble(CONFIG_PATH + ".x", 0);
        double y = config.getDouble(CONFIG_PATH + ".y", 64);
        double z = config.getDouble(CONFIG_PATH + ".z", 0);

        return new Location(world, x, y, z);
    }

    private void removeAuthorLocation() {
        ConfigManager configManager = plugin.getConfigManager();
        if (configManager == null)
            return;

        FileConfiguration config = configManager.getConfig();
        config.set(CONFIG_PATH, null);
        configManager.saveConfig();
    }

    private Location findGlassBlockNear(World world, Location center) {
        int searchY = center.getBlockY();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block block = world.getBlockAt(center.getBlockX() + dx, searchY, center.getBlockZ() + dz);
                if (isGlass(block.getType())) {
                    return block.getLocation();
                }
            }
        }
        return null;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("author")
                .then(Commands.literal("add")
                        .executes(ctx -> runAdd(ctx)))
                .then(Commands.literal("remove")
                        .executes(ctx -> runRemove(ctx)));
    }

    private int runAdd(CommandContext<CommandSourceStack> ctx) {
        return CommandManager.requirePlayer(ctx, player -> {
            Block glassBlock = findGlassBeneath(player);

            if (glassBlock == null) {
                player.sendMessage(text("Aucun bloc de verre trouvé sous vos pieds.", NamedTextColor.RED));
                return 0;
            }

            float yaw = getYawFacingPlayer(glassBlock, player);

            Location spawnLoc = glassBlock.getLocation().add(0.5, -1.25, 0.5);
            spawnLoc.setYaw(yaw);

            ArmorStand armorStand = player.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setSmall(false);
                stand.setBasePlate(false);
                stand.setArms(false);
                stand.addScoreboardTag(ARMOR_STAND_TAG);
            });

            final ItemBuilder<SkullMeta> skull = ItemBuilder.<SkullMeta>of(Material.PLAYER_HEAD);
            final PlayerProfile profile = Bukkit.createProfile(getAuthorName());

            profile.update().thenAccept(updatedProfile -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    skull.meta(m -> m.setPlayerProfile(updatedProfile));
                    armorStand.getEquipment().setHelmet(skull.build());
                });
            });

            playFancyParticles(armorStand.getUniqueId(), glassBlock.getLocation().add(0.5, 0.5, 0.5));

            spawnHologram(armorStand.getUniqueId(), glassBlock.getLocation().add(0.5, 2.2, 0.5));

            glassBlocks.put(glassBlock.getLocation(), armorStand.getUniqueId());

            saveAuthorLocation(glassBlock.getLocation(), armorStand.getUniqueId());

            player.sendMessage(text("Armor stand de " + getAuthorName() + " placé!", NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        });
    }

    private int runRemove(CommandContext<CommandSourceStack> ctx) {
        return CommandManager.requirePlayer(ctx, player -> {
            int removed = 0;

            for (Entity entity : player.getWorld().getEntities()) {
                if (entity instanceof ArmorStand stand && stand.getScoreboardTags().contains(ARMOR_STAND_TAG)) {
                    UUID standId = stand.getUniqueId();
                    BukkitTask task = particleTasks.remove(standId);
                    if (task != null) {
                        task.cancel();
                    }
                    TextHologram hologram = holograms.remove(standId);
                    if (hologram != null && plugin.getHologramManager() != null) {
                        plugin.getHologramManager().remove(hologram);
                    }
                    glassBlocks.values().remove(standId);

                    removeAuthorLocation();

                    stand.remove();
                    removed++;
                }
            }

            if (removed > 0) {
                player.sendMessage(text(removed + " armor stand(s) supprimé(s).", NamedTextColor.GREEN));
            } else {
                player.sendMessage(text("Aucun armor stand d'auteur trouvé.", NamedTextColor.RED));
            }

            return Command.SINGLE_SUCCESS;
        });
    }

    private float getYawFacingPlayer(Block block, Player player) {
        double dx = player.getLocation().getX() - (block.getX() + 0.5);
        double dz = player.getLocation().getZ() - (block.getZ() + 0.5);
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return (float) angle;
    }

    private Block findGlassBeneath(Player player) {
        Location loc = player.getLocation();
        int playerY = loc.getBlockY();
        Block closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                Block block = loc.getWorld().getBlockAt(loc.getBlockX() + x, playerY - 1, loc.getBlockZ() + z);

                if (isGlass(block.getType())) {
                    double dist = block.getLocation().add(0.5, 0, 0.5).distanceSquared(loc);
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = block;
                    }
                }
            }
        }

        return closest;
    }

    private void playFancyParticles(UUID armorStandId, Location center) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int step = 0;

            @Override
            public void run() {
                double progress = (double) step / PARTICLE_STEPS;

                // Rising spiral helix with END_ROD particles
                double spiralAngle = (2 * Math.PI * step * 2) / PARTICLE_STEPS;
                double spiralHeight = Math.sin(progress * 2 * Math.PI) * 0.5;
                double spiralX = center.getX() + (PARTICLE_RADIUS * 0.8) * Math.cos(spiralAngle);
                double spiralZ = center.getZ() + (PARTICLE_RADIUS * 0.8) * Math.sin(spiralAngle);
                Location spiralLoc = new Location(center.getWorld(), spiralX, center.getY() + spiralHeight, spiralZ);
                center.getWorld().spawnParticle(Particle.END_ROD, spiralLoc, 1, 0, 0, 0, 0);

                step = (step + 1) % PARTICLE_STEPS;
            }
        }, 0L, 1L);

        particleTasks.put(armorStandId, task);
    }

    private boolean isGlass(Material material) {
        String name = material.name();
        return material == Material.GLASS
                || material == Material.TINTED_GLASS
                || name.endsWith("_STAINED_GLASS");
    }

    private void spawnHologram(UUID armorStandId, Location location) {
        if (plugin.getHologramManager() == null) {
            return;
        }

        TextHologram hologram = new TextHologram("author_" + armorStandId.toString())
                .setMiniMessageText(
                        "<white>Construit & développé</white><br><white>par <gradient:#FFD700:#FF69B4><bold>"
                                + getAuthorName() + "</bold></gradient></white>")
                .setBillboard(Display.Billboard.CENTER)
                .setShadow(true)
                .setScale(1.2F, 1.2F, 1.2F)
                .setTextOpacity((byte) 255)
                .setBackgroundColor(Color.fromARGB(0, 0, 0, 0).asARGB())
                .setAlignment(TextDisplay.TextAlignment.CENTER)
                .setViewRange(0.5);

        plugin.getHologramManager().spawn(hologram, location);
        holograms.put(armorStandId, hologram);
    }

    public static void playCookieFountain(Location location) {
        final int COOKIE_COUNT = 20;
        final double SPREAD = 0.3;
        final double UPWARD_VELOCITY = 0.8;
        final int DESPAWN_TICKS = 40;

        for (int i = 0; i < COOKIE_COUNT; i++) {
            double angle = (2 * Math.PI * i) / COOKIE_COUNT;
            double radius = 0.2 + Math.random() * 0.3;
            double x = location.getX() + radius * Math.cos(angle);
            double z = location.getZ() + radius * Math.sin(angle);
            double y = location.getY() + 0.5;

            Location spawnLoc = new Location(location.getWorld(), x, y, z);
            Item cookie = location.getWorld().spawn(spawnLoc, Item.class, item -> {
                item.setItemStack(new ItemStack(Material.COOKIE));
                item.setPickupDelay(Integer.MAX_VALUE);
                item.setInvulnerable(true);
                item.setGravity(true);
            });

            double vx = SPREAD * Math.cos(angle) * (0.5 + Math.random() * 0.5);
            double vz = SPREAD * Math.sin(angle) * (0.5 + Math.random() * 0.5);
            double vy = UPWARD_VELOCITY + Math.random() * 0.4;
            cookie.setVelocity(new Vector(vx, vy, vz));

            Bukkit.getScheduler().runTaskLater(Main.getInstance(), cookie::remove, DESPAWN_TICKS);
        }
    }

    /**
     * Cleans up all author displays, holograms, and tasks.
     * Called during plugin shutdown.
     */
    public void cleanupAll() {
        // Cancel all particle tasks
        for (BukkitTask task : particleTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        particleTasks.clear();

        // Remove all holograms
        if (plugin.getHologramManager() != null) {
            for (TextHologram hologram : holograms.values()) {
                plugin.getHologramManager().remove(hologram);
            }
        }
        holograms.clear();

        // Remove all armor stands with our tag across all worlds
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand stand && stand.getScoreboardTags().contains(ARMOR_STAND_TAG)) {
                    stand.remove();
                }
            }
        }

        // Clear the glass blocks map
        glassBlocks.clear();

        // Note: We intentionally don't remove from config here,
        // as the author location should persist across restarts
    }
}
