package io.github.rush.game;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import lombok.Getter;

public class ResourceSpawner implements Runnable {

    private final Game game;
    @Getter
    private final ResourceType resourceType;
    @Getter
    private final Location location;

    public ResourceSpawner(Game game, ResourceType resourceType, Location location) {
        this.game = game;
        this.resourceType = resourceType;
        this.location = location.clone();
    }

    @Override
    public void run() {
        if (game.getState() == GameState.RUNNING) {
            final ItemStack itemStack = new ItemStack(resourceType.getMaterial(), 1);
            final Location dropLocation = location.clone();

            dropLocation.setY(dropLocation.getY() + 1.0);
            location.getWorld().dropItem(dropLocation, itemStack).setPickupDelay(0);
        }
    }
}
