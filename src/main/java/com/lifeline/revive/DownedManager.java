package com.lifeline.revive;

import com.lifeline.Lifeline;
import com.lifeline.config.PluginConfig;
import com.lifeline.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages downed player state, bleeding timers, revive interactions, revive counters, and safe death execution.
 */
public class DownedManager {

    private final Lifeline plugin;
    private final Map<UUID, DownedState> downedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> remainingRevives = new ConcurrentHashMap<>();

    public DownedManager(Lifeline plugin) {
        this.plugin = plugin;
    }

    public boolean isDowned(UUID uuid) {
        return downedPlayers.containsKey(uuid);
    }

    public DownedState getDownedState(UUID uuid) {
        return downedPlayers.get(uuid);
    }

    public boolean isRevivingSomeone(UUID uuid) {
        if (uuid == null) return false;
        for (DownedState state : downedPlayers.values()) {
            if (uuid.equals(state.getActiveReviverUuid())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the remaining revives for a player.
     * If uninitialized and max-revives > 0, initializes to max-revives.
     */
    public int getRemainingRevives(UUID uuid) {
        int max = plugin.getPluginConfig().getMaxRevives();
        if (max == 0) {
            return 0; // 0 represents infinite revives
        }
        int current = remainingRevives.computeIfAbsent(uuid, k -> max);
        if (current > max) {
            current = max;
            remainingRevives.put(uuid, current);
        }
        return current;
    }

    /**
     * Sets the remaining revives for a player.
     */
    public void setRemainingRevives(UUID uuid, int count) {
        remainingRevives.put(uuid, Math.max(0, count));
    }

    /**
     * Resets the player's revive counter back to max-revives.
     */
    public void resetRevives(UUID uuid) {
        int max = plugin.getPluginConfig().getMaxRevives();
        if (max > 0) {
            remainingRevives.put(uuid, max);
        } else {
            remainingRevives.remove(uuid);
        }
    }

    /**
     * Checks if a player has revives left or if infinite revives are enabled.
     */
    public boolean hasRevivesLeft(UUID uuid) {
        int max = plugin.getPluginConfig().getMaxRevives();
        if (max == 0) {
            return true; // Infinite revives
        }
        return getRemainingRevives(uuid) > 0;
    }

    /**
     * Checks if a player can enter the downed state upon fatal damage.
     */
    public boolean canBeDowned(UUID uuid) {
        return plugin.getPluginConfig().isReviveEnabled() && hasRevivesLeft(uuid);
    }

    /**
     * Puts a player into the downed state upon receiving fatal damage.
     */
    public void downPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (downedPlayers.containsKey(uuid)) {
            return;
        }

        PluginConfig config = plugin.getPluginConfig();
        int timerSeconds = config.getDownedTimerSeconds();
        if (timerSeconds <= 0) {
            killPlayerSafely(player);
            return;
        }

        DownedState state = new DownedState(uuid, timerSeconds);
        if (downedPlayers.putIfAbsent(uuid, state) != null) {
            return;
        }

        // Cancel any active waypoint or tether teleport warmup
        if (plugin.getWaypointManager() != null) {
            plugin.getWaypointManager().cancelWarmup(player, false);
        }
        if (plugin.getPersonalWaypointManager() != null) {
            plugin.getPersonalWaypointManager().cancelWarmup(player, false);
        }
        if (plugin.getTetherManager() != null) {
            plugin.getTetherManager().cancelWarmup(player, false, null);
            plugin.getTetherManager().cancelOutgoingRequest(player);
        }

        // Close any open GUI
        player.closeInventory();

        // Eject from vehicles if riding
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }

        // Cancel Elytra gliding if gliding
        if (player.isGliding()) {
            player.setGliding(false);
        }

        // Deduct a revive if finite revives are active
        int maxRevives = config.getMaxRevives();
        int left = 0;
        if (maxRevives > 0) {
            int current = getRemainingRevives(uuid);
            left = Math.max(0, current - 1);
            remainingRevives.put(uuid, left);
        }

        // Set minimal health, extinguish fire, and restore air
        player.setHealth(1.0);
        player.setFireTicks(0);
        player.setRemainingAir(player.getMaximumAir());

        // Apply configurable negative downed effects
        if (config.isDownedDarkness()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        }
        if (config.isDownedBlindness()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        }
        if (config.getDownedSlownessLevel() > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, config.getDownedSlownessLevel() - 1, false, false, true));
        }
        if (config.isDownedGlowing()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        }

        // Alert server and play knock-down audio/visuals
        String livesInfo = "";
        if (maxRevives > 0) {
            livesInfo = MessageUtil.getRaw("revive.downed-lives-info", " <gray>(Revives remaining: <yellow><remaining></yellow>/<gold><max></gold>)</gray>")
                    .replace("<remaining>", String.valueOf(left))
                    .replace("<max>", String.valueOf(maxRevives));
        }

