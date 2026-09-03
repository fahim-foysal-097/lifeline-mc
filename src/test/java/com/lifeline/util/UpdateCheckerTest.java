package com.lifeline.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateCheckerTest {

    @Test
    public void testVersionComparison() {
        // Equal versions
        assertEquals(0, UpdateChecker.compareVersions("1.0.0", "1.0.0"));
        assertEquals(0, UpdateChecker.compareVersions("v1.0.0", "1.0.0"));
        assertEquals(0, UpdateChecker.compareVersions("1.0.0", "v1.0.0"));
        assertEquals(0, UpdateChecker.compareVersions("v2.1.4", "V2.1.4"));

        // Outdated versions (current < latest -> negative)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.0.1") < 0);
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.1.0") < 0);
        assertTrue(UpdateChecker.compareVersions("1.0.9", "1.1.0") < 0);
        assertTrue(UpdateChecker.compareVersions("1.0.0", "2.0.0") < 0);
        assertTrue(UpdateChecker.compareVersions("v1.0.0", "v1.0.1") < 0);
        assertTrue(UpdateChecker.compareVersions("1.0.0-SNAPSHOT", "1.0.0") < 0);

        // Ahead of release (current > latest -> positive)
        assertTrue(UpdateChecker.compareVersions("1.0.1", "1.0.0") > 0);
        assertTrue(UpdateChecker.compareVersions("1.1.0", "1.0.9") > 0);
        assertTrue(UpdateChecker.compareVersions("2.0.0", "1.9.9") > 0);
        assertTrue(UpdateChecker.compareVersions("1.0.1-SNAPSHOT", "1.0.0") > 0);
        assertTrue(UpdateChecker.compareVersions("1.0.0-beta", "0.9.9") > 0);
    }
}
