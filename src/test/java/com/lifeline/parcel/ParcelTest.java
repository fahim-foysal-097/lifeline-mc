package com.lifeline.parcel;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ParcelTest {

    @Test
    public void testActiveSlotsCalculation() {
        // Slot limits 1 to 9
        Set<Integer> slots1 = ParcelGUI.getActiveSlots(1);
        assertEquals(1, slots1.size());
        assertTrue(slots1.contains(13)); // centered

        Set<Integer> slots4 = ParcelGUI.getActiveSlots(4);
        assertEquals(4, slots4.size());
        assertEquals(Set.of(11, 12, 13, 14), slots4);

        Set<Integer> slots9 = ParcelGUI.getActiveSlots(9);
        assertEquals(9, slots9.size());
        assertEquals(Set.of(9, 10, 11, 12, 13, 14, 15, 16, 17), slots9);

        // Clamping under 1 -> 1
        Set<Integer> slots0 = ParcelGUI.getActiveSlots(0);
        assertEquals(1, slots0.size());

        // Clamping over 9 -> 9
        Set<Integer> slots20 = ParcelGUI.getActiveSlots(20);
        assertEquals(9, slots20.size());
    }

    @Test
    public void testParcelHolderState() {
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ParcelHolder holder = new ParcelHolder(sender, target, 4);

        assertEquals(sender, holder.getSenderUuid());
        assertEquals(target, holder.getTargetUuid());
        assertEquals(4, holder.getMaxSlots());
        assertFalse(holder.isDelivered());

        holder.setDelivered(true);
        assertTrue(holder.isDelivered());
        assertNull(holder.getInventory());
    }
}
