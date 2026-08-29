package com.lifeline.vault;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder implementation for identifying a player's personal stash inventory.
 */
public class PersonalVaultHolder implements InventoryHolder {

    private final UUID ownerUuid;

    public PersonalVaultHolder(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
