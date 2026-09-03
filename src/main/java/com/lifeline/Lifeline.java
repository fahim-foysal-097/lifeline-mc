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
    private com.lifeline.radar.RadarManager radarManager;
    private com.lifeline.vault.PersonalVaultManager personalVaultManager;
    private com.lifeline.waypoint.PersonalWaypointManager personalWaypointManager;
    private com.lifeline.waypoint.PersonalWaypointGUI personalWaypointGUI;
    private com.lifeline.backup.BackupManager backupManager;
    private com.lifeline.trash.TrashGUI trashGUI;

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
        this.radarManager = new com.lifeline.radar.RadarManager(this);
        this.personalVaultManager = new com.lifeline.vault.PersonalVaultManager(this);
        this.personalWaypointManager = new com.lifeline.waypoint.PersonalWaypointManager(this);
        this.personalWaypointGUI = new com.lifeline.waypoint.PersonalWaypointGUI(this, this.personalWaypointManager);
        this.backupManager = new com.lifeline.backup.BackupManager(this);
        this.trashGUI = new com.lifeline.trash.TrashGUI(this);

        // Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this.waypointManager, this);
        pm.registerEvents(this.waypointGUI, this);
        pm.registerEvents(this.sharedVaultManager, this);
        pm.registerEvents(this.reviveListener, this);
        pm.registerEvents(this.tetherManager, this);
        pm.registerEvents(this.tetherGUI, this);
        pm.registerEvents(this.radarManager, this);
        pm.registerEvents(this.personalVaultManager, this);
        pm.registerEvents(this.personalWaypointManager, this);
        pm.registerEvents(this.personalWaypointGUI, this);
        pm.registerEvents(this.backupManager, this);
        pm.registerEvents(this.trashGUI, this);
        pm.registerEvents(new com.lifeline.util.UpdateListener(this), this);

        registerCommands();

        if (this.pluginConfig.isUpdateCheckerEnabled()) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
                com.lifeline.util.UpdateChecker.checkForUpdates(getPluginMeta().getVersion())
                        .thenAccept(result -> com.lifeline.util.UpdateChecker.notifyConsole(this, result));
            }, 40L);
        }

        getLogger().info("Lifeline v" + getPluginMeta().getVersion() + " loaded.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down Lifeline and persisting all active co-op data...");

        // Close any open GUIs so player inventories and stash buffers are synchronized
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.closeInventory();
        }

        // Perform final backup and persist stashes while dirty state is still intact
        if (this.backupManager != null) {
            this.backupManager.cleanup();
        }

        if (this.waypointManager != null) {
            this.waypointManager.cleanup();
        }

        if (this.personalWaypointManager != null) {
            this.personalWaypointManager.cleanup();
        }

        if (this.sharedVaultManager != null) {
            this.sharedVaultManager.cleanup();
        }

        if (this.personalVaultManager != null) {
            this.personalVaultManager.cleanup();
        }

        if (this.downedManager != null) {
            this.downedManager.cleanupAll();
        }

        if (this.tetherManager != null) {
            this.tetherManager.cleanup();
        }

        if (this.radarManager != null) {
            this.radarManager.cleanup();
        }

        getLogger().info("Lifeline successfully disabled.");
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(
                    "node",
                    "Opens the shared waypoints (nodes) menu",
                    List.of("nd", "wp", "nodes", "waypoint", "waypoints"),
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
                    "pstash",
                    "Opens your personal stash",
                    List.of("stashp", "pst", "mystash"),
                    new BasicCommand() {
                        @Override
                        public void execute(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();
                            if (!(sender instanceof Player player)) {
                                MessageUtil.sendPrefixed(sender, "general.player-only");
                                return;
                            }

                            if (!hasPersonalStashPermission(player)) {
                                MessageUtil.sendPrefixed(player, "personal-stash.no-permission");
                                return;
                            }

                            if (!pluginConfig.isPersonalStashEnabled()) {
                                MessageUtil.sendPrefixed(player, "personal-stash.globally-disabled");
                                return;
                            }

                            if (downedManager.isDowned(player.getUniqueId())) {
                                MessageUtil.sendPrefixed(player, "personal-stash.downed-blocked");
                                return;
                            }

                            personalVaultManager.openStash(player);
                        }

                        @Override
                        public boolean canUse(CommandSender sender) {
                            return hasPersonalStashPermission(sender);
                        }
                    }
            );

            commands.register(
                    "mywp",
                    "Opens your personal waypoints menu",
                    List.of("pwaypoints", "lfnode"),
                    new BasicCommand() {
                        @Override
                        public void execute(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();
                            if (!(sender instanceof Player player)) {
                                MessageUtil.sendPrefixed(sender, "general.player-only");
                                return;
                            }

                            if (!hasPersonalWaypointPermission(player)) {
                                MessageUtil.sendPrefixed(player, "personal-waypoints.no-permission");
                                return;
                            }

                            if (!pluginConfig.isPersonalWaypointsEnabled()) {
                                MessageUtil.sendPrefixed(player, "personal-waypoints.globally-disabled");
                                return;
                            }

                            if (downedManager.isDowned(player.getUniqueId())) {
                                MessageUtil.sendPrefixed(player, "personal-waypoints.downed-blocked");
                                return;
                            }

                            personalWaypointGUI.open(player);
                        }

                        @Override
                        public boolean canUse(CommandSender sender) {
                            return hasPersonalWaypointPermission(sender);
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
                    "coradar",
                    "Toggles the live teammate actionbar radar",
                    List.of("teamradar", "lfradar"),
                    new BasicCommand() {
                        @Override
                        public void execute(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();
                            if (!(sender instanceof Player player)) {
                                MessageUtil.sendPrefixed(sender, "general.player-only");
                                return;
                            }

                            if (!hasRadarPermission(player)) {
                                MessageUtil.sendPrefixed(player, "radar.no-permission");
                                return;
                            }

                            if (!pluginConfig.isRadarEnabled()) {
                                MessageUtil.sendPrefixed(player, "radar.globally-disabled");
                                return;
                            }

                            if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
                                radarManager.toggle(player);
                            } else if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("enable")) {
                                radarManager.setEnabled(player, true);
                            } else if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("disable")) {
                                radarManager.setEnabled(player, false);
                            } else {
                                radarManager.toggle(player);
                            }
                        }

                        @Override
                        public Collection<String> suggest(CommandSourceStack stack, String[] args) {
                            if (args.length <= 1) {
                                String prefix = args.length == 0 ? "" : args[0].toLowerCase();
                                return List.of("toggle", "on", "off").stream()
                                        .filter(s -> s.startsWith(prefix))
                                        .toList();
                            }
                            return List.of();
                        }

                        @Override
                        public boolean canUse(CommandSender sender) {
                            return hasRadarPermission(sender);
                        }
                    }
            );

            commands.register(
                    "lltrash",
                    "Opens the quick trash GUI to dispose of unwanted items",
                    List.of("trash", "dispose"),
                    new BasicCommand() {
                        @Override
                        public void execute(CommandSourceStack stack, String[] args) {
                            CommandSender sender = stack.getSender();
                            if (!(sender instanceof Player player)) {
                                MessageUtil.sendPrefixed(sender, "general.player-only");
                                return;
                            }

                            if (!hasTrashPermission(player)) {
                                MessageUtil.sendPrefixed(player, "trash.no-permission");
                                return;
                            }

                            if (!pluginConfig.isTrashEnabled()) {
                                MessageUtil.sendPrefixed(player, "trash.globally-disabled");
                                return;
                            }

                            if (downedManager.isDowned(player.getUniqueId())) {
                                MessageUtil.sendPrefixed(player, "trash.downed-blocked");
                                return;
                            }

                            trashGUI.open(player);
                        }

                        @Override
                        public Collection<String> suggest(CommandSourceStack stack, String[] args) {
                            return List.of();
                        }

                        @Override
                        public boolean canUse(CommandSender sender) {
                            return hasTrashPermission(sender);
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
                                    // Close any open Lifeline GUIs before saving/reloading so players don't hold an orphaned view
                                    for (Player p : Bukkit.getOnlinePlayers()) {
                                        org.bukkit.inventory.Inventory top = p.getOpenInventory().getTopInventory();
                                        if (top.getHolder() instanceof SharedVaultManager
                                                || top.getHolder() instanceof com.lifeline.vault.PersonalVaultHolder
                                                || top.getHolder() instanceof com.lifeline.waypoint.WaypointGUI
                                                || top.getHolder() instanceof com.lifeline.waypoint.PersonalWaypointGUI
                                                || top.getHolder() instanceof com.lifeline.tether.TetherGUI
                                                || top.getHolder() instanceof com.lifeline.trash.TrashHolder) {
                                            p.closeInventory();
                                        }
                                    }
                                    saveAllStashesAndPlayers(true);
                                    pluginConfig.load();
                                    MessageUtil.load(Lifeline.this);
                                    waypointManager.loadWaypoints();
                                    personalWaypointManager.loadWaypoints();
                                    if (sharedVaultManager != null) {
                                        sharedVaultManager.loadVault();
                                    }
                                    if (personalVaultManager != null) {
                                        personalVaultManager.loadStashes();
                                    }
                                    if (backupManager != null) {
                                        backupManager.updateBakFiles();
                                    }
                                    radarManager.startTask();
                                    if (!pluginConfig.isReviveEnabled()) {
                                        for (Player p : Bukkit.getOnlinePlayers()) {
                                            if (downedManager.isDowned(p.getUniqueId())) {
                                                downedManager.killPlayerSafely(p);
                                            }
                                        }
                                    }
                                    MessageUtil.sendPrefixed(sender, "general.config-reloaded");
                                }
                                case "save" -> {
                                    if (!sender.hasPermission("lifeline.admin")) {
                                        MessageUtil.sendPrefixed(sender, "stash.no-permission-save");
                                        return;
                                    }
                                    saveAllStashesAndPlayers(true);
                                    MessageUtil.sendPrefixed(sender, "stash.saved");
                                }
                                case "backup" -> {
                                    if (!sender.hasPermission("lifeline.admin")) {
                                        MessageUtil.sendPrefixed(sender, "backup.no-permission");
                                        return;
                                    }
                                    if (args.length > 1 && args[1].equalsIgnoreCase("list")) {
                                        backupManager.handleListBackups(sender);
                                    } else {
                                        backupManager.handleManualBackup(sender);
                                    }
                                }
                                case "update" -> {
                                    if (!sender.hasPermission("lifeline.admin")) {
                                        MessageUtil.sendPrefixed(sender, "general.no-permission");
                                        return;
                                    }
                                    MessageUtil.sendPrefixed(sender, "update.checking");
                                    com.lifeline.util.UpdateChecker.checkForUpdates(getPluginMeta().getVersion())
                                            .thenAccept(result -> {
                                                Bukkit.getScheduler().runTask(Lifeline.this, () -> {
                                                    com.lifeline.util.UpdateChecker.notifySender(Lifeline.this, sender, result);
                                                });
                                            });
                                }
                                case "radar" -> {
                                    if (!(sender instanceof Player player)) {
                                        MessageUtil.sendPrefixed(sender, "general.player-only");
                                        return;
                                    }
                                    if (!hasRadarPermission(player)) {
                                        MessageUtil.sendPrefixed(player, "radar.no-permission");
                                        return;
                                    }
                                    if (!pluginConfig.isRadarEnabled()) {
                                        MessageUtil.sendPrefixed(player, "radar.globally-disabled");
                                        return;
                                    }
                                    if (args.length == 1 || args[1].equalsIgnoreCase("toggle")) {
                                        radarManager.toggle(player);
                                    } else if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("enable")) {
                                        radarManager.setEnabled(player, true);
                                    } else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("disable")) {
                                        radarManager.setEnabled(player, false);
                                    } else {
                                        radarManager.toggle(player);
                                    }
                                }
                                case "revives" -> {
                                    if (args.length > 1) {
                                        if (!sender.hasPermission("lifeline.admin")) {
                                            MessageUtil.sendPrefixed(sender, "revive.no-permission-other");
                                            return;
                                        }
                                        Player target = Bukkit.getPlayer(args[1]);
                                        if (target == null) {
                                            MessageUtil.sendPrefixed(sender, "revive.player-not-found", MessageUtil.unparsed("player", args[1]));
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
                                        MessageUtil.sendPrefixed(sender, "revive.player-not-found", MessageUtil.unparsed("player", args[1]));
                                        return;
                                    }
                                    downedManager.resetRevives(target.getUniqueId());
                                    int max = pluginConfig.getMaxRevives();
                                    String countStr = max == 0 ? "Infinite" : String.valueOf(max);
                                    MessageUtil.sendPrefixed(sender, "revive.reset-revives-sender", MessageUtil.unparsed("player", target.getName()), MessageUtil.p("count", countStr));
                                    MessageUtil.sendPrefixed(target, "revive.reset-revives-target", MessageUtil.p("count", countStr));
                                }
                                default -> sendHelp(sender);
                            }
                        }

                        @Override
                        public Collection<String> suggest(CommandSourceStack stack, String[] args) {
                            if (args.length <= 1) {
                                List<String> list = new java.util.ArrayList<>(List.of("help", "radar", "revives"));
                                if (stack.getSender().hasPermission("lifeline.admin")) {
                                    list.add("backup");
                                    list.add("reload");
                                    list.add("resetrevives");
                                    list.add("save");
                                    list.add("update");
                                }
                                String prefix = args.length == 0 ? "" : args[0].toLowerCase();
                                return list.stream().filter(s -> s.startsWith(prefix)).toList();
                            }
                            if (args.length == 2 && args[0].equalsIgnoreCase("backup") && stack.getSender().hasPermission("lifeline.admin")) {
                                String prefix = args[1].toLowerCase();
                                return List.of("create", "list").stream()
                                        .filter(s -> s.startsWith(prefix))
                                        .toList();
                            }
                            if (args.length == 2 && args[0].equalsIgnoreCase("radar")) {
                                String prefix = args[1].toLowerCase();
                                return List.of("toggle", "on", "off").stream()
                                        .filter(s -> s.startsWith(prefix))
                                        .toList();
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

    private boolean hasRadarPermission(CommandSender sender) {
        return sender.hasPermission("lifeline.radar") || sender.hasPermission("lifeline.use");
    }

    private boolean hasNodePermission(CommandSender sender) {
        return sender.hasPermission("lifeline.node") || sender.hasPermission("lifeline.waypoint");
    }

    private boolean hasStashPermission(CommandSender sender) {
        return sender.hasPermission("lifeline.stash")
                || sender.hasPermission("lifeline.safe")
                || sender.hasPermission("lifeline.vault");
    }

    private boolean hasPersonalStashPermission(CommandSender sender) {
        return sender.hasPermission("lifeline.pstash")
                || sender.hasPermission("lifeline.personalstash")
                || sender.hasPermission("lifeline.stashp")
                || sender.hasPermission("lifeline.pst")
                || sender.hasPermission("lifeline.mystash")
                || sender.hasPermission("lifeline.use");
    }

    private boolean hasPersonalWaypointPermission(CommandSender sender) {
        return sender.hasPermission("lifeline.mywp")
                || sender.hasPermission("lifeline.personalwaypoint")
                || sender.hasPermission("lifeline.personalwaypoints")
                || sender.hasPermission("lifeline.pwaypoints")
                || sender.hasPermission("lifeline.lfnode")
                || sender.hasPermission("lifeline.use");
    }

    private boolean hasTetherPermission(CommandSender sender) {
        return sender.hasPermission("lifeline.tpq") || sender.hasPermission("lifeline.tether");
    }

    private boolean hasTrashPermission(CommandSender sender) {
        return sender.hasPermission("lifeline.trash") || sender.hasPermission("lifeline.use");
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.sendPrefixed(sender, "help.header");
        MessageUtil.sendRaw(sender, "help.node");
        MessageUtil.sendRaw(sender, "help.mywp");
        MessageUtil.sendRaw(sender, "help.stash");
        MessageUtil.sendRaw(sender, "help.pstash");
        MessageUtil.sendRaw(sender, "help.trash");
        MessageUtil.sendRaw(sender, "help.tpq");
        MessageUtil.sendRaw(sender, "help.radar");
        MessageUtil.sendRaw(sender, "help.revives");
        if (sender.hasPermission("lifeline.admin")) {
            MessageUtil.sendRaw(sender, "help.save");
            MessageUtil.sendRaw(sender, "help.backup");
            MessageUtil.sendRaw(sender, "help.update");
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
            MessageUtil.sendPrefixed(sender, "revive.infinite-revives-info", MessageUtil.unparsed("player", target.getName()));
        } else {
            int remaining = downedManager.getRemainingRevives(target.getUniqueId());
            MessageUtil.sendPrefixed(sender, "revive.remaining-revives-info",
                    MessageUtil.unparsed("player", target.getName()),
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

    public com.lifeline.radar.RadarManager getRadarManager() {
        return radarManager;
    }

    public com.lifeline.vault.PersonalVaultManager getPersonalVaultManager() {
        return personalVaultManager;
    }

    public com.lifeline.waypoint.PersonalWaypointManager getPersonalWaypointManager() {
        return personalWaypointManager;
    }

    public com.lifeline.waypoint.PersonalWaypointGUI getPersonalWaypointGUI() {
        return personalWaypointGUI;
    }

    public com.lifeline.backup.BackupManager getBackupManager() {
        return backupManager;
    }

    public com.lifeline.trash.TrashGUI getTrashGUI() {
        return trashGUI;
    }

    /**
     * Flushes all shared and personal stash data to disk.
     *
     * @param force if true, writes to disk even if no changes were flagged dirty
     */
    public void saveAllStashes(boolean force) {
        if (this.sharedVaultManager != null) {
            this.sharedVaultManager.saveVault(force);
        }
        if (this.personalVaultManager != null) {
            this.personalVaultManager.savePersonalStashes(force);
        }
    }

    /**
     * Flushes all stashes and ensures online players' inventory data are synchronized to disk.
     *
     * @param force if true, forces writing even if no in-memory stash changes were flagged dirty
     */
    public void saveAllStashesAndPlayers(boolean force) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(this, () -> saveAllStashesAndPlayers(force));
            return;
        }

        boolean dirty = force
                || (sharedVaultManager != null && sharedVaultManager.isDirty())
                || (personalVaultManager != null && personalVaultManager.isDirty());

        if (!dirty) {
            return;
        }

        // Save all online players' inventories to disk first so their state matches the stashes
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.saveData();
        }

        // Save stash inventories to disk
        saveAllStashes(force);
    }
}
