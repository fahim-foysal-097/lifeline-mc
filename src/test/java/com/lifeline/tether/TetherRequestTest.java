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
    public void testTetherRequestTypes() {
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        long now = System.currentTimeMillis();

        // Default constructor should default to TELEPORT_TO
        TetherRequest defaultReq = new TetherRequest(sender, "Sender", target, "Target", now, now + 30000);
        assertEquals(TetherRequest.Type.TELEPORT_TO, defaultReq.type());

        // Explicit constructor with SUMMON_HERE
        TetherRequest summonReq = new TetherRequest(sender, "Sender", target, "Target", TetherRequest.Type.SUMMON_HERE, now, now + 30000);
        assertEquals(TetherRequest.Type.SUMMON_HERE, summonReq.type());
        assertFalse(summonReq.isExpired());
        assertTrue(summonReq.getRemainingSeconds() > 0);
    }

    @Test
    public void testClickButtonPlaceholderResolution() {
        String template = "<green><bold><click:run_command:'/tpq accept <player>'><hover:show_text:'<green>Click to accept teleport request from <player></green>'>[✔ ACCEPT]</click></hover></bold></green>";
        String resolved = template.replace("<player>", "TestPlayer");

        net.kyori.adventure.text.Component component = com.lifeline.util.MessageUtil.parse(resolved);
        assertNotNull(component);

        // Verify summon button template
        String summonTemplate = "<green><bold><click:run_command:'/tpq accept <player>'><hover:show_text:'<green>Click to accept summon from <player></green>'>[✔ ACCEPT]</click></hover></bold></green>";
        String summonResolved = summonTemplate.replace("<player>", "TestPlayer");
        net.kyori.adventure.text.Component summonComponent = com.lifeline.util.MessageUtil.parse(summonResolved);
        assertNotNull(summonComponent);

        // Verify that parsing unparsed placeholder doesn't crash or break
        com.lifeline.util.MessageUtil.load(new org.bukkit.configuration.file.YamlConfiguration());
        net.kyori.adventure.text.Component parsed = com.lifeline.util.MessageUtil.parse("<yellow><player></yellow>",
                com.lifeline.util.MessageUtil.unparsed("player", "<player>"));
        assertNotNull(parsed);
    }

    @Test
    public void testAllSummonMessagesLoadAndParse() throws Exception {
        java.io.InputStream stream = getClass().getResourceAsStream("/messages.yml");
        assertNotNull(stream, "messages.yml must exist in classpath");

        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);

            String[] requiredKeys = {
                "teleport.usage-here",
                "teleport.sender-spectator",
                "teleport.summon-sent-sender",
                "teleport.summon-received-target",
                "teleport.summon-received-buttons",
                "teleport.summon-accept-target",
                "teleport.summon-accept-sender",
                "teleport.summon-deny-target",
                "teleport.summon-deny-sender",
                "teleport.summon-cancel-outgoing",
                "teleport.summon-expired-sender",
                "teleport.summon-warmup-traveler",
                "teleport.summon-warmup-summoner",
                "teleport.summon-success-traveler",
                "teleport.summon-success-summoner",
                "teleport.head-lore-summon-footer",
                "bedrock.tpq-action-title",
                "bedrock.tpq-action-content",
                "bedrock.tpq-action-tp-btn",
                "bedrock.tpq-action-summon-btn",
                "bedrock.tpq-action-back-btn",
                "help.tpqhere"
            };

            for (String key : requiredKeys) {
                assertTrue(yaml.contains(key), "Missing key in messages.yml: " + key);
                String val = yaml.getString(key);
                assertNotNull(val, "Key has null value: " + key);
                assertFalse(val.isBlank(), "Key has blank value: " + key);

                // Ensure it can be parsed as MiniMessage without syntax errors
                net.kyori.adventure.text.Component comp = com.lifeline.util.MessageUtil.parse(val,
                        com.lifeline.util.MessageUtil.p("player", "Alice"),
                        com.lifeline.util.MessageUtil.p("seconds", "3"),
                        com.lifeline.util.MessageUtil.p("dim", "Overworld"),
                        com.lifeline.util.MessageUtil.p("dist", "20m"),
                        com.lifeline.util.MessageUtil.p("health", "20"));
                assertNotNull(comp);
            }
        }
    }
}
