package com.lifeline;

import com.lifeline.config.PluginConfig;
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
import org.bukkit.Bukkit;
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

    private PluginConfig pluginConfig;
    private WaypointManager waypointManager;
    private WaypointGUI waypointGUI;
    private SharedVaultManager sharedVaultManager;
    private DownedManager downedManager;
    private ReviveListener reviveListener;

    @Override
    public void onEnable() {
        instance = this;

        // Ensure default config is created and load configuration
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);

        // Initialize Managers
        this.waypointManager = new WaypointManager(this);
        this.waypointGUI = new WaypointGUI(this, this.waypointManager);
        this.sharedVaultManager = new SharedVaultManager(this);
        this.downedManager = new DownedManager(this);
        this.reviveListener = new ReviveListener(this, this.downedManager);

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

            // Register /lifeline and /ll
            commands.register(
                    "lifeline",
                    "Lifeline plugin administration and management commands",
                    List.of("ll"),
                    new BasicCommand() {
                        @Override
                        public void execute(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();

                            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                                sendHelp(sender);
                                return;
                            }

                            String sub = args[0].toLowerCase();
                            switch (sub) {
                                case "reload" -> {
                                    if (!sender.hasPermission("lifeline.admin")) {
                                        MessageUtil.sendPrefixed(sender, "<red>You do not have permission to reload Lifeline.");
                                        return;
                                    }
                                    pluginConfig.load();
                                    if (!pluginConfig.isReviveEnabled()) {
                                        for (Player p : Bukkit.getOnlinePlayers()) {
                                            if (downedManager.isDowned(p.getUniqueId())) {
                                                downedManager.killPlayerSafely(p);
                                            }
                                        }
                                    }
                                    MessageUtil.sendPrefixed(sender, "<green>Configuration successfully reloaded!");
                                }
                                case "revives" -> {
                                    if (args.length > 1) {
                                        if (!sender.hasPermission("lifeline.admin")) {
                                            MessageUtil.sendPrefixed(sender, "<red>You do not have permission to check other players' revives.");
                                            return;
                                        }
                                        Player target = Bukkit.getPlayer(args[1]);
                                        if (target == null) {
                                            MessageUtil.sendPrefixed(sender, "<red>Player '<yellow>" + args[1] + "</yellow>' not found.");
                                            return;
                                        }
                                        displayRevives(sender, target);
                                    } else {
                                        if (!(sender instanceof Player player)) {
                                            MessageUtil.sendPrefixed(sender, "<red>Usage: /lifeline revives <player>");
                                            return;
                                        }
                                        displayRevives(sender, player);
                                    }
                                }
                                case "resetrevives" -> {
                                    if (!sender.hasPermission("lifeline.admin")) {
                                        MessageUtil.sendPrefixed(sender, "<red>You do not have permission to reset revives.");
                                        return;
                                    }
                                    if (args.length < 2) {
                                        MessageUtil.sendPrefixed(sender, "<red>Usage: /lifeline resetrevives <player>");
                                        return;
                                    }
                                    Player target = Bukkit.getPlayer(args[1]);
                                    if (target == null) {
                                        MessageUtil.sendPrefixed(sender, "<red>Player '<yellow>" + args[1] + "</yellow>' not found.");
                                        return;
                                    }
                                    downedManager.resetRevives(target.getUniqueId());
                                    int max = pluginConfig.getMaxRevives();
                                    String countStr = max == 0 ? "Infinite" : String.valueOf(max);
                                    MessageUtil.sendPrefixed(sender, "<green>Reset revives for <yellow>" + target.getName() + "</yellow> to <gold>" + countStr + "</gold>.");
                                    MessageUtil.sendPrefixed(target, "<green>Your revives counter has been reset to <gold>" + countStr + "</gold> by an administrator.");
                                }
                                default -> sendHelp(sender);
                            }
                        }

                        @Override
                        public Collection<String> suggest(CommandSourceStack stack, String[] args) {
                            if (args.length <= 1) {
                                List<String> list = new java.util.ArrayList<>(List.of("help", "revives"));
                                if (stack.getSender().hasPermission("lifeline.admin")) {
                                    list.add("reload");
                                    list.add("resetrevives");
                                }
                                String prefix = args.length == 0 ? "" : args[0].toLowerCase();
                                return list.stream().filter(s -> s.startsWith(prefix)).toList();
                            }
                            if (args.length == 2 && (args[0].equalsIgnoreCase("revives") || args[0].equalsIgnoreCase("resetrevives"))) {
                                String prefix = args[1].toLowerCase();
                                return Bukkit.getOnlinePlayers().stream()
                                        .map(Player::getName)
                                        .filter(name -> name.toLowerCase().startsWith(prefix))
                                        .toList();
                            }
                            return List.of();
                        }

                        @Override
                        public boolean canUse(CommandSender sender) {
                            return sender.hasPermission("lifeline.use");
                        }
                    }
            );
        });
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.sendPrefixed(sender, "<gold><bold>Lifeline Commands</bold></gold>");
        MessageUtil.sendRaw(sender, "  <yellow>/waypoint (or /wp)</yellow> <dark_gray>-</dark_gray> <gray>Open shared waypoints</gray>");
        MessageUtil.sendRaw(sender, "  <yellow>/vault (or /svault)</yellow> <dark_gray>-</dark_gray> <gray>Open shared co-op vault</gray>");
        MessageUtil.sendRaw(sender, "  <yellow>/lifeline revives [player]</yellow> <dark_gray>-</dark_gray> <gray>Check remaining revives</gray>");
        if (sender.hasPermission("lifeline.admin")) {
            MessageUtil.sendRaw(sender, "  <yellow>/lifeline reload</yellow> <dark_gray>-</dark_gray> <gray>Reload configuration</gray>");
            MessageUtil.sendRaw(sender, "  <yellow>/lifeline resetrevives <player></yellow> <dark_gray>-</dark_gray> <gray>Reset player's revives</gray>");
        }
    }

    private void displayRevives(CommandSender sender, Player target) {
        if (!pluginConfig.isReviveEnabled()) {
            MessageUtil.sendPrefixed(sender, "<yellow>The revive system is currently <red><bold>disabled</bold></red> in config (downed-timer-seconds: 0).");
            return;
        }
        int max = pluginConfig.getMaxRevives();
        if (max == 0) {
            MessageUtil.sendPrefixed(sender, "<yellow>" + target.getName() + "</yellow> currently has <gold><bold>Infinite Revives</bold></gold> enabled.");
        } else {
            int remaining = downedManager.getRemainingRevives(target.getUniqueId());
            MessageUtil.sendPrefixed(sender, "<yellow>" + target.getName() + "</yellow> has <gold><bold>" + remaining + "/" + max + "</bold></gold> revives remaining.");
        }
    }

    public static Lifeline getInstance() {
        return instance;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
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
