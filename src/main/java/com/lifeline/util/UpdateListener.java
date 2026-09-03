package com.lifeline.util;

import com.lifeline.Lifeline;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listens for administrator joins to deliver cached update notifications.
 */
public class UpdateListener implements Listener {

    private final Lifeline plugin;

    public UpdateListener(Lifeline plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getPluginConfig().isUpdateCheckerEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("lifeline.admin")) {
            return;
        }

        UpdateChecker.UpdateResult result = UpdateChecker.getCachedResult();
        if (result != null && (result.status() == UpdateChecker.UpdateStatus.OUTDATED || result.status() == UpdateChecker.UpdateStatus.AHEAD)) {
            // Delay notification slightly (50 ticks / 2.5s) so it cleanly appears after MOTD and welcome messages
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    UpdateChecker.notifyPlayer(player, result);
                }
            }, 50L);
        }
    }
}
