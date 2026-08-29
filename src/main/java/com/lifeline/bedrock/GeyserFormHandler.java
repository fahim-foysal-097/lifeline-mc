package com.lifeline.bedrock;

import com.lifeline.Lifeline;
import com.lifeline.tether.TetherManager;
import com.lifeline.waypoint.Waypoint;
import com.lifeline.waypoint.WaypointManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.geyser.api.GeyserApi;
import com.lifeline.util.MessageUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete handler for Bedrock Cumulus forms using Geyser API.
 * Loaded lazily only when Geyser is present on the server.
 */
final class GeyserFormHandler {

    private GeyserFormHandler() {}

    static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        try {
            GeyserApi api = GeyserApi.api();
            if (api == null) return false;
            java.util.UUID uuid = player.getUniqueId();
            return api.isBedrockPlayer(uuid) || api.connectionByUuid(uuid) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    static void sendForm(Player player, org.geysermc.cumulus.form.Form form) {
        if (player == null || form == null) return;
        try {
            GeyserApi api = GeyserApi.api();
            if (api == null) return;
            java.util.UUID uuid = player.getUniqueId();

            // Close container first so Bedrock client does not suppress the form
            player.closeInventory();

            org.geysermc.geyser.api.connection.GeyserConnection connection = api.connectionByUuid(uuid);
            if (connection != null) {
                connection.sendForm(form);
            } else {
                api.sendForm(uuid, form);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Lifeline] Failed to send Bedrock form to " + player.getName() + ": " + t.getMessage());
        }
    }

    static void openWaypointsForm(Player player, WaypointManager manager, Lifeline plugin) {
        if (!isBedrockPlayer(player)) return;

        List<Waypoint> waypoints = new ArrayList<>(manager.getAllWaypoints());

        String title = MessageUtil.getRaw("bedrock.waypoints-title", "Shared Waypoints");
        String content = MessageUtil.getRaw("bedrock.waypoints-content", "Select a waypoint or add a new one:");
        String addBtnText = MessageUtil.getRaw("bedrock.add-waypoint-btn", "+ Add New Waypoint");

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(title)
                .content(content)
                .button(addBtnText, FormImage.Type.PATH, "textures/ui/color_plus");

        for (Waypoint wp : waypoints) {
            String dim = getDimensionName(wp.getWorldName());
            String btnLabel = wp.getName() + " [" + dim + "]\n(" + (int) wp.getX() + ", " + (int) wp.getY() + ", " + (int) wp.getZ() + ")";
            String iconPath = getDimensionIconPath(wp.getWorldName());
            builder.button(btnLabel, FormImage.Type.PATH, iconPath);
        }

        builder.validResultHandler(response -> {
            int clickedId = response.clickedButtonId();
            if (clickedId == 0) {
                // Clicked "+ Add New Waypoint"
                Bukkit.getScheduler().runTask(plugin, () -> openCreateWaypointForm(player, manager, plugin));
            } else {
                int wpIndex = clickedId - 1;
                if (wpIndex >= 0 && wpIndex < waypoints.size()) {
                    Waypoint wp = waypoints.get(wpIndex);
                    Bukkit.getScheduler().runTask(plugin, () -> openWaypointDetailsForm(player, wp, manager, plugin));
                }
            }
        });

        sendForm(player, builder.build());
    }

    static void openCreateWaypointForm(Player player, WaypointManager manager, Lifeline plugin) {
        if (!isBedrockPlayer(player)) return;

        int maxWaypoints = plugin.getPluginConfig().getMaxWaypoints();
        int maxPages = plugin.getPluginConfig().getWaypointMaxPages();
        if (manager.getAllWaypoints().size() >= maxWaypoints) {
            MessageUtil.sendPrefixed(player, "waypoints.capacity-reached",
                    MessageUtil.p("max", String.valueOf(maxWaypoints)),
                    MessageUtil.p("pages", String.valueOf(maxPages)));
            return;
        }

        String title = MessageUtil.getRaw("bedrock.create-title", "Create Shared Waypoint");
        String nameLabel = MessageUtil.getRaw("bedrock.create-name-label", "Waypoint Name");
        String placeholder = MessageUtil.getRaw("bedrock.create-name-placeholder", "Enter waypoint name...");

        CustomForm form = CustomForm.builder()
                .title(title)
                .input(nameLabel, placeholder)
                .validResultHandler(response -> {
                    String input = response.asInput(0);
                    if (input == null || input.trim().isEmpty()) {
                        return;
                    }
                    String trimmed = input.trim();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(player.getUniqueId())) {
                            MessageUtil.sendPrefixed(player, "waypoints.prompt-downed");
                            return;
                        }

                        if (trimmed.equalsIgnoreCase("cancel")) {
                            MessageUtil.sendPrefixed(player, "waypoints.prompt-cancelled");
                            return;
                        }

                        if (trimmed.length() < 2 || trimmed.length() > 24) {
                            MessageUtil.sendPrefixed(player, "waypoints.name-length-error");
                            return;
                        }

                        if (trimmed.contains(".")) {
                            MessageUtil.sendPrefixed(player, "waypoints.name-invalid");
                            return;
                        }

                        if (manager.getWaypoint(trimmed) != null) {
                            MessageUtil.sendPrefixed(player, "waypoints.already-exists", MessageUtil.p("name", trimmed));
                            return;
                        }

                        if (manager.getAllWaypoints().size() >= maxWaypoints) {
                            MessageUtil.sendPrefixed(player, "waypoints.capacity-reached",
                                    MessageUtil.p("max", String.valueOf(maxWaypoints)),
                                    MessageUtil.p("pages", String.valueOf(maxPages)));
                            return;
                        }

                        Waypoint newWaypoint = Waypoint.fromLocation(trimmed, player.getLocation(), player.getUniqueId(), player.getName());
                        if (manager.addWaypoint(newWaypoint)) {
                            MessageUtil.broadcast("waypoints.created-broadcast",
                                    MessageUtil.unparsed("player", player.getName()),
                                    MessageUtil.unparsed("name", trimmed));
                        } else {
                            MessageUtil.sendPrefixed(player, "waypoints.save-error");
                        }
                    });
                })
                .build();

        sendForm(player, form);
    }

    static void openWaypointDetailsForm(Player player, Waypoint wp, WaypointManager manager, Lifeline plugin) {
        if (!isBedrockPlayer(player)) return;

        String dim = getDimensionName(wp.getWorldName());
        int warmup = plugin.getPluginConfig().getWaypointWarmupSeconds();

        String title = MessageUtil.getRaw("bedrock.details-title", "Waypoint: <name>")
                .replace("<name>", wp.getName());
        String content = MessageUtil.getRaw("bedrock.details-content", "Dimension: <dim>\nLocation: <x>, <y>, <z>\nCreated by: <creator>")
                .replace("<dim>", dim)
                .replace("<x>", String.valueOf((int) wp.getX()))
                .replace("<y>", String.valueOf((int) wp.getY()))
                .replace("<z>", String.valueOf((int) wp.getZ()))
                .replace("<creator>", wp.getCreatorName());

        String teleportBtn = MessageUtil.getRaw("bedrock.details-teleport-btn", "✦ Teleport (<seconds>s warm-up)")
                .replace("<seconds>", String.valueOf(warmup));
        String deleteBtn = MessageUtil.getRaw("bedrock.details-delete-btn", "✖ Delete Waypoint");
        String backBtn = MessageUtil.getRaw("bedrock.details-back-btn", "« Back to Waypoints");

        SimpleForm form = SimpleForm.builder()
                .title(title)
                .content(content)
                .button(teleportBtn, FormImage.Type.PATH, "textures/ui/portalIcon")
                .button(deleteBtn, FormImage.Type.PATH, "textures/ui/trash")
                .button(backBtn, FormImage.Type.PATH, "textures/ui/arrow_left")
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (clicked == 0) {
                            // Teleport
                            manager.startTeleportWarmup(player, wp);
                        } else if (clicked == 1) {
                            // Delete
                            if (manager.deleteWaypoint(wp.getName())) {
                                MessageUtil.sendPrefixed(player, "waypoints.deleted", MessageUtil.p("name", wp.getName()));
                            }
                            openWaypointsForm(player, manager, plugin);
                        } else if (clicked == 2) {
                            // Back
                            openWaypointsForm(player, manager, plugin);
                        }
                    });
                })
                .build();

        sendForm(player, form);
    }

    static void openTetherForm(Player player, TetherManager manager, Lifeline plugin) {
        if (!isBedrockPlayer(player)) return;

        List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
        targets.remove(player);

        // Map button index → target UUID so button clicks survive mid-form disconnects.
        // If a player quits after the form opens, we look them up by UUID (Bukkit returns
        // null for offline players) rather than relying on a now-shifted list index.
        final java.util.Map<Integer, java.util.UUID> buttonIndexToUuid = new java.util.HashMap<>();

        String title = MessageUtil.getRaw("bedrock.tpq-title", "Teleport to Player");
        String content = targets.isEmpty()
                ? MessageUtil.getRaw("bedrock.tpq-no-players", "No other players online.")
                : MessageUtil.getRaw("bedrock.tpq-content", "Select a player to send a teleport request:");

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(title)
                .content(content);

        for (int i = 0; i < targets.size(); i++) {
            Player target = targets.get(i);
            buttonIndexToUuid.put(i, target.getUniqueId());

            boolean isDowned = plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(target.getUniqueId());
            boolean isSpectator = target.getGameMode() == org.bukkit.GameMode.SPECTATOR;

            String dim = getDimensionName(target.getWorld().getName());
            String distStr = player.getWorld().equals(target.getWorld())
                    ? ((int) player.getLocation().distance(target.getLocation())) + "m"
                    : "Diff Dimension";
            int health = (int) Math.ceil(target.getHealth());

            String label;
            if (isDowned) {
                label = MessageUtil.getRaw("bedrock.tpq-btn-downed", "<player> [DOWNED]\n<dist>")
                        .replace("<player>", target.getName())
                        .replace("<dist>", distStr);
            } else if (isSpectator) {
                label = MessageUtil.getRaw("bedrock.tpq-btn-spectator", "<player> [SPECTATOR]\n<dist>")
                        .replace("<player>", target.getName())
                        .replace("<dist>", distStr);
            } else {
                label = MessageUtil.getRaw("bedrock.tpq-btn-normal", "<player> [<dim>]\n<dist> | HP: <health>")
                        .replace("<player>", target.getName())
                        .replace("<dim>", dim)
                        .replace("<dist>", distStr)
                        .replace("<health>", String.valueOf(health));
            }

            builder.button(label, FormImage.Type.PATH, "textures/ui/icon_steve");
        }

        builder.validResultHandler(response -> {
            int clicked = response.clickedButtonId();
            java.util.UUID targetUuid = buttonIndexToUuid.get(clicked);
            if (targetUuid == null) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null && target.isOnline()) {
                    manager.sendRequest(player, target);
                } else {
                    // Player left after the form was opened
                    String name = Bukkit.getOfflinePlayer(targetUuid).getName();
                    MessageUtil.sendPrefixed(player, "teleport.player-offline",
                            MessageUtil.p("player", name != null ? name : targetUuid.toString()));
                }
            });
        });

        sendForm(player, builder.build());
    }

    static void openPersonalWaypointsForm(Player player, com.lifeline.waypoint.PersonalWaypointManager manager, Lifeline plugin) {
        if (!isBedrockPlayer(player)) return;

        List<Waypoint> waypoints = new ArrayList<>(manager.getWaypoints(player.getUniqueId()));

        String title = MessageUtil.getRaw("bedrock.personal-waypoints-title", "Personal Waypoints");
        String content = MessageUtil.getRaw("bedrock.personal-waypoints-content", "Select a personal waypoint or add a new one:");
        String addBtnText = MessageUtil.getRaw("bedrock.personal-add-waypoint-btn", "+ Add New Personal Waypoint");

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(title)
                .content(content)
                .button(addBtnText, FormImage.Type.PATH, "textures/ui/color_plus");

        for (Waypoint wp : waypoints) {
            String dim = getDimensionName(wp.getWorldName());
            String btnLabel = wp.getName() + " [" + dim + "]\n(" + (int) wp.getX() + ", " + (int) wp.getY() + ", " + (int) wp.getZ() + ")";
            String iconPath = getDimensionIconPath(wp.getWorldName());
            builder.button(btnLabel, FormImage.Type.PATH, iconPath);
        }

        builder.validResultHandler(response -> {
            int clickedId = response.clickedButtonId();
            if (clickedId == 0) {
                Bukkit.getScheduler().runTask(plugin, () -> openCreatePersonalWaypointForm(player, manager, plugin));
            } else {
                int wpIndex = clickedId - 1;
                if (wpIndex >= 0 && wpIndex < waypoints.size()) {
                    Waypoint wp = waypoints.get(wpIndex);
                    Bukkit.getScheduler().runTask(plugin, () -> openPersonalWaypointDetailsForm(player, wp, manager, plugin));
                }
            }
        });

        sendForm(player, builder.build());
    }

    static void openCreatePersonalWaypointForm(Player player, com.lifeline.waypoint.PersonalWaypointManager manager, Lifeline plugin) {
        if (!isBedrockPlayer(player)) return;

        if (manager.getWaypoints(player.getUniqueId()).size() >= com.lifeline.waypoint.PersonalWaypointManager.MAX_PERSONAL_WAYPOINTS) {
            MessageUtil.sendPrefixed(player, "personal-waypoints.capacity-reached",
                    MessageUtil.p("max", String.valueOf(com.lifeline.waypoint.PersonalWaypointManager.MAX_PERSONAL_WAYPOINTS)));
            return;
        }

        String title = MessageUtil.getRaw("bedrock.personal-create-title", "Create Personal Waypoint");
        String nameLabel = MessageUtil.getRaw("bedrock.personal-create-name-label", "Waypoint Name");
        String placeholder = MessageUtil.getRaw("bedrock.personal-create-name-placeholder", "Enter waypoint name...");

        CustomForm form = CustomForm.builder()
                .title(title)
                .input(nameLabel, placeholder)
                .validResultHandler(response -> {
                    String input = response.asInput(0);
                    if (input == null || input.trim().isEmpty()) {
                        return;
                    }
                    String trimmed = input.trim();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(player.getUniqueId())) {
                            MessageUtil.sendPrefixed(player, "personal-waypoints.prompt-downed");
                            return;
                        }

                        if (trimmed.equalsIgnoreCase("cancel")) {
                            MessageUtil.sendPrefixed(player, "personal-waypoints.prompt-cancelled");
                            return;
                        }

                        if (trimmed.length() < 2 || trimmed.length() > 24) {
                            MessageUtil.sendPrefixed(player, "personal-waypoints.name-length-error");
                            return;
                        }

                        if (trimmed.contains(".")) {
                            MessageUtil.sendPrefixed(player, "personal-waypoints.name-invalid");
                            return;
                        }

                        if (manager.getWaypoint(player.getUniqueId(), trimmed) != null) {
                            MessageUtil.sendPrefixed(player, "personal-waypoints.already-exists", MessageUtil.p("name", trimmed));
                            return;
                        }

                        if (manager.getWaypoints(player.getUniqueId()).size() >= com.lifeline.waypoint.PersonalWaypointManager.MAX_PERSONAL_WAYPOINTS) {
                            MessageUtil.sendPrefixed(player, "personal-waypoints.capacity-reached",
                                    MessageUtil.p("max", String.valueOf(com.lifeline.waypoint.PersonalWaypointManager.MAX_PERSONAL_WAYPOINTS)));
                            return;
                        }

                        Waypoint newWaypoint = Waypoint.fromLocation(trimmed, player.getLocation(), player.getUniqueId(), player.getName());
                        if (manager.addWaypoint(player.getUniqueId(), newWaypoint)) {
                            MessageUtil.sendPrefixed(player, "personal-waypoints.created-msg",
                                    MessageUtil.unparsed("name", trimmed));
                        } else {
                            MessageUtil.sendPrefixed(player, "personal-waypoints.save-error");
                        }
                    });
                })
                .build();

        sendForm(player, form);
    }

    static void openPersonalWaypointDetailsForm(Player player, Waypoint wp, com.lifeline.waypoint.PersonalWaypointManager manager, Lifeline plugin) {
        if (!isBedrockPlayer(player)) return;

        String dim = getDimensionName(wp.getWorldName());
        int warmup = plugin.getPluginConfig().getWaypointWarmupSeconds();

        String title = MessageUtil.getRaw("bedrock.personal-details-title", "Waypoint: <name>")
                .replace("<name>", wp.getName());
        String content = MessageUtil.getRaw("bedrock.personal-details-content", "Dimension: <dim>\nLocation: <x>, <y>, <z>")
                .replace("<dim>", dim)
                .replace("<x>", String.valueOf((int) wp.getX()))
                .replace("<y>", String.valueOf((int) wp.getY()))
                .replace("<z>", String.valueOf((int) wp.getZ()));

        String teleportBtn = MessageUtil.getRaw("bedrock.personal-details-teleport-btn", "✦ Teleport (<seconds>s warm-up)")
                .replace("<seconds>", String.valueOf(warmup));
        String deleteBtn = MessageUtil.getRaw("bedrock.personal-details-delete-btn", "✖ Delete Waypoint");
        String backBtn = MessageUtil.getRaw("bedrock.personal-details-back-btn", "« Back to Waypoints");

        SimpleForm form = SimpleForm.builder()
                .title(title)
                .content(content)
                .button(teleportBtn, FormImage.Type.PATH, "textures/ui/portalIcon")
                .button(deleteBtn, FormImage.Type.PATH, "textures/ui/trash")
                .button(backBtn, FormImage.Type.PATH, "textures/ui/arrow_left")
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (clicked == 0) {
                            manager.startTeleportWarmup(player, wp);
                        } else if (clicked == 1) {
                            if (manager.deleteWaypoint(player.getUniqueId(), wp.getName())) {
                                MessageUtil.sendPrefixed(player, "personal-waypoints.deleted", MessageUtil.p("name", wp.getName()));
                            }
                            openPersonalWaypointsForm(player, manager, plugin);
                        } else if (clicked == 2) {
                            openPersonalWaypointsForm(player, manager, plugin);
                        }
                    });
                })
                .build();

        sendForm(player, form);
    }

    private static String getDimensionName(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (world.getEnvironment() == World.Environment.NETHER) {
                return MessageUtil.getRaw("waypoints.dim-nether", "The Nether");
            } else if (world.getEnvironment() == World.Environment.THE_END) {
                return MessageUtil.getRaw("waypoints.dim-end", "The End");
            }
        }
        return MessageUtil.getRaw("waypoints.dim-overworld", "Overworld");
    }

    private static String getDimensionIconPath(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (world.getEnvironment() == World.Environment.NETHER) {
                return "textures/items/nether_star";
            } else if (world.getEnvironment() == World.Environment.THE_END) {
                return "textures/items/ender_eye";
            }
        }
        return "textures/items/compass_item";
    }
}
