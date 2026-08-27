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

    @Test
    public void testClickButtonPlaceholderResolution() {
        String template = "<green><bold><click:run_command:'/tpq accept <player>'><hover:show_text:'<green>Click to accept teleport request from <player></green>'>[✔ ACCEPT]</click></hover></bold></green>";
        String resolved = template.replace("<player>", "TestPlayer");

        net.kyori.adventure.text.Component component = com.lifeline.util.MessageUtil.parse(resolved);
        assertNotNull(component);

        // Verify that parsing unparsed placeholder doesn't crash or break
        com.lifeline.util.MessageUtil.load(new org.bukkit.configuration.file.YamlConfiguration());
        net.kyori.adventure.text.Component parsed = com.lifeline.util.MessageUtil.parse("<yellow><player></yellow>",
                com.lifeline.util.MessageUtil.unparsed("player", "<player>"));
        assertNotNull(parsed);
    }
}
