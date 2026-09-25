package com.lifeline.tether;

import com.lifeline.Lifeline;
import com.lifeline.config.PluginConfig;
import com.lifeline.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player-to-player teleport requests (/tpq, /teleportgui),
 * expiration, acceptance, warmups, anti-movement checks, and safe teleportation.
 */
public class TetherManager implements Listener {

    private final Lifeline plugin;

    // Outgoing requests: senderUuid -> TetherRequest
    private final Map<UUID, TetherRequest> outgoingRequests = new ConcurrentHashMap<>();

    // Incoming requests: targetUuid -> (senderUuid -> TetherRequest)
    private final Map<UUID, Map<UUID, TetherRequest>> incomingRequests = new ConcurrentHashMap<>();

    // Active teleport warmups: senderUuid -> BukkitTask
    private final Map<UUID, BukkitTask> activeWarmups = new ConcurrentHashMap<>();
    private final Map<UUID, Location> warmupStartLocations = new ConcurrentHashMap<>();

    // Player cooldowns: senderUuid -> timestamp in millis when cooldown ends
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public TetherManager(Lifeline plugin) {
        this.plugin = plugin;
    }

    public boolean isWarmingUp(UUID uuid) {
        return uuid != null && activeWarmups.containsKey(uuid);
    }

    /**
     * Sends a teleport request from sender to target defaulting to TELEPORT_TO.
     */
    public boolean sendRequest(Player sender, Player target) {
        return sendRequest(sender, target, TetherRequest.Type.TELEPORT_TO);
    }

    /**
     * Sends a teleport or summon request from sender to target.
     */
    public boolean sendRequest(Player sender, Player target, TetherRequest.Type type) {
        if (sender == null || target == null) {
            return false;
        }

        if (type == null) {
            type = TetherRequest.Type.TELEPORT_TO;
        }

        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        // Edge case: Self-teleport / Self-summon
        if (senderUuid.equals(targetUuid)) {
            MessageUtil.sendPrefixed(sender, "teleport.self-target");
            return false;
        }

        // Edge case: Sender is in Spectator mode
        if (sender.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            MessageUtil.sendPrefixed(sender, "teleport.sender-spectator");
            return false;
        }

        // Edge case: Target is in Spectator mode
        if (target.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            MessageUtil.sendPrefixed(sender, "teleport.target-spectator", MessageUtil.p("player", target.getName()));
            return false;
        }

        // Edge case: Sender is downed
        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(senderUuid)) {
            MessageUtil.sendPrefixed(sender, "teleport.sender-downed");
            return false;
        }

