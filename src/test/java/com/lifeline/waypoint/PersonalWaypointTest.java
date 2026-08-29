package com.lifeline.waypoint;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PersonalWaypointTest {

    @Test
    public void testPersonalWaypointModel() {
        UUID owner = UUID.randomUUID();
        Waypoint wp = new Waypoint("MyHome", "world_nether", 123.0, 70.0, -456.0, 180.0f, 15.0f, owner, "Alex", 123456789L);

        assertEquals("MyHome", wp.getName());
        assertEquals("world_nether", wp.getWorldName());
        assertEquals(123.0, wp.getX());
        assertEquals(70.0, wp.getY());
        assertEquals(-456.0, wp.getZ());
        assertEquals(180.0f, wp.getYaw());
        assertEquals(15.0f, wp.getPitch());
        assertEquals(owner, wp.getCreatorUuid());
        assertEquals("Alex", wp.getCreatorName());
        assertEquals(123456789L, wp.getCreatedAt());

        Map<String, Object> serialized = wp.serialize();
        Waypoint deserialized = Waypoint.deserialize(serialized);

        assertEquals(wp.getName(), deserialized.getName());
        assertEquals(wp.getWorldName(), deserialized.getWorldName());
        assertEquals(wp.getX(), deserialized.getX(), 0.001);
        assertEquals(wp.getY(), deserialized.getY(), 0.001);
        assertEquals(wp.getZ(), deserialized.getZ(), 0.001);
        assertEquals(wp.getYaw(), deserialized.getYaw(), 0.001);
        assertEquals(wp.getPitch(), deserialized.getPitch(), 0.001);
        assertEquals(wp.getCreatorUuid(), deserialized.getCreatorUuid());
        assertEquals(wp.getCreatorName(), deserialized.getCreatorName());
        assertEquals(wp.getCreatedAt(), deserialized.getCreatedAt());
    }

    @Test
    public void testMaxLimitConstant() {
        assertEquals(27, PersonalWaypointManager.MAX_PERSONAL_WAYPOINTS);
    }
}
