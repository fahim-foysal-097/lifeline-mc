package com.lifeline.tether;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TetherRequestTest {

    @Test
    public void testTetherRequestExpiration() {
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        long now = System.currentTimeMillis();

        // Expired request
        TetherRequest expiredReq = new TetherRequest(sender, "Sender", target, "Target", now - 10000, now - 1000);
        assertTrue(expiredReq.isExpired());
        assertEquals(0, expiredReq.getRemainingSeconds());

        // Active request
        TetherRequest activeReq = new TetherRequest(sender, "Sender", target, "Target", now, now + 30000);
        assertFalse(activeReq.isExpired());
        assertTrue(activeReq.getRemainingSeconds() > 0);
    }
}
