package io.github.rush.entities;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;

import java.util.UUID;

public record GamePlayer(Player player) implements GameCombatant {
    public UUID uniqueId()                      { return player.getUniqueId(); }
    public String name()                         { return player.getName(); }
    public Location location()                   { return player.getLocation(); }
    public World world()                         { return player.getWorld(); }
    public boolean teleport(Location location)   { return player.teleport(location); }
    public void remove()                         { player.remove(); }
    public boolean dead()                        { return player.isDead(); }
    public boolean valid()                       { return player.isValid(); }
    public double health()                       { return player.getHealth(); }
    public void health(double health)            { player.setHealth(health); }
    public EntityEquipment equipment()           { return player.getEquipment(); }
    public AttributeInstance attribute(Attribute a) { return player.getAttribute(a); }
    public void setFallDistance(float distance)  { player.setFallDistance(distance); }
}
