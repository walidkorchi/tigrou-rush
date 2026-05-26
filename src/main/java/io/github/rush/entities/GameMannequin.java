package io.github.rush.entities;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EntityEquipment;

import java.util.UUID;

public record GameMannequin(Mannequin mannequin) implements GameCombatant {
    public UUID uniqueId()                      { return mannequin.getUniqueId(); }
    public String name()                         { return mannequin.getName(); }
    public Location location()                   { return mannequin.getLocation(); }
    public World world()                         { return mannequin.getWorld(); }
    public boolean teleport(Location location)   { return mannequin.teleport(location); }
    public void remove()                         { mannequin.remove(); }
    public boolean dead()                        { return mannequin.isDead(); }
    public boolean valid()                       { return mannequin.isValid(); }
    public double health()                       { return mannequin.getHealth(); }
    public void health(double health)            { mannequin.setHealth(health); }
    public EntityEquipment equipment()           { return mannequin.getEquipment(); }
    public AttributeInstance attribute(Attribute a) { return mannequin.getAttribute(a); }
    public void setFallDistance(float distance)  { mannequin.setFallDistance(distance); }
}
