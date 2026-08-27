package com.lifeline.revive;

import com.lifeline.Lifeline;
import com.lifeline.config.PluginConfig;
import com.lifeline.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.*;

/**
 * Listens for fatal damage, interaction/revive triggers, invulnerability, and death/respawn resets.
 */
public class ReviveListener implements Listener {

    private final Lifeline plugin;
    private final DownedManager downedManager;

    public ReviveListener(Lifeline plugin, DownedManager downedManager) {
        this.plugin = plugin;
        this.downedManager = downedManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.getPluginConfig().isReviveEnabled()) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        PluginConfig config = plugin.getPluginConfig();

        // Do not intercept void deaths, /kill, or suicide commands
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID ||
            cause == EntityDamageEvent.DamageCause.KILL ||
            cause == EntityDamageEvent.DamageCause.SUICIDE) {
            if (downedManager.isDowned(player.getUniqueId())) {
                downedManager.killPlayerSafely(player);
            }
            return;
        }

        // Case 1: Player is already downed
        if (downedManager.isDowned(player.getUniqueId())) {
            event.setCancelled(true);
            // If downed invulnerability is disabled, allow fatal execution on hit
            if (!config.isDownedInvulnerable() && event.getFinalDamage() >= 0.5) {
                downedManager.killPlayerSafely(player);
            }
            return;
        }

        // Case 2: Player takes fatal damage and should enter downed state
        double finalDamage = event.getFinalDamage();
        if (player.getHealth() - finalDamage <= 0.0) {
            // Check if player holds Totem of Undying (allow Totem resurrection to trigger first)
            if (hasTotemOfUndying(player)) {
                return;
            }

            if (downedManager.canBeDowned(player.getUniqueId())) {
                event.setCancelled(true);
                downedManager.downPlayer(player);
            }
            // If canBeDowned is false (revives disabled or 0 revives left), event proceeds to natural death.
        }
    }

    private boolean hasTotemOfUndying(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING ||
               player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getPluginConfig().isReviveEnabled()) {
            return;
        }

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
            MessageUtil.sendActionBar(reviver, "revive.sneak-hint-actionbar", MessageUtil.p("player", target.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getPluginConfig().isReviveEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        downedManager.resetRevives(player.getUniqueId());
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

    @EventHandler(priority = EventPriority.LOW)
    public void onDropItem(PlayerDropItemEvent event) {
        if (downedManager.isDowned(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && downedManager.isDowned(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (downedManager.isDowned(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (downedManager.isDowned(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onTeleport(PlayerTeleportEvent event) {
        if (downedManager.isDowned(event.getPlayer().getUniqueId())) {
            // Prevent enderpearls or chorus fruit from teleporting downed players away
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL ||
                event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && downedManager.isDowned(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (downedManager.isDowned(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (downedManager.isDowned(player.getUniqueId()) &&
            player.getGameMode() != org.bukkit.GameMode.SPECTATOR &&
            player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player && downedManager.isDowned(player.getUniqueId())) {
            if (event.isGliding()) {
                event.setCancelled(true);
            }
        }
    }
}
