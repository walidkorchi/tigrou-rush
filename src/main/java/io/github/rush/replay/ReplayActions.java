package io.github.rush.replay;

import java.util.UUID;

import org.bukkit.inventory.EquipmentSlot;

record MoveAction(long timestamp, UUID uuid, double x, double y, double z,
                         float yaw, float pitch,
                         String mainHand, String offHand, boolean sneaking)
        implements ReplayAction {}

record BlockChangeAction(long timestamp, int x, int y, int z, String worldName, String newMaterial, String oldMaterial)
        implements ReplayAction {}

record DeathAction(long timestamp, UUID uuid) implements ReplayAction {}

record RespawnAction(long timestamp, UUID uuid, double x, double y, double z) implements ReplayAction {}

record PhaseAction(long timestamp, String phaseName) implements ReplayAction {}

record BedDestroyAction(long timestamp, String teamColorName, UUID destroyerUuid) implements ReplayAction {}

record ChatAction(long timestamp, UUID uuid, String message)
        implements ReplayAction {}

record ArmSwingAction(long timestamp, UUID uuid, EquipmentSlot hand)
        implements ReplayAction {}

record DamageAction(long timestamp, UUID victimUuid, UUID attackerUuid, String cause, double amount)
        implements ReplayAction {}

record DropItemAction(long timestamp, UUID uuid, String material, int amount,
                      double x, double y, double z, float yaw,
                      double velX, double velY, double velZ)
        implements ReplayAction {}

record TntIgniteAction(long timestamp, double x, double y, double z, int fuseTicks)
        implements ReplayAction {}
