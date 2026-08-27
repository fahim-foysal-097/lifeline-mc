package com.lifeline;

import com.lifeline.revive.DownedManager;
import com.lifeline.revive.ReviveListener;
import com.lifeline.util.MessageUtil;
import com.lifeline.vault.SharedVaultManager;
import com.lifeline.waypoint.WaypointGUI;
import com.lifeline.waypoint.WaypointManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;

/**
 * Lifeline - A 2-Player Co-op Plugin for Paper.
 */
public final class Lifeline extends JavaPlugin {

    private static Lifeline instance;

    private WaypointManager waypointManager;
    private WaypointGUI waypointGUI;
    private SharedVaultManager sharedVaultManager;
    private DownedManager downedManager;
    private ReviveListener reviveListener;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize Managers
        this.waypointManager = new WaypointManager(this);
        this.waypointGUI = new WaypointGUI(this, this.waypointManager);
        this.sharedVaultManager = new SharedVaultManager(this);
        this.downedManager = new DownedManager(this);
        this.reviveListener = new ReviveListener(this.downedManager);

        // Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this.waypointManager, this);
        pm.registerEvents(this.waypointGUI, this);
        pm.registerEvents(this.sharedVaultManager, this);
        pm.registerEvents(this.reviveListener, this);

        // Register Commands via modern Paper LifecycleEvents
        registerCommands();

        getLogger().info("Lifeline v" + getPluginMeta().getVersion() + " has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down Lifeline and persisting all active co-op data...");

        if (this.waypointManager != null) {
            this.waypointManager.cleanup();
        }

        if (this.sharedVaultManager != null) {
            this.sharedVaultManager.cleanup();
        }

        if (this.downedManager != null) {
            this.downedManager.cleanupAll();
        }

        getLogger().info("Lifeline successfully disabled.");
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // Register /waypoint and /wp
            commands.register(
                    "waypoint",
                    "Opens the shared waypoints menu",
                    List.of("wp"),
                    new BasicCommand() {
                        @Override
                        public void execute(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();
                            if (!(sender instanceof Player player)) {
                                MessageUtil.sendPrefixed(sender, "<red>This command can only be executed by players.");
                                return;
                            }

                            if (!player.hasPermission("lifeline.waypoint")) {
                                MessageUtil.sendPrefixed(player, "<red>You do not have permission to use waypoints.");
                                return;
                            }

                            if (downedManager.isDowned(player.getUniqueId())) {
                                MessageUtil.sendPrefixed(player, "<red>You cannot open waypoints while downed!");
                                return;
                            }

                            waypointGUI.open(player);
                        }

                        @Override
                        public boolean canUse(CommandSender sender) {
                            return sender.hasPermission("lifeline.waypoint");
                        }
                    }
            );

            // Register /vault and /svault
            commands.register(
                    "vault",
                    "Opens the shared co-op vault",
                    List.of("svault"),
                    new BasicCommand() {
                        @Override
                        public void execute(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();
                            if (!(sender instanceof Player player)) {
                                MessageUtil.sendPrefixed(sender, "<red>This command can only be executed by players.");
                                return;
                            }

                            if (!player.hasPermission("lifeline.vault")) {
                                MessageUtil.sendPrefixed(player, "<red>You do not have permission to access the vault.");
                                return;
                            }

                            if (downedManager.isDowned(player.getUniqueId())) {
                                MessageUtil.sendPrefixed(player, "<red>You cannot access the vault while downed!");
                                return;
                            }

                            sharedVaultManager.openVault(player);
                        }

                        @Override
                        public boolean canUse(CommandSender sender) {
                            return sender.hasPermission("lifeline.vault");
                        }
                    }
            );
        });
    }

    public static Lifeline getInstance() {
        return instance;
    }

    public WaypointManager getWaypointManager() {
        return waypointManager;
    }

    public WaypointGUI getWaypointGUI() {
        return waypointGUI;
    }

    public SharedVaultManager getSharedVaultManager() {
        return sharedVaultManager;
    }

    public DownedManager getDownedManager() {
        return downedManager;
    }
}
