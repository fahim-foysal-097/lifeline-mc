package com.lifeline;

import com.lifeline.config.PluginConfig;
import com.lifeline.revive.DownedManager;
import com.lifeline.revive.ReviveListener;
import com.lifeline.tether.TetherGUI;
import com.lifeline.tether.TetherManager;
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
    private TetherManager tetherManager;
    private TetherGUI tetherGUI;

    @Override
    public void onEnable() {
        instance = this;

        // Ensure default config is created and load config
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);
        MessageUtil.load(this);

        // Initialize Managers
        this.waypointManager = new WaypointManager(this);
        this.waypointGUI = new WaypointGUI(this, this.waypointManager);
        this.sharedVaultManager = new SharedVaultManager(this);
        this.downedManager = new DownedManager(this);
        this.reviveListener = new ReviveListener(this, this.downedManager);
        this.tetherManager = new TetherManager(this);
        this.tetherGUI = new TetherGUI(this, this.tetherManager);

        // Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this.waypointManager, this);
        pm.registerEvents(this.waypointGUI, this);
        pm.registerEvents(this.sharedVaultManager, this);
        pm.registerEvents(this.reviveListener, this);
        pm.registerEvents(this.tetherManager, this);
        pm.registerEvents(this.tetherGUI, this);

        registerCommands();

        getLogger().info("Lifeline v" + getPluginMeta().getVersion() + " loaded.");
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

        if (this.tetherManager != null) {
            this.tetherManager.cleanup();
        }

        getLogger().info("Lifeline successfully disabled.");
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(
                    "node",
                    "Opens the shared waypoints (nodes) menu",
                    List.of("nd", "wp"),
                    new BasicCommand() {
                        @Override
                        public void execute(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();
                            if (!(sender instanceof Player player)) {
                                MessageUtil.sendPrefixed(sender, "general.player-only");
                                return;
                            }

                            if (!hasNodePermission(player)) {
                                MessageUtil.sendPrefixed(player, "waypoints.no-permission");
                                return;
                            }

                            if (downedManager.isDowned(player.getUniqueId())) {
                                MessageUtil.sendPrefixed(player, "waypoints.downed-blocked");
                                return;
                            }

                            waypointGUI.open(player);
                        }

                        @Override
                        public boolean canUse(CommandSender sender) {
                            return hasNodePermission(sender);
                        }
                    }
            );

            commands.register(
                    "stash",
                    "Opens the shared co-op stash / safe",
                    List.of("st", "safe"),
                    new BasicCommand() {
                        @Override
                        public void execute(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();
                            if (!(sender instanceof Player player)) {
                                MessageUtil.sendPrefixed(sender, "general.player-only");
                                return;
                            }

                            if (!hasStashPermission(player)) {
                                MessageUtil.sendPrefixed(player, "stash.no-permission");
                                return;
                            }

                            if (downedManager.isDowned(player.getUniqueId())) {
                                MessageUtil.sendPrefixed(player, "stash.downed-blocked");
                                return;
                            }

                            sharedVaultManager.openVault(player);
                        }

                        @Override
                        public boolean canUse(CommandSender sender) {
                            return hasStashPermission(sender);
                        }
                    }
            );

            commands.register(
                    "tpq",
                    "Opens the player teleport GUI or manages teleport requests",
                    List.of("teleportgui"),
                    new BasicCommand() {
                        @Override
                        public void execute(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();
                            if (!(sender instanceof Player player)) {
                                MessageUtil.sendPrefixed(sender, "general.player-only");
                                return;
                            }

                            if (!hasTetherPermission(player)) {
                                MessageUtil.sendPrefixed(player, "teleport.no-permission");
                                return;
                            }

                            if (downedManager.isDowned(player.getUniqueId())) {
                                MessageUtil.sendPrefixed(player, "teleport.downed-blocked");
                                return;
                            }

                            if (args.length == 0) {
                                tetherGUI.open(player);
                                return;
                            }

                            String sub = args[0].toLowerCase();
                            switch (sub) {
                                case "accept", "a" -> {
                                    String targetSender = args.length > 1 ? args[1] : null;
                                    tetherManager.acceptRequest(player, targetSender);
                                }
                                case "deny", "decline", "d" -> {
                                    String targetSender = args.length > 1 ? args[1] : null;
                                    tetherManager.denyRequest(player, targetSender);
                                }
                                case "cancel", "c" -> {
                                    tetherManager.cancelOutgoingRequest(player);
                                }
                                case "gui", "menu" -> {
                                    tetherGUI.open(player);
                                }
                                default -> {
                                    Player target = Bukkit.getPlayer(args[0]);
                                    if (target == null || !target.isOnline()) {
                                        MessageUtil.sendPrefixed(player, "teleport.player-offline", MessageUtil.unparsed("player", args[0]));
                                        return;
                                    }
                                    tetherManager.sendRequest(player, target);
                                }
                            }
                        }

                        @Override
                        public Collection<String> suggest(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();
                            if (!(sender instanceof Player player)) {
                                return List.of();
                            }

                            if (args.length <= 1) {
                                List<String> list = new java.util.ArrayList<>(List.of("accept", "deny", "cancel", "gui", "a", "d", "c"));
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    if (!p.getUniqueId().equals(player.getUniqueId())) {
                                        list.add(p.getName());
                                    }
                                }
                                String prefix = args.length == 0 ? "" : args[0].toLowerCase();
                                return list.stream().filter(s -> s.toLowerCase().startsWith(prefix)).toList();
                            }

                            if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("a") || args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("d") || args[0].equalsIgnoreCase("decline"))) {
                                String prefix = args[1].toLowerCase();
                                return tetherManager.getPendingSenderNames(player).stream()
                                         .filter(name -> name.toLowerCase().startsWith(prefix))
                                         .toList();
                            }

                            return List.of();
                        }

                        @Override
                        public boolean canUse(CommandSender sender) {
                            return hasTetherPermission(sender);
                        }
                    }
            );

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
                                        MessageUtil.sendPrefixed(sender, "general.no-permission-reload");
                                        return;
                                    }
                                    pluginConfig.load();
                                    MessageUtil.load(Lifeline.this);
                                    waypointManager.loadWaypoints();
                                    if (!pluginConfig.isReviveEnabled()) {
                                        for (Player p : Bukkit.getOnlinePlayers()) {
                                            if (downedManager.isDowned(p.getUniqueId())) {
                                                downedManager.killPlayerSafely(p);
                                            }
                                        }
                                    }
                                    MessageUtil.sendPrefixed(sender, "general.config-reloaded");
                                }
                                case "revives" -> {
                                    if (args.length > 1) {
                                        if (!sender.hasPermission("lifeline.admin")) {
                                            MessageUtil.sendPrefixed(sender, "revive.no-permission-other");
                                            return;
                                        }
                                        Player target = Bukkit.getPlayer(args[1]);
                                        if (target == null) {
                                            MessageUtil.sendPrefixed(sender, "revive.player-not-found", MessageUtil.p("player", args[1]));
                                            return;
                                        }
                                        displayRevives(sender, target);
                                    } else {
                                        if (!(sender instanceof Player player)) {
                                            MessageUtil.sendPrefixed(sender, "revive.usage-revives");
                                            return;
                                        }
                                        displayRevives(sender, player);
                                    }
                                }
                                case "resetrevives" -> {
                                    if (!sender.hasPermission("lifeline.admin")) {
                                        MessageUtil.sendPrefixed(sender, "revive.no-permission-reset");
                                        return;
                                    }
                                    if (args.length < 2) {
                                        MessageUtil.sendPrefixed(sender, "revive.usage-resetrevives");
                                        return;
                                    }
                                    Player target = Bukkit.getPlayer(args[1]);
                                    if (target == null) {
                                        MessageUtil.sendPrefixed(sender, "revive.player-not-found", MessageUtil.p("player", args[1]));
                                        return;
                                    }
                                    downedManager.resetRevives(target.getUniqueId());
                                    int max = pluginConfig.getMaxRevives();
                                    String countStr = max == 0 ? "Infinite" : String.valueOf(max);
                                    MessageUtil.sendPrefixed(sender, "revive.reset-revives-sender", MessageUtil.p("player", target.getName()), MessageUtil.p("count", countStr));
                                    MessageUtil.sendPrefixed(target, "revive.reset-revives-target", MessageUtil.p("count", countStr));
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

    private boolean hasNodePermission(CommandSender sender) {
        return sender.hasPermission("lifeline.node") || sender.hasPermission("lifeline.waypoint");
    }

    private boolean hasStashPermission(CommandSender sender) {
        return sender.hasPermission("lifeline.stash")
                || sender.hasPermission("lifeline.safe")
                || sender.hasPermission("lifeline.vault");
    }

    private boolean hasTetherPermission(CommandSender sender) {
        return sender.hasPermission("lifeline.tpq") || sender.hasPermission("lifeline.tether");
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.sendPrefixed(sender, "help.header");
        MessageUtil.sendRaw(sender, "help.node");
        MessageUtil.sendRaw(sender, "help.stash");
        MessageUtil.sendRaw(sender, "help.tpq");
        MessageUtil.sendRaw(sender, "help.revives");
        if (sender.hasPermission("lifeline.admin")) {
            MessageUtil.sendRaw(sender, "help.reload");
            MessageUtil.sendRaw(sender, "help.resetrevives");
        }
    }

    private void displayRevives(CommandSender sender, Player target) {
        if (!pluginConfig.isReviveEnabled()) {
            MessageUtil.sendPrefixed(sender, "revive.disabled-in-config");
            return;
        }
        int max = pluginConfig.getMaxRevives();
        if (max == 0) {
            MessageUtil.sendPrefixed(sender, "revive.infinite-revives-info", MessageUtil.p("player", target.getName()));
        } else {
            int remaining = downedManager.getRemainingRevives(target.getUniqueId());
            MessageUtil.sendPrefixed(sender, "revive.remaining-revives-info",
                    MessageUtil.p("player", target.getName()),
                    MessageUtil.p("remaining", String.valueOf(remaining)),
                    MessageUtil.p("max", String.valueOf(max)));
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

    public TetherManager getTetherManager() {
        return tetherManager;
    }

    public TetherGUI getTetherGUI() {
        return tetherGUI;
    }
}
