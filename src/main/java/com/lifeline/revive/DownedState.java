package com.lifeline.revive;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Tracks the state of an actively downed player.
 */
public class DownedState {

    private final UUID downedPlayerUuid;
    private int remainingSeconds = 30;
    private BukkitTask countdownTask;

    private UUID activeReviverUuid;
    private int reviveProgressTicks = 0;
    private BukkitTask reviveTask;

    public DownedState(UUID downedPlayerUuid) {
        this.downedPlayerUuid = downedPlayerUuid;
    }

    public UUID getDownedPlayerUuid() {
        return downedPlayerUuid;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void decrementSeconds() {
        this.remainingSeconds--;
    }

    public BukkitTask getCountdownTask() {
        return countdownTask;
    }

    public void setCountdownTask(BukkitTask countdownTask) {
        this.countdownTask = countdownTask;
    }

    public UUID getActiveReviverUuid() {
        return activeReviverUuid;
    }

    public void setActiveReviverUuid(UUID activeReviverUuid) {
        this.activeReviverUuid = activeReviverUuid;
    }

    public int getReviveProgressTicks() {
        return reviveProgressTicks;
    }

    public void setReviveProgressTicks(int reviveProgressTicks) {
        this.reviveProgressTicks = reviveProgressTicks;
    }

    public BukkitTask getReviveTask() {
        return reviveTask;
    }

    public void setReviveTask(BukkitTask reviveTask) {
        this.reviveTask = reviveTask;
    }

    public void cancelAllTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (reviveTask != null) {
            reviveTask.cancel();
            reviveTask = null;
        }
    }
}
