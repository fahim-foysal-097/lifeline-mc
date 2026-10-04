package com.lifeline.tether;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * InventoryHolder for the Tether GUI window, preserving whether
 * the window was opened in normal (/tpq) or inverted (/tpqhere) mode.
 */
public class TetherHolder implements InventoryHolder {

    private final boolean hereMode;

    public TetherHolder(boolean hereMode) {
        this.hereMode = hereMode;
    }

    public boolean isHereMode() {
        return hereMode;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
