package com.lifeline.revive;

import com.lifeline.Lifeline;
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
 * Manages downed player state, bleeding timers, revive interactions, and safe death execution.
 */
public class DownedManager {

    private final Lifeline plugin;
    private final Map<UUID, DownedState> downedPlayers = new ConcurrentHashMap<>();

    public DownedManager(Lifeline plugin) {
        this.plugin = plugin;
    }

    public boolean isDowned(UUID uuid) {
        return downedPlayers.containsKey(uuid);
    }

    public DownedState getDownedState(UUID uuid) {
        return downedPlayers.get(uuid);
    }

    /**
     * Puts a player into the downed state upon receiving fatal damage.
     */
    public void downPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (downedPlayers.containsKey(uuid)) {
            return;
        }

        DownedState state = new DownedState(uuid);
        downedPlayers.put(uuid, state);

        // Set minimal health
        player.setHealth(1.0);

        // Apply negative downed effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 4, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false, true));

        // Alert server and play knock-down audio/visuals
        MessageUtil.broadcast("<red><bold>☠ DOWNED!</bold> <yellow>" + player.getName() + "</yellow> is bleeding out! Sneak and right-click to revive!</red>");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);

        // Start 30s bleed-out countdown task
        BukkitTask countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead()) {
                killPlayerSafely(player);
                return;
            }

            // Keep health at 1.0 during downed state
            if (player.getHealth() > 1.0) {
                player.setHealth(1.0);
            }

            int remaining = state.getRemainingSeconds();

            // Heartbeat audio effect (double thud)
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.6f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isDowned(uuid) && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 0.5f);
                }
            }, 4L);

            // Display action bar countdown if not currently being revived
            if (state.getActiveReviverUuid() == null) {
                MessageUtil.sendActionBar(player, "<red><bold>☠ DOWNED ☠</bold> Bleeding out: <yellow><bold>" + remaining + "s</bold></yellow> | Wait for revive!</red>");
            }

            if (remaining <= 0) {
                MessageUtil.broadcast("<red><yellow>" + player.getName() + "</yellow> bled out!</red>");
                killPlayerSafely(player);
            } else {
                state.decrementSeconds();
            }
        }, 0L, 20L);

        state.setCountdownTask(countdownTask);
    }

    /**
     * Initiates the 3-second revive channel when a sneaking partner right-clicks the downed player.
     */
    public void startRevive(Player reviver, Player downed) {
        DownedState state = downedPlayers.get(downed.getUniqueId());
        if (state == null) {
            return;
        }

        if (state.getActiveReviverUuid() != null) {
            MessageUtil.sendPrefixed(reviver, "<yellow>" + downed.getName() + " is already being revived!");
            return;
        }

        state.setActiveReviverUuid(reviver.getUniqueId());
        state.setReviveProgressTicks(0);

        final int totalTicks = 60; // 3 seconds = 60 ticks (checked every 2 ticks)
        final int interval = 2;

        MessageUtil.sendPrefixed(reviver, "<green>Reviving <yellow>" + downed.getName() + "</yellow>... Hold sneak!</green>");
        MessageUtil.sendPrefixed(downed, "<green><yellow>" + reviver.getName() + "</yellow> is reviving you!</green>");

        BukkitTask reviveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Validation checks
            if (!reviver.isOnline() || !downed.isOnline() || !isDowned(downed.getUniqueId())) {
                cancelRevive(state, reviver, downed, false);
                return;
            }

            if (!reviver.isSneaking()) {
                cancelRevive(state, reviver, downed, true);
                return;
            }

            Location reviverLoc = reviver.getLocation();
            Location downedLoc = downed.getLocation();

            if (reviverLoc.getWorld() != downedLoc.getWorld() || reviverLoc.distanceSquared(downedLoc) > 16.0) {
                cancelRevive(state, reviver, downed, true);
                return;
            }

            int currentTicks = state.getReviveProgressTicks() + interval;
            state.setReviveProgressTicks(currentTicks);

            int percent = (int) ((currentTicks / (double) totalTicks) * 100);
            String progressBar = buildProgressBar(currentTicks, totalTicks, 15);

            String barMessage = "<green><bold>Reviving:</bold> " + progressBar + " <yellow>" + percent + "%</yellow></green>";
            MessageUtil.sendActionBar(reviver, barMessage);
            MessageUtil.sendActionBar(downed, barMessage);

            // Particles and sounds during revive
            downed.getWorld().spawnParticle(Particle.HEART, downed.getLocation().add(0, 1.2, 0), 1, 0.2, 0.2, 0.2, 0.02);
            if (currentTicks % 10 == 0) {
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
                MessageUtil.sendActionBar(reviver, "<red>✖ Revive Interrupted (Keep sneaking & stay close)</red>");
                reviver.playSound(reviver.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
            }
            if (downed != null && downed.isOnline()) {
                MessageUtil.sendActionBar(downed, "<red>✖ Revive Interrupted</red>");
            }
        }
    }

    /**
     * Successfully completes the revive, clearing debuffs and restoring health.
     */
    public void completeRevive(Player reviver, Player downed) {
        UUID uuid = downed.getUniqueId();
        DownedState state = downedPlayers.remove(uuid);
        if (state != null) {
            state.cancelAllTasks();
        }

        // Remove negative effects
        downed.removePotionEffect(PotionEffectType.DARKNESS);
        downed.removePotionEffect(PotionEffectType.BLINDNESS);
        downed.removePotionEffect(PotionEffectType.SLOWNESS);
        downed.removePotionEffect(PotionEffectType.GLOWING);

        // Restore health to 6.0 (3 hearts)
        double maxHealth = 20.0;
        AttributeInstance maxHealthAttr = downed.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealth = maxHealthAttr.getValue();
        }
        downed.setHealth(Math.min(6.0, maxHealth));

        // Add helpful recovery buff
        downed.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 4, 1, false, false, true));
        downed.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 3, 0, false, false, true));

        // Audio-visual fanfare
        downed.playSound(downed.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1.2f);
        downed.playSound(downed.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        reviver.playSound(reviver.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        downed.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, downed.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.2);
        downed.getWorld().spawnParticle(Particle.HEART, downed.getLocation().add(0, 1.5, 0), 8, 0.4, 0.4, 0.4, 0.1);

        MessageUtil.sendActionBar(reviver, "<green><bold>✔ Revive Successful!</bold></green>");
        MessageUtil.sendActionBar(downed, "<green><bold>✔ You have been revived!</bold></green>");

        MessageUtil.broadcast("<green><yellow>" + reviver.getName() + "</yellow> successfully revived <yellow>" + downed.getName() + "</yellow>!</green>");
    }

    /**
     * Executes clean player death. Removes state first, then calls setHealth(0)
     * so third-party plugins (Gravestone, DeathChest) receive the natural PlayerDeathEvent cleanly.
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

        if (player.isOnline()) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.GLOWING);

            if (!player.isDead()) {
                player.setHealth(0.0);
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
        for (DownedState state : downedPlayers.values()) {
            state.cancelAllTasks();
        }
        downedPlayers.clear();
    }
}
