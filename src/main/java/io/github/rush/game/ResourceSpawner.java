package io.github.rush.game;

import io.github.rush.Main;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@Getter
@Setter
public class ResourceSpawner implements Runnable {

    private final Main plugin;
    private final String name;
    private final Location location;
    private final int interval;
    private final List<ItemStack> resources;

    public ResourceSpawner(Main plugin, String name, Location location) {
        this.plugin = plugin;
        this.name = name;
        this.location = location;
        this.interval = plugin.getConfig().getInt("resource." + name + ".spawn-interval", 10000);
        this.resources = new ArrayList<>();

        loadResources(name);
    }

    private void loadResources(String name) {
        final List<?> configList = plugin.getConfig().getList("resource." + name + ".item");

        if (configList == null)
            return;

        for (Object obj : configList) {
            if (obj instanceof MemorySection section) {
                final Material material = Material.getMaterial(section.getString("type"));

                if (material != null) {
                    final ItemStack item = new ItemStack(material, section.getInt("amount", 1));

                    if (section.contains("meta")) {
                        final ItemMeta meta = plugin.getServer().getItemFactory().getItemMeta(material);

                        if (section.contains("meta.display-name")) {
                            meta.displayName(Component.text(section.getString("meta.display-name")));
                        }

                        item.setItemMeta(meta);
                    }

                    resources.add(item);
                }
            }
        }
    }

    @Override
    public void run() {
        final World world = location.getWorld();
        final Location dropLocation = location.clone().add(0, 1, 0);

        for (ItemStack itemStack : resources) {
            final Item dropped = world.dropItemNaturally(dropLocation, itemStack.clone());
            dropped.setPickupDelay(0);
        }
    }

    public int getInterval() {
        return interval;
    }

    public String getName() {
        return name;
    }

    public void setGame(Game game) {
    }
}
