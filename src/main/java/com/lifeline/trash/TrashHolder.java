package com.lifeline.trash;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder implementation for identifying an active Quick Trash inventory
 * and tracking its confirmation state.
 */
public class TrashHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private boolean confirmed = false;

    public TrashHolder(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
