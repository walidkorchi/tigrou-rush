package io.github.rush.game;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

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
            dropLocation.setX(dropLocation.getX() + 0.5);
            dropLocation.setZ(dropLocation.getZ() + 0.5);

            location.getWorld().dropItem(dropLocation, itemStack, item -> {
                item.setVelocity(new Vector(0, 0, 0));
                item.setPickupDelay(0);
            });
        }
    }
}
