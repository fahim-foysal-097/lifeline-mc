package com.lifeline.vault;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PersonalVaultTest {

    @Test
    public void testPersonalVaultHolder() {
        UUID uuid = UUID.randomUUID();
        PersonalVaultHolder holder = new PersonalVaultHolder(uuid);

        assertEquals(uuid, holder.getOwnerUuid());
        assertNull(holder.getInventory());
    }
}
