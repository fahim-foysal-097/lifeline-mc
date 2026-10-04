package com.lifeline.synergy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SynergyTest {

    @Test
    public void testDistanceSquaredCalculation() {
        double maxDist = 20.0;
        double maxDistSq = maxDist * maxDist;
        assertEquals(400.0, maxDistSq);

        // Within 15 blocks
        double dx = 10.0;
        double dz = 10.0;
        double distSq = dx * dx + dz * dz; // 200.0
        assertTrue(distSq <= maxDistSq);

        // Outside 25 blocks
        dx = 20.0;
        dz = 20.0;
        distSq = dx * dx + dz * dz; // 800.0
        assertFalse(distSq <= maxDistSq);
    }
}
