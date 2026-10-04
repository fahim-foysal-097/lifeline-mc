package com.lifeline.parcel;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder implementation for identifying an active Item Parcel Delivery inventory,
 * tracking sender and recipient UUIDs, slot capacity, and delivery status.
 */
public class ParcelHolder implements InventoryHolder {

    private final UUID senderUuid;
    private final UUID targetUuid;
    private final int maxSlots;
    private boolean delivered = false;

    public ParcelHolder(UUID senderUuid, UUID targetUuid, int maxSlots) {
        this.senderUuid = senderUuid;
        this.targetUuid = targetUuid;
        this.maxSlots = maxSlots;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
