package io.github.rush.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.EntityEquipment;

import java.util.UUID;

public sealed interface GameParticipant permits GamePlayer, GameMannequin {
    UUID uniqueId();
    String name();
    Location location();
    World world();
    boolean teleport(Location location);
    void remove();
    boolean dead();
    boolean valid();
    double health();
    void health(double health);
    EntityEquipment equipment();
    AttributeInstance attribute(Attribute attribute);
    void setFallDistance(float distance);
}
