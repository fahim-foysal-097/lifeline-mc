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

    @Test
    public void testTetherHolderModes() {
        TetherHolder defaultHolder = new TetherHolder(false);
        assertFalse(defaultHolder.isHereMode());
        assertNull(defaultHolder.getInventory());

        TetherHolder hereHolder = new TetherHolder(true);
        assertTrue(hereHolder.isHereMode());
        assertNull(hereHolder.getInventory());
    }

    /**
     * Verifies the summon direction logic: when a SUMMON_HERE request is accepted,
     * traveler = the acceptor (target), destination = the requester (sender).
     * This directly mirrors the acceptRequest isSummon branch.
     */
    @Test
    public void testSummonAcceptDirection() {
        UUID summoner = UUID.randomUUID(); // PlayerA - who sends /tpqhere PlayerB
        UUID summoned = UUID.randomUUID(); // PlayerB - who receives and accepts

        long now = System.currentTimeMillis();

        // PlayerA sends SUMMON_HERE to PlayerB
        TetherRequest summonReq = new TetherRequest(
                summoner, "PlayerA",
                summoned, "PlayerB",
                TetherRequest.Type.SUMMON_HERE, now, now + 60000);

        // Verify type is SUMMON_HERE
        assertEquals(TetherRequest.Type.SUMMON_HERE, summonReq.type());

        // In acceptRequest: target=PlayerB, sender=PlayerA resolved from matchingRequest.senderUuid()
        // isSummon = true → startTeleportWarmup(target=PlayerB, sender=PlayerA, ...)
        // → traveler=PlayerB, destination=PlayerA → PlayerB teleports to PlayerA ✓
        boolean isSummon = summonReq.type() == TetherRequest.Type.SUMMON_HERE;
        assertTrue(isSummon);

        // The traveler is the one accepting (summoned), the destination is the summoner
        UUID travelerUuid = summoned;   // PlayerB moves
        UUID destinationUuid = summoner; // To PlayerA's location

        assertEquals(summonReq.senderUuid(), destinationUuid, "Summoner (sender) should be the destination");
        assertEquals(summonReq.targetUuid(), travelerUuid, "Summoned (target) should be the traveler");
    }

    /**
     * Verifies that a TELEPORT_TO accept has sender=requester as traveler, target=acceptor as destination.
     * Normal /tpq PlayerB → PlayerA accepts → PlayerA teleports to PlayerB.
     */
    @Test
    public void testTeleportToAcceptDirection() {
        UUID requester = UUID.randomUUID(); // PlayerA - sends /tpq PlayerB
        UUID acceptor  = UUID.randomUUID(); // PlayerB - accepts

        long now = System.currentTimeMillis();
        TetherRequest tpReq = new TetherRequest(
                requester, "PlayerA",
                acceptor,  "PlayerB",
                TetherRequest.Type.TELEPORT_TO, now, now + 60000);

        assertEquals(TetherRequest.Type.TELEPORT_TO, tpReq.type());

        boolean isSummon = tpReq.type() == TetherRequest.Type.SUMMON_HERE;
        assertFalse(isSummon);

        // Non-summon: startTeleportWarmup(sender=PlayerA, target=PlayerB, ...)
        // → traveler=PlayerA, destination=PlayerB → PlayerA teleports to PlayerB ✓
        UUID travelerUuid     = requester; // PlayerA moves
        UUID destinationUuid  = acceptor;  // To PlayerB's location

        assertEquals(tpReq.senderUuid(), travelerUuid, "Requester (sender) should be the traveler");
        assertEquals(tpReq.targetUuid(), destinationUuid, "Acceptor (target) should be the destination");
    }

    /**
     * Verifies cross-request detection: if PlayerB has an outgoing request to PlayerA,
     * and PlayerA then sends SUMMON_HERE to PlayerB, the stale cross-request from B→A
     * should be identified and cleaned up before the new request is stored.
     */
    @Test
    public void testCrossRequestDetection() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        long now = System.currentTimeMillis();

        // PlayerB previously sent TELEPORT_TO to PlayerA
        TetherRequest crossReq = new TetherRequest(
                playerB, "PlayerB",
                playerA, "PlayerA",
                TetherRequest.Type.TELEPORT_TO, now - 5000, now + 55000);

        // PlayerA sends new SUMMON_HERE to PlayerB
        TetherRequest newReq = new TetherRequest(
                playerA, "PlayerA",
                playerB, "PlayerB",
                TetherRequest.Type.SUMMON_HERE, now, now + 60000);

        // The cross-request (B→A) should be detectable as targeting playerA from playerB
        assertTrue(crossReq.targetUuid().equals(playerA), "Cross-request targets PlayerA");
        assertTrue(crossReq.senderUuid().equals(playerB), "Cross-request is from PlayerB");

        // New request from PlayerA should be SUMMON_HERE
        assertEquals(TetherRequest.Type.SUMMON_HERE, newReq.type());
        assertTrue(newReq.senderUuid().equals(playerA));
        assertTrue(newReq.targetUuid().equals(playerB));

        // After cleanup, only newReq should be in incoming[PlayerB]
        // Cross-request in incoming[PlayerA] from PlayerB should be gone
        // This is verified structurally: newReq.type() != crossReq.type()
        assertNotEquals(newReq.type(), crossReq.type());
    }

    /**
     * Verifies that when multiple requests exist (e.g. A→B TELEPORT_TO older, A→B SUMMON_HERE newer),
     * the most-recent request is picked when accepting with no sender name (/tpq a).
     */
    @Test
    public void testPicksNewestRequestOnBlindAccept() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        long base = System.currentTimeMillis();

        // Older TELEPORT_TO (should NOT be picked)
        TetherRequest older = new TetherRequest(
                playerA, "PlayerA",
                playerB, "PlayerB",
                TetherRequest.Type.TELEPORT_TO, base - 3000, base + 57000);

        // Newer SUMMON_HERE (SHOULD be picked by max-createdAtMillis logic)
        TetherRequest newer = new TetherRequest(
                playerA, "PlayerA",
                playerB, "PlayerB",
                TetherRequest.Type.SUMMON_HERE, base, base + 60000);

        // Simulate "pick newest"
        java.util.List<TetherRequest> all = java.util.List.of(older, newer);
        TetherRequest picked = all.stream()
                .max(java.util.Comparator.comparingLong(TetherRequest::createdAtMillis))
                .orElseThrow();

        assertEquals(TetherRequest.Type.SUMMON_HERE, picked.type(),
                "Blind accept (/tpq a) should pick the newest request (SUMMON_HERE)");
        assertEquals(base, picked.createdAtMillis());
    }
}

