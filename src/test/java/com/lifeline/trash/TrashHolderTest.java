package com.lifeline.trash;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TrashHolderTest {

    @Test
    public void testTrashHolderInitialState() {
        UUID uuid = UUID.randomUUID();
        TrashHolder holder = new TrashHolder(uuid);

        assertEquals(uuid, holder.getOwnerUuid());
        assertFalse(holder.isConfirmed());
        assertNull(holder.getInventory());
    }

    @Test
    public void testTrashHolderConfirmation() {
        UUID uuid = UUID.randomUUID();
        TrashHolder holder = new TrashHolder(uuid);

        assertFalse(holder.isConfirmed());
        holder.setConfirmed(true);
        assertTrue(holder.isConfirmed());
        holder.setConfirmed(false);
        assertFalse(holder.isConfirmed());
    }

    @Test
    public void testTrashGUISlotConstants() {
        assertEquals(18, TrashGUI.DISPOSAL_SLOTS_COUNT);
        assertEquals(22, TrashGUI.CONFIRM_SLOT);
        assertEquals(18, TrashGUI.INFO_SLOT);
    }
}
