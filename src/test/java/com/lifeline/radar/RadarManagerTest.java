package com.lifeline.radar;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RadarManagerTest {

    @Test
    public void testDirectionalArrowsFacingNorth() {
        // Viewer facing North (yaw = 180) at (0, 64, 0)
        Location viewer = new Location(null, 0, 64, 0, 180f, 0f);

        // Target straight North (dz = -10, dx = 0) -> Ahead (↑)
        assertEquals("↑", RadarManager.calculateDirectionArrow(viewer, new Location(null, 0, 64, -10)));

        // Target North-East (dz = -10, dx = 10) -> Ahead-Right (⬈)
        assertEquals("⬈", RadarManager.calculateDirectionArrow(viewer, new Location(null, 10, 64, -10)));

        // Target East (dz = 0, dx = 10) -> Right (→)
        assertEquals("→", RadarManager.calculateDirectionArrow(viewer, new Location(null, 10, 64, 0)));

        // Target South-East (dz = 10, dx = 10) -> Behind-Right (⬊)
        assertEquals("⬊", RadarManager.calculateDirectionArrow(viewer, new Location(null, 10, 64, 10)));

        // Target South (dz = 10, dx = 0) -> Behind (↓)
        assertEquals("↓", RadarManager.calculateDirectionArrow(viewer, new Location(null, 0, 64, 10)));

        // Target South-West (dz = 10, dx = -10) -> Behind-Left (⬋)
        assertEquals("⬋", RadarManager.calculateDirectionArrow(viewer, new Location(null, -10, 64, 10)));

        // Target West (dz = 0, dx = -10) -> Left (←)
        assertEquals("←", RadarManager.calculateDirectionArrow(viewer, new Location(null, -10, 64, 0)));

        // Target North-West (dz = -10, dx = -10) -> Ahead-Left (⬉)
        assertEquals("⬉", RadarManager.calculateDirectionArrow(viewer, new Location(null, -10, 64, -10)));
    }

    @Test
    public void testDirectionalArrowsFacingEast() {
        // Viewer facing East (yaw = -90 or 270) at (0, 64, 0)
        Location viewer = new Location(null, 0, 64, 0, -90f, 0f);

        // Target straight East (dx = 10, dz = 0) -> Ahead (↑)
        assertEquals("↑", RadarManager.calculateDirectionArrow(viewer, new Location(null, 10, 64, 0)));

        // Target South (dx = 0, dz = 10) -> Right (→)
        assertEquals("→", RadarManager.calculateDirectionArrow(viewer, new Location(null, 0, 64, 10)));

        // Target West (dx = -10, dz = 0) -> Behind (↓)
        assertEquals("↓", RadarManager.calculateDirectionArrow(viewer, new Location(null, -10, 64, 0)));

        // Target North (dx = 0, dz = -10) -> Left (←)
        assertEquals("←", RadarManager.calculateDirectionArrow(viewer, new Location(null, 0, 64, -10)));
    }

    @Test
    public void testSameLocationOrCloseDistance() {
        Location viewer = new Location(null, 100, 64, 100, 0f, 0f);
        Location target = new Location(null, 100.1, 64, 100.1);

        // Closer than 0.5 blocks horizontal -> returns dot "●"
        assertEquals("●", RadarManager.calculateDirectionArrow(viewer, target));
    }
}
