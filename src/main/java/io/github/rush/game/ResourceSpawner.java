package io.github.rush.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

public class ResourceSpawner implements Runnable {

    private final Game game;
    private final ResourceType resourceType;
    private final Location location;

    public ResourceSpawner(Game game, ResourceType resourceType, Location location) {
        this.game = game;
        this.resourceType = resourceType;
        this.location = location.clone();
    }

    @Override
    public void run() {
        if (game.getState() != GameState.RUNNING) {
            return;
        }

        World world = location.getWorld();
        if (world == null) {
            return;
        }

        ItemStack itemStack = new ItemStack(resourceType.getMaterial(), 1);

        Location dropLocation = location.clone();
        dropLocation.setY(dropLocation.getY() + 1.0);

        Item droppedItem = world.dropItemNaturally(dropLocation, itemStack);
        droppedItem.setPickupDelay(0);
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public Location getLocation() {
        return location;
    }

    public long getIntervalTicks() {
        return resourceType.getSpawnIntervalTicks();
    }
}