        MessageUtil.broadcast("revive.downed-broadcast",
                MessageUtil.p("player", player.getName()),
                MessageUtil.p("lives", livesInfo));

        if (config.isSoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.5f);
        }
        if (config.isParticlesEnabled()) {
            player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
        }

        // Start bleed-out countdown task.
        // Initial delay is 2L (not 0L) to guarantee state.setCountdownTask() is assigned
        // before the first tick fires, preventing a race where cancelAllTasks() finds null.
        final int finalLeft = left;
        BukkitTask countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isDowned(uuid)) {
                return;
            }
            if (!player.isOnline() || player.isDead()) {
                killPlayerSafely(player);
                return;  // task is cancelled via cancelAllTasks(); stop processing this tick
            }

            // Keep health at 1.0 and prevent burning/drowning during downed state
            if (player.getHealth() > 1.0) {
                player.setHealth(1.0);
            }
            player.setFireTicks(0);
            player.setRemainingAir(player.getMaximumAir());

            int remaining = state.getRemainingSeconds();

            // Heartbeat audio effect
            if (config.isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.6f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isDowned(uuid) && player.isOnline()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 0.5f);
                    }
                }, 4L);
            }

            // Display action bar countdown if not currently being revived
            if (state.getActiveReviverUuid() == null) {
                String barLives = "";
                if (maxRevives > 0) {
                    barLives = MessageUtil.getRaw("revive.downed-actionbar-lives", " <gray>[Lives: <yellow><remaining></yellow>]</gray>")
                            .replace("<remaining>", String.valueOf(finalLeft));
                }
                MessageUtil.sendActionBar(player, "revive.downed-actionbar",
                        MessageUtil.p("seconds", String.valueOf(remaining)),
                        MessageUtil.p("lives", barLives));
            }

            if (remaining <= 0) {
                MessageUtil.broadcast("revive.bled-out-broadcast", MessageUtil.unparsed("player", player.getName()));
                killPlayerSafely(player);
                return;
            } else {
                state.decrementSeconds();
            }
        }, 2L, 20L);

        state.setCountdownTask(countdownTask);
    }

    /**
     * Initiates the revive channel when a sneaking partner right-clicks the downed player.
     */
    public void startRevive(Player reviver, Player downed) {
        DownedState state = downedPlayers.get(downed.getUniqueId());
        if (state == null) {
            return;
        }

        if (state.getActiveReviverUuid() != null) {
            MessageUtil.sendPrefixed(reviver, "revive.already-reviving", MessageUtil.p("player", downed.getName()));
            return;
        }

        PluginConfig config = plugin.getPluginConfig();
        state.setActiveReviverUuid(reviver.getUniqueId());
        state.setReviveProgressTicks(0);

        final int channelSeconds = config.getReviveChannelSeconds();
        final int totalTicks = channelSeconds * 20;
        final int interval = 2;
        final double maxDistSquared = config.getReviveMaxDistance() * config.getReviveMaxDistance();

        MessageUtil.sendPrefixed(reviver, "revive.start-reviver", MessageUtil.p("player", downed.getName()));
        MessageUtil.sendPrefixed(downed, "revive.start-downed", MessageUtil.p("player", reviver.getName()));

        BukkitTask reviveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Validation checks
            if (!reviver.isOnline() || reviver.isDead() || isDowned(reviver.getUniqueId()) ||
                !downed.isOnline() || downed.isDead() || !isDowned(downed.getUniqueId())) {
                cancelRevive(state, reviver, downed, false);
                return;
            }

            if (!reviver.isSneaking()) {
                cancelRevive(state, reviver, downed, true);
                return;
            }

            Location reviverLoc = reviver.getLocation();
            Location downedLoc = downed.getLocation();

            if (reviverLoc.getWorld() != downedLoc.getWorld() || reviverLoc.distanceSquared(downedLoc) > maxDistSquared) {
                cancelRevive(state, reviver, downed, true);
                return;
            }

            int currentTicks = state.getReviveProgressTicks() + interval;
            state.setReviveProgressTicks(currentTicks);

            int percent = (int) ((currentTicks / (double) totalTicks) * 100);
            String progressBar = buildProgressBar(currentTicks, totalTicks, 15);

            MessageUtil.sendActionBar(reviver, "revive.revive-progress-actionbar",
                    MessageUtil.p("bar", progressBar),
                    MessageUtil.p("percent", String.valueOf(percent)));
            MessageUtil.sendActionBar(downed, "revive.revive-progress-actionbar",
                    MessageUtil.p("bar", progressBar),
                    MessageUtil.p("percent", String.valueOf(percent)));

            // Particles and sounds during revive
            if (config.isParticlesEnabled()) {
                downed.getWorld().spawnParticle(Particle.HEART, downed.getLocation().add(0, 1.2, 0), 1, 0.2, 0.2, 0.2, 0.02);
            }
            if (config.isSoundEffectsEnabled() && currentTicks % 10 == 0) {
                reviver.playSound(reviver.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.4f);
            }

            if (currentTicks >= totalTicks) {
                completeRevive(reviver, downed);
            }
        }, 0L, interval);

        state.setReviveTask(reviveTask);
    }

    private void cancelRevive(DownedState state, Player reviver, Player downed, boolean notify) {
        if (state != null) {
            if (state.getReviveTask() != null) {
                state.getReviveTask().cancel();
                state.setReviveTask(null);
            }
            state.setActiveReviverUuid(null);
            state.setReviveProgressTicks(0);
        }

        if (notify) {
            if (reviver != null && reviver.isOnline()) {
                MessageUtil.sendActionBar(reviver, "revive.revive-interrupted-reviver");
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    reviver.playSound(reviver.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
                }
            }
            if (downed != null && downed.isOnline()) {
                MessageUtil.sendActionBar(downed, "revive.revive-interrupted-downed");
            }
        }
    }

    /**
     * Successfully completes the revive, clearing debuffs and restoring configured health and buffs.
     */
    public void completeRevive(Player reviver, Player downed) {
        UUID uuid = downed.getUniqueId();
        DownedState state = downedPlayers.remove(uuid);
        if (state != null) {
            state.cancelAllTasks();
        }

        PluginConfig config = plugin.getPluginConfig();

        // Remove negative effects
        downed.removePotionEffect(PotionEffectType.DARKNESS);
        downed.removePotionEffect(PotionEffectType.BLINDNESS);
        downed.removePotionEffect(PotionEffectType.SLOWNESS);
        downed.removePotionEffect(PotionEffectType.GLOWING);

        // Restore health based on configuration
        double maxHealth = 20.0;
        AttributeInstance maxHealthAttr = downed.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealth = maxHealthAttr.getValue();
        }
        downed.setHealth(Math.min(config.getReviveHealthRestored(), maxHealth));

        // Add configured recovery buffs
        if (config.isRegenEnabled()) {
            downed.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * config.getRegenDuration(), config.getRegenAmplifier(), false, false, true));
        }
        if (config.isResistanceEnabled()) {
            downed.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * config.getResistanceDuration(), config.getResistanceAmplifier(), false, false, true));
        }
        if (config.isFireResistanceEnabled()) {
            downed.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * config.getFireResistanceDuration(), config.getFireResistanceAmplifier(), false, false, true));
        }

        // Audio-visual fanfare
        if (config.isSoundEffectsEnabled()) {
            downed.playSound(downed.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1.2f);
            downed.playSound(downed.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            reviver.playSound(reviver.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        if (config.isParticlesEnabled()) {
            downed.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, downed.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.2);
            downed.getWorld().spawnParticle(Particle.HEART, downed.getLocation().add(0, 1.5, 0), 8, 0.4, 0.4, 0.4, 0.1);
        }

        MessageUtil.sendActionBar(reviver, "revive.revive-success-reviver-actionbar");
        MessageUtil.sendActionBar(downed, "revive.revive-success-downed-actionbar");

        int maxRevives = config.getMaxRevives();
        String remainingText = "";
        if (maxRevives > 0) {
            remainingText = MessageUtil.getRaw("revive.downed-lives-info", " <gray>(Revives remaining: <yellow><remaining></yellow>/<gold><max></gold>)</gray>")
                    .replace("<remaining>", String.valueOf(getRemainingRevives(uuid)))
                    .replace("<max>", String.valueOf(maxRevives));
        }

        MessageUtil.broadcast("revive.revive-success-broadcast",
                MessageUtil.p("reviver", reviver.getName()),
                MessageUtil.p("downed", downed.getName()),
                MessageUtil.p("lives", remainingText));
    }

    /**
     * Executes clean player death and resets revives upon complete death.
     */
    public void killPlayerSafely(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        DownedState state = downedPlayers.remove(uuid);
        if (state != null) {
            state.cancelAllTasks();
        }

        // Reset revive counter back to set value upon complete death
        resetRevives(uuid);

        if (player.isOnline()) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.GLOWING);

            if (!player.isDead()) {
                if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && !player.isDead()) {
                            player.setHealth(0.0);
                        }
                    });
                } else {
                    player.setHealth(0.0);
                }
            }
        }
    }

    private String buildProgressBar(int current, int total, int totalBars) {
        int filled = (int) Math.round(((double) current / total) * totalBars);
        StringBuilder bar = new StringBuilder("<green>");
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }
        bar.append("</green><dark_gray>");
        for (int i = filled; i < totalBars; i++) {
            bar.append("█");
        }
        bar.append("</dark_gray>");
        return "[" + bar + "]";
    }

    public void cleanupAll() {
        for (UUID uuid : new java.util.ArrayList<>(downedPlayers.keySet())) {
            DownedState state = downedPlayers.remove(uuid);
            if (state != null) {
                state.cancelAllTasks();
            }
            resetRevives(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.removePotionEffect(PotionEffectType.DARKNESS);
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        downedPlayers.clear();
        remainingRevives.clear();
    }
}
