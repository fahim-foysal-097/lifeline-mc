package com.lifeline.tether;

import java.util.UUID;

/**
 * Represents an active, pending teleport request between two players.
 */
public record TetherRequest(
        UUID senderUuid,
        String senderName,
        UUID targetUuid,
        String targetName,
        Type type,
        long createdAtMillis,
        long expiryMillis
) {
    public enum Type {
        TELEPORT_TO,
        SUMMON_HERE
    }

    /**
     * Backwards-compatible constructor defaulting to {@link Type#TELEPORT_TO}.
     */
    public TetherRequest(
            UUID senderUuid,
            String senderName,
            UUID targetUuid,
            String targetName,
            long createdAtMillis,
            long expiryMillis
    ) {
        this(senderUuid, senderName, targetUuid, targetName, Type.TELEPORT_TO, createdAtMillis, expiryMillis);
    }

    /**
     * Checks if this teleport request has expired.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryMillis;
    }

    /**
     * Returns remaining seconds before expiration.
     */
    public long getRemainingSeconds() {
        long remaining = (expiryMillis - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }
}
