package com.lifeline.waypoint;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class WaypointTest {

    @Test
    public void testSerializationAndDeserialization() {
        UUID creatorUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Waypoint wp = new Waypoint("Base", "world", 100.5, 64.0, -200.5, 90.0f, 0.0f, creatorUuid, "Steve", now);

        assertEquals("Base", wp.getName());
        assertEquals("world", wp.getWorldName());
        assertEquals(100.5, wp.getX());
        assertEquals(64.0, wp.getY());
        assertEquals(-200.5, wp.getZ());
        assertEquals(90.0f, wp.getYaw());
        assertEquals(0.0f, wp.getPitch());
        assertEquals(creatorUuid, wp.getCreatorUuid());
        assertEquals("Steve", wp.getCreatorName());
        assertEquals(now, wp.getCreatedAt());

        Map<String, Object> serialized = wp.serialize();
        assertNotNull(serialized);

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
}
