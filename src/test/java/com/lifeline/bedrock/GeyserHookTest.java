package com.lifeline.bedrock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GeyserHookTest {

    @Test
    public void testIsBedrockPlayerNullPlayer() {
        assertFalse(GeyserHook.isBedrockPlayer(null));
    }

    @Test
    public void testOpenFormsNullSafe() {
        assertDoesNotThrow(() -> GeyserHook.openWaypointsForm(null, null, null));
        assertDoesNotThrow(() -> GeyserHook.openCreateWaypointForm(null, null, null));
        assertDoesNotThrow(() -> GeyserHook.openWaypointDetailsForm(null, null, null, null));
        assertDoesNotThrow(() -> GeyserHook.openTetherForm(null, null, null));
    }
}
