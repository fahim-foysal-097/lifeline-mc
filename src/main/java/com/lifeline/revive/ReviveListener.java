package com.lifeline.revive;

import com.lifeline.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for fatal damage, interaction/revive triggers, and edge cases (disconnects, void).
 */
public class ReviveListener implements Listener {

    private final DownedManager downedManager;

    public ReviveListener(DownedManager downedManager) {
        this.downedManager = downedManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Do not intercept void deaths or kill commands
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.KILL) {
            if (downedManager.isDowned(player.getUniqueId())) {
                downedManager.killPlayerSafely(player);
            }
            return;
        }

        // Case 1: Player is already downed and takes further damage
        if (downedManager.isDowned(player.getUniqueId())) {
            event.setCancelled(true);
            // Fatal damage execution while downed
            if (event.getFinalDamage() >= 0.5) {
                downedManager.killPlayerSafely(player);
            }
            return;
        }

        // Case 2: Player takes fatal damage and should enter downed state
        double finalDamage = event.getFinalDamage();
        if (player.getHealth() - finalDamage <= 0.0) {
            event.setCancelled(true);
            downedManager.downPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        if (!downedManager.isDowned(target.getUniqueId())) {
            return;
        }

        Player reviver = event.getPlayer();

        if (downedManager.isDowned(reviver.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        if (reviver.isSneaking()) {
            downedManager.startRevive(reviver, target);
        } else {
            MessageUtil.sendActionBar(reviver, "<yellow>Hold <gold><bold>Sneak (Shift)</bold></gold> and right-click to revive <aqua>" + target.getName() + "</aqua>!</yellow>");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (downedManager.isDowned(player.getUniqueId())) {
            downedManager.killPlayerSafely(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (downedManager.isDowned(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (downedManager.isDowned(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (downedManager.isDowned(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamageEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && downedManager.isDowned(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && downedManager.isDowned(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
