package io.github.rush.game;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import lombok.Getter;

import java.util.Random;

public class ResourceSpawner implements Runnable {

    private static final Random RANDOM = new Random();

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
            double spread = resourceType.getSpread();

            for (ResourceType.ResourceDrop drop : resourceType.getDrops()) {
                final ItemStack itemStack = new ItemStack(drop.material(), drop.amount());
                final Location dropLocation = location.clone();

                dropLocation.setY(dropLocation.getY() + 1.0);
                dropLocation.setX(dropLocation.getX() + 0.5);
                dropLocation.setZ(dropLocation.getZ() + 0.5);

                if (spread > 0) {
                    dropLocation.add(
                            (RANDOM.nextDouble() - 0.5) * spread,
                            0,
                            (RANDOM.nextDouble() - 0.5) * spread
                    );
                }

                location.getWorld().dropItem(dropLocation, itemStack, item -> {
                    item.setVelocity(new Vector(0, 0, 0));
                    item.setPickupDelay(0);
                });
            }
        }
    }
}