        // Edge case: Target is downed
        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(targetUuid)) {
            MessageUtil.sendPrefixed(sender, "teleport.target-downed", MessageUtil.p("player", target.getName()));
            return false;
        }

        // Edge case: Sender is in the void while trying to summon target to sender
        if (type == TetherRequest.Type.SUMMON_HERE && sender.getLocation().getY() < sender.getWorld().getMinHeight()) {
            MessageUtil.sendPrefixed(sender, "teleport.teleport-cancelled-void");
            return false;
        }

        PluginConfig config = plugin.getPluginConfig();

        // Check cooldown
        int cooldownSec = config.getTetherCooldownSeconds();
        if (cooldownSec > 0) {
            Long cooldownEnd = cooldowns.get(senderUuid);
            if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
                long remaining = Math.max(1, (cooldownEnd - System.currentTimeMillis()) / 1000);
                MessageUtil.sendPrefixed(sender, "teleport.cooldown", MessageUtil.p("seconds", String.valueOf(remaining)));
                return false;
            }
        }

        // Edge case: If sender already has an outgoing request, cancel it first
        TetherRequest existing = outgoingRequests.remove(senderUuid);
        if (existing != null) {
            removeRequest(existing);
            MessageUtil.sendPrefixed(sender, "teleport.request-cancelled-previous", MessageUtil.p("player", existing.targetName()));
        }

        int timeoutSec = config.getTetherTimeoutSeconds();
        long now = System.currentTimeMillis();
        long expiry = now + (timeoutSec * 1000L);

        TetherRequest request = new TetherRequest(senderUuid, sender.getName(), targetUuid, target.getName(), type, now, expiry);
        outgoingRequests.put(senderUuid, request);
        incomingRequests.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>()).put(senderUuid, request);

        // Notify sender
        String senderMsgKey = type == TetherRequest.Type.SUMMON_HERE
                ? "teleport.summon-sent-sender"
                : "teleport.request-sent-sender";
        MessageUtil.sendPrefixed(sender, senderMsgKey,
                MessageUtil.unparsed("player", target.getName()),
                MessageUtil.p("seconds", String.valueOf(timeoutSec)));

        if (config.isSoundEffectsEnabled()) {
            sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
        }

        // Notify target with interactive clickable components
        String escapedSender = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(sender.getName()).replace("'", "\\'");
        if (type == TetherRequest.Type.SUMMON_HERE) {
            MessageUtil.sendPrefixed(target, "teleport.summon-received-target", MessageUtil.unparsed("player", sender.getName()));

            String rawButtons = MessageUtil.getRaw("teleport.summon-received-buttons",
                    "<green><bold><click:run_command:'/tpq accept <player>'><hover:show_text:'<green>Click to accept summon from <player></green>'>[✔ ACCEPT]</click></hover></bold></green>   <red><bold><click:run_command:'/tpq deny <player>'><hover:show_text:'<red>Click to decline summon from <player></red>'>[✖ DECLINE]</click></hover></bold></red>")
                    .replace("<player>", escapedSender);
            MessageUtil.sendPrefixed(target, rawButtons);
        } else {
            MessageUtil.sendPrefixed(target, "teleport.request-received-target", MessageUtil.unparsed("player", sender.getName()));

            String rawButtons = MessageUtil.getRaw("teleport.request-received-buttons",
                    "<green><bold><click:run_command:'/tpq accept <player>'><hover:show_text:'<green>Click to accept teleport request from <player></green>'>[✔ ACCEPT]</click></hover></bold></green>   <red><bold><click:run_command:'/tpq deny <player>'><hover:show_text:'<red>Click to decline teleport request from <player></red>'>[✖ DECLINE]</click></hover></bold></red>")
                    .replace("<player>", escapedSender);
            MessageUtil.sendPrefixed(target, rawButtons);
        }

        if (config.isSoundEffectsEnabled()) {
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
        }

        // Schedule auto-expiry
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TetherRequest current = outgoingRequests.get(senderUuid);
            if (current != null && current.createdAtMillis() == now) {
                expireRequest(current);
            }
        }, timeoutSec * 20L);

        return true;
    }

    /**
     * Accepts an incoming teleport request.
     */
    public boolean acceptRequest(Player target, String senderNameOrNull) {
        if (target == null) return false;

        UUID targetUuid = target.getUniqueId();

        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(targetUuid)) {
            MessageUtil.sendPrefixed(target, "teleport.downed-blocked");
            return false;
        }

        Map<UUID, TetherRequest> requests = incomingRequests.get(targetUuid);
        if (requests == null || requests.isEmpty()) {
            MessageUtil.sendPrefixed(target, "teleport.no-pending-requests");
            return false;
        }

        // Prune expired requests first
        requests.values().removeIf(TetherRequest::isExpired);
        if (requests.isEmpty()) {
            incomingRequests.remove(targetUuid);
            MessageUtil.sendPrefixed(target, "teleport.no-pending-requests-expired");
            return false;
        }

        // Normalize senderName: if empty, blank, or placeholder "<player>", treat as null to accept newest
        if (senderNameOrNull != null && (senderNameOrNull.isBlank() || senderNameOrNull.equalsIgnoreCase("<player>"))) {
            senderNameOrNull = null;
        }

        TetherRequest matchingRequest = null;
        if (senderNameOrNull != null) {
            for (TetherRequest req : requests.values()) {
                if (req.senderName().equalsIgnoreCase(senderNameOrNull)) {
                    matchingRequest = req;
                    break;
                }
            }
            if (matchingRequest == null) {
                MessageUtil.sendPrefixed(target, "teleport.no-request-from-player", MessageUtil.unparsed("player", senderNameOrNull));
                return false;
            }
        } else {
            // Pick the only request or most recent
            if (requests.size() == 1) {
                matchingRequest = requests.values().iterator().next();
            } else {
                // If multiple requests, pick the newest
                matchingRequest = requests.values().stream()
                        .max(Comparator.comparingLong(TetherRequest::createdAtMillis))
                        .orElse(null);
            }
        }

        if (matchingRequest == null || matchingRequest.isExpired()) {
            MessageUtil.sendPrefixed(target, "teleport.request-expired-target");
            return false;
        }

        // Remove from tracking
        removeRequest(matchingRequest);

        Player sender = Bukkit.getPlayer(matchingRequest.senderUuid());
        if (sender == null || !sender.isOnline()) {
            MessageUtil.sendPrefixed(target, "teleport.player-offline", MessageUtil.unparsed("player", matchingRequest.senderName()));
            return false;
        }

        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(sender.getUniqueId())) {
            MessageUtil.sendPrefixed(target, "teleport.target-downed-accept", MessageUtil.unparsed("player", sender.getName()));
            return false;
        }

        boolean isSummon = matchingRequest.type() == TetherRequest.Type.SUMMON_HERE;
        if (isSummon) {
            MessageUtil.sendPrefixed(target, "teleport.summon-accept-target", MessageUtil.unparsed("player", sender.getName()));
            MessageUtil.sendPrefixed(sender, "teleport.summon-accept-sender", MessageUtil.unparsed("player", target.getName()));
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            }
            // In summon: target travels to sender. Sender was the requester.
            startTeleportWarmup(target, sender, TetherRequest.Type.SUMMON_HERE, sender.getUniqueId());
        } else {
            MessageUtil.sendPrefixed(target, "teleport.accept-target", MessageUtil.unparsed("player", sender.getName()));
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            }
            // In normal: sender travels to target. Sender was the requester.
            startTeleportWarmup(sender, target, TetherRequest.Type.TELEPORT_TO, sender.getUniqueId());
        }
        return true;
    }

    /**
     * Denies an incoming teleport request.
     */
    public boolean denyRequest(Player target, String senderNameOrNull) {
        if (target == null) return false;

        UUID targetUuid = target.getUniqueId();
        Map<UUID, TetherRequest> requests = incomingRequests.get(targetUuid);
        if (requests == null || requests.isEmpty()) {
            MessageUtil.sendPrefixed(target, "teleport.no-pending-requests");
            return false;
        }

        // Prune expired requests
        requests.values().removeIf(TetherRequest::isExpired);
        if (requests.isEmpty()) {
            incomingRequests.remove(targetUuid);
            MessageUtil.sendPrefixed(target, "teleport.no-pending-requests");
            return false;
        }

        // Normalize senderName: if empty, blank, or placeholder "<player>", treat as null to deny newest
        if (senderNameOrNull != null && (senderNameOrNull.isBlank() || senderNameOrNull.equalsIgnoreCase("<player>"))) {
            senderNameOrNull = null;
        }

        TetherRequest matchingRequest = null;
        if (senderNameOrNull != null) {
            for (TetherRequest req : requests.values()) {
                if (req.senderName().equalsIgnoreCase(senderNameOrNull)) {
                    matchingRequest = req;
                    break;
                }
            }
            if (matchingRequest == null) {
                MessageUtil.sendPrefixed(target, "teleport.no-request-from-player", MessageUtil.unparsed("player", senderNameOrNull));
                return false;
            }
        } else {
            if (requests.size() == 1) {
                matchingRequest = requests.values().iterator().next();
            } else {
                matchingRequest = requests.values().stream()
                        .max(Comparator.comparingLong(TetherRequest::createdAtMillis))
                        .orElse(null);
            }
        }

        if (matchingRequest == null) {
            MessageUtil.sendPrefixed(target, "teleport.no-pending-requests");
            return false;
        }

        removeRequest(matchingRequest);

        boolean isSummon = matchingRequest.type() == TetherRequest.Type.SUMMON_HERE;
        if (isSummon) {
            MessageUtil.sendPrefixed(target, "teleport.summon-deny-target", MessageUtil.unparsed("player", matchingRequest.senderName()));
            Player sender = Bukkit.getPlayer(matchingRequest.senderUuid());
            if (sender != null && sender.isOnline()) {
                MessageUtil.sendPrefixed(sender, "teleport.summon-deny-sender", MessageUtil.unparsed("player", target.getName()));
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                }
            }
        } else {
            MessageUtil.sendPrefixed(target, "teleport.deny-target", MessageUtil.unparsed("player", matchingRequest.senderName()));
            Player sender = Bukkit.getPlayer(matchingRequest.senderUuid());
            if (sender != null && sender.isOnline()) {
                MessageUtil.sendPrefixed(sender, "teleport.deny-sender", MessageUtil.unparsed("player", target.getName()));
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                }
            }
        }

        return true;
    }

    /**
     * Cancels any pending outgoing teleport request for the sender.
     */
    public boolean cancelOutgoingRequest(Player sender) {
        if (sender == null) return false;

        TetherRequest req = outgoingRequests.remove(sender.getUniqueId());
        if (req == null) {
            MessageUtil.sendPrefixed(sender, "teleport.no-pending-requests");
            return false;
        }

        // Use removeRequest logic to also prune empty target maps
        Map<UUID, TetherRequest> targetMap = incomingRequests.get(req.targetUuid());
        if (targetMap != null) {
            targetMap.remove(sender.getUniqueId());
            if (targetMap.isEmpty()) {
                incomingRequests.remove(req.targetUuid());
            }
        }

        if (req.type() == TetherRequest.Type.SUMMON_HERE) {
            MessageUtil.sendPrefixed(sender, "teleport.summon-cancel-outgoing", MessageUtil.unparsed("player", req.targetName()));
        } else {
            MessageUtil.sendPrefixed(sender, "teleport.cancel-outgoing", MessageUtil.unparsed("player", req.targetName()));
        }
        return true;
    }

    private void expireRequest(TetherRequest request) {
        if (request == null) return;
        TetherRequest current = outgoingRequests.get(request.senderUuid());
        if (current != null && current.createdAtMillis() == request.createdAtMillis()) {
            removeRequest(request);

            Player sender = Bukkit.getPlayer(request.senderUuid());
            if (sender != null && sender.isOnline()) {
                String key = request.type() == TetherRequest.Type.SUMMON_HERE
                        ? "teleport.summon-expired-sender"
                        : "teleport.request-expired-sender";
                MessageUtil.sendPrefixed(sender, key, MessageUtil.unparsed("player", request.targetName()));
            }
        }
    }

    private void removeRequest(TetherRequest request) {
        if (request == null) return;
        outgoingRequests.remove(request.senderUuid());
        Map<UUID, TetherRequest> targetMap = incomingRequests.get(request.targetUuid());
        if (targetMap != null) {
            targetMap.remove(request.senderUuid());
            if (targetMap.isEmpty()) {
                incomingRequests.remove(request.targetUuid());
            }
        }
    }

    /**
     * Initiates warmup and safe teleportation of sender to target defaulting to TELEPORT_TO.
     */
    public void startTeleportWarmup(Player sender, Player target) {
        startTeleportWarmup(sender, target, TetherRequest.Type.TELEPORT_TO, sender != null ? sender.getUniqueId() : null);
    }

    /**
     * Initiates warmup and safe teleportation of traveler to destination.
     *
     * @param traveler The player who will physically teleport.
     * @param destination The player at the destination.
     * @param type The request type (TELEPORT_TO or SUMMON_HERE).
     * @param requesterUuid The UUID of the player who initiated the request (for cooldown).
     */
    public void startTeleportWarmup(Player traveler, Player destination, TetherRequest.Type type, UUID requesterUuid) {
        if (traveler == null || destination == null) return;
        UUID travelerUuid = traveler.getUniqueId();
        cancelWarmup(traveler, false, null);

        // Cancel any active waypoint warmup for traveler
        if (plugin.getWaypointManager() != null) {
            plugin.getWaypointManager().cancelWarmup(traveler, false);
        }
        if (plugin.getPersonalWaypointManager() != null) {
            plugin.getPersonalWaypointManager().cancelWarmup(traveler, false);
        }

        PluginConfig config = plugin.getPluginConfig();
        int warmupSeconds = config.getTetherWarmupSeconds();

        // Instant teleport if warmup <= 0
        if (warmupSeconds <= 0) {
            executeTeleport(traveler, destination, type, requesterUuid);
            return;
        }

        warmupStartLocations.put(travelerUuid, traveler.getLocation().clone());

        if (type == TetherRequest.Type.SUMMON_HERE) {
            MessageUtil.sendPrefixed(traveler, "teleport.summon-warmup-traveler",
                    MessageUtil.p("player", destination.getName()),
                    MessageUtil.p("seconds", String.valueOf(warmupSeconds)));
            MessageUtil.sendPrefixed(destination, "teleport.summon-warmup-summoner",
                    MessageUtil.p("player", traveler.getName()),
                    MessageUtil.p("seconds", String.valueOf(warmupSeconds)));
        } else {
            MessageUtil.sendPrefixed(traveler, "teleport.warmup-sender",
                    MessageUtil.p("player", destination.getName()),
                    MessageUtil.p("seconds", String.valueOf(warmupSeconds)));
            MessageUtil.sendPrefixed(destination, "teleport.warmup-target",
                    MessageUtil.p("player", traveler.getName()),
                    MessageUtil.p("seconds", String.valueOf(warmupSeconds)));
        }

        if (config.isSoundEffectsEnabled()) {
            traveler.playSound(traveler.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.8f);
        }

        final int totalTicks = warmupSeconds * 20;
        final int interval = 5;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!traveler.isOnline() || traveler.isDead()) {
                    cancelWarmup(traveler, false, null);
                    return;
                }

                if (!destination.isOnline() || destination.isDead()) {
                    cancelWarmup(traveler, true, "teleport.teleport-cancelled-target-unavailable", MessageUtil.p("player", destination.getName()));
                    return;
                }

                // Check downed state
                if (plugin.getDownedManager() != null) {
                    if (plugin.getDownedManager().isDowned(travelerUuid)) {
                        cancelWarmup(traveler, false, null);
                        return;
                    }
                    if (plugin.getDownedManager().isDowned(destination.getUniqueId())) {
                        cancelWarmup(traveler, true, "teleport.target-downed-warmup", MessageUtil.p("player", destination.getName()));
                        return;
                    }
                }

                // Movement check
                Location initial = warmupStartLocations.get(travelerUuid);
                if (initial == null || initial.getWorld() != traveler.getWorld() || initial.distanceSquared(traveler.getLocation()) > 0.05) {
                    cancelWarmup(traveler, true, "teleport.teleport-cancelled-moved");
                    return;
                }

                elapsed += interval;
                int remainingSeconds = (int) Math.ceil((totalTicks - elapsed) / 20.0);

                if (elapsed % 20 == 0 && remainingSeconds > 0) {
                    MessageUtil.sendActionBar(traveler, "teleport.warmup-actionbar",
                            MessageUtil.p("player", destination.getName()),
                            MessageUtil.p("seconds", String.valueOf(remainingSeconds)));
                    if (config.isSoundEffectsEnabled()) {
                        traveler.playSound(traveler.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
                    }
                }

                if (config.isParticlesEnabled()) {
                    traveler.getWorld().spawnParticle(Particle.PORTAL, traveler.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.05);
                }

                if (elapsed >= totalTicks) {
                    // Only execute if warmup hasn't been cancelled externally (e.g. by event listener)
                    if (activeWarmups.containsKey(travelerUuid)) {
                        cancelWarmup(traveler, false, null);
                        executeTeleport(traveler, destination, type, requesterUuid);
                    }
                }
            }
        }, 0L, interval);

        activeWarmups.put(travelerUuid, task);
    }

    private void executeTeleport(Player traveler, Player destination, TetherRequest.Type type, UUID requesterUuid) {
        if (!traveler.isOnline() || !destination.isOnline() || traveler.isDead() || destination.isDead()) {
            return;
        }

        Location dest = destination.getLocation();
        if (dest.getWorld() == null) {
            MessageUtil.sendPrefixed(traveler, "teleport.teleport-cancelled-invalid-world");
            return;
        }

        // Void safety check
        if (dest.getY() < dest.getWorld().getMinHeight()) {
            MessageUtil.sendPrefixed(traveler, "teleport.teleport-cancelled-void");
            return;
        }

        // Leave vehicle before teleporting
        if (traveler.isInsideVehicle()) {
            traveler.leaveVehicle();
        }

        PluginConfig config = plugin.getPluginConfig();

        traveler.teleportAsync(dest).thenAccept(success -> {
            // teleportAsync completes on a background thread - schedule all Bukkit API work onto the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!traveler.isOnline()) {
                    return;
                }
                if (success) {
                    if (type == TetherRequest.Type.SUMMON_HERE) {
                        MessageUtil.sendPrefixed(traveler, "teleport.summon-success-traveler", MessageUtil.p("player", destination.getName()));
                        MessageUtil.sendActionBar(traveler, "teleport.teleport-success-actionbar");
                        MessageUtil.sendPrefixed(destination, "teleport.summon-success-summoner", MessageUtil.p("player", traveler.getName()));
                    } else {
                        MessageUtil.sendPrefixed(traveler, "teleport.teleport-success-sender", MessageUtil.p("player", destination.getName()));
                        MessageUtil.sendActionBar(traveler, "teleport.teleport-success-actionbar");
                        MessageUtil.sendPrefixed(destination, "teleport.teleport-success-target", MessageUtil.p("player", traveler.getName()));
                    }

                    if (config.isSoundEffectsEnabled()) {
                        traveler.playSound(traveler.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        destination.playSound(destination.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }

                    if (config.isParticlesEnabled()) {
                        traveler.getWorld().spawnParticle(Particle.REVERSE_PORTAL, traveler.getLocation().add(0, 1, 0), 25, 0.5, 1.0, 0.5, 0.1);
                        destination.getWorld().spawnParticle(Particle.PORTAL, destination.getLocation().add(0, 1, 0), 25, 0.5, 1.0, 0.5, 0.1);
                    }

                    // Set cooldown for requester
                    int cooldownSec = config.getTetherCooldownSeconds();
                    if (cooldownSec > 0 && requesterUuid != null) {
                        cooldowns.put(requesterUuid, System.currentTimeMillis() + (cooldownSec * 1000L));
                    }
                } else {
                    MessageUtil.sendPrefixed(traveler, "teleport.teleport-failed");
                }
            });
        });
    }

    public void cancelWarmup(Player sender, boolean notify) {
        cancelWarmup(sender, notify, null);
    }

    public void cancelWarmup(Player sender, boolean notify, String reasonKey, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        if (sender == null) return;
        UUID uuid = sender.getUniqueId();
        BukkitTask task = activeWarmups.remove(uuid);
        warmupStartLocations.remove(uuid);
        if (task != null) {
            task.cancel();
            if (notify && sender.isOnline()) {
                // Send a prefixed chat message with the reason key.
                // Only fall back to the actionbar key if no specific reason is provided.
                if (reasonKey != null) {
                    MessageUtil.sendPrefixed(sender, reasonKey, resolvers);
                } else {
                    MessageUtil.sendActionBar(sender, "teleport.teleport-cancelled-actionbar");
                }
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Returns a list of sender names with pending requests for the given target.
     */
    public List<String> getPendingSenderNames(Player target) {
        if (target == null) return List.of();
        Map<UUID, TetherRequest> map = incomingRequests.get(target.getUniqueId());
        if (map == null || map.isEmpty()) return List.of();
        return map.values().stream()
                .filter(r -> !r.isExpired())
                .map(TetherRequest::senderName)
                .toList();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (activeWarmups.containsKey(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())) {
                cancelWarmup(player, true, "teleport.teleport-cancelled-moved");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (activeWarmups.containsKey(player.getUniqueId())) {
                cancelWarmup(player, true, "teleport.teleport-cancelled-damage");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        cancelWarmup(player, false, null);

        // Clean outgoing
        TetherRequest outgoing = outgoingRequests.remove(uuid);
        if (outgoing != null) {
            Map<UUID, TetherRequest> targetMap = incomingRequests.get(outgoing.targetUuid());
            if (targetMap != null) {
                targetMap.remove(uuid);
                if (targetMap.isEmpty()) {
                    incomingRequests.remove(outgoing.targetUuid());
                }
            }
        }

        // Clean incoming
        Map<UUID, TetherRequest> incoming = incomingRequests.remove(uuid);
        if (incoming != null) {
            for (TetherRequest req : incoming.values()) {
                outgoingRequests.remove(req.senderUuid());
                Player reqSender = Bukkit.getPlayer(req.senderUuid());
                if (reqSender != null && reqSender.isOnline()) {
                    MessageUtil.sendPrefixed(reqSender, "teleport.cancel-target-left", MessageUtil.unparsed("player", player.getName()));
                }
            }
        }
    }

    public void cleanup() {
        for (BukkitTask task : activeWarmups.values()) {
            task.cancel();
        }
        activeWarmups.clear();
        warmupStartLocations.clear();
        outgoingRequests.clear();
        incomingRequests.clear();
        cooldowns.clear();
    }
}
