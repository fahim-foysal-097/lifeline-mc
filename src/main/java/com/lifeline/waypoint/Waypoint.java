package com.lifeline.waypoint;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data model for a shared waypoint.
 */
public class Waypoint {

    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final UUID creatorUuid;
    private final String creatorName;
    private final long createdAt;

    public Waypoint(String name, String worldName, double x, double y, double z, float yaw, float pitch, UUID creatorUuid, String creatorName, long createdAt) {
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.createdAt = createdAt;
    }

    public static Waypoint fromLocation(String name, Location location, UUID creatorUuid, String creatorName) {
        return new Waypoint(
                name,
                location.getWorld() != null ? location.getWorld().getName() : "world",
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                creatorUuid,
                creatorName,
                System.currentTimeMillis()
        );
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Converts this Waypoint to a Bukkit Location. Returns null if world is not loaded.
     */
    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Serializes waypoint to Map for YAML persistence.
     */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("world", worldName);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("yaw", (double) yaw);
        map.put("pitch", (double) pitch);
        map.put("creatorUuid", creatorUuid != null ? creatorUuid.toString() : null);
        map.put("creatorName", creatorName);
        map.put("createdAt", createdAt);
        return map;
    }

    /**
     * Deserializes waypoint from Map.
     */
    public static Waypoint deserialize(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("Waypoint data map cannot be null");
        }
        String name = (String) map.get("name");
        String worldName = (String) map.get("world");
        Object xObj = map.get("x");
        Object yObj = map.get("y");
        Object zObj = map.get("z");
        if (name == null || worldName == null || !(xObj instanceof Number) || !(yObj instanceof Number) || !(zObj instanceof Number)) {
            throw new IllegalArgumentException("Waypoint entry missing required fields (name, world, x, y, z)");
        }
        double x = ((Number) xObj).doubleValue();
        double y = ((Number) yObj).doubleValue();
        double z = ((Number) zObj).doubleValue();
        float yaw = ((Number) map.getOrDefault("yaw", 0.0)).floatValue();
        float pitch = ((Number) map.getOrDefault("pitch", 0.0)).floatValue();
        String uuidStr = (String) map.get("creatorUuid");
        UUID creatorUuid = null;
        if (uuidStr != null) {
            try {
                creatorUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String creatorName = (String) map.getOrDefault("creatorName", "Unknown");
        long createdAt = ((Number) map.getOrDefault("createdAt", System.currentTimeMillis())).longValue();

        return new Waypoint(name, worldName, x, y, z, yaw, pitch, creatorUuid, creatorName, createdAt);
    }
}
