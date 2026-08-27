package com.lifeline.revive;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DownedStateTest {

    @Test
    public void testDownedStateCountdown() {
        UUID uuid = UUID.randomUUID();
        DownedState state = new DownedState(uuid, 45);

        assertEquals(uuid, state.getDownedPlayerUuid());
        assertEquals(45, state.getRemainingSeconds());

        state.decrementSeconds();
        assertEquals(44, state.getRemainingSeconds());

        state.setRemainingSeconds(10);
        assertEquals(10, state.getRemainingSeconds());
    }

    @Test
    public void testReviveProgress() {
        UUID uuid = UUID.randomUUID();
        DownedState state = new DownedState(uuid, 30);

        UUID reviver = UUID.randomUUID();
        state.setActiveReviverUuid(reviver);
        assertEquals(reviver, state.getActiveReviverUuid());

        state.setReviveProgressTicks(20);
        assertEquals(20, state.getReviveProgressTicks());
    }
}
