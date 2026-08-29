package com.lifeline.bedrock;

import com.lifeline.Lifeline;
import com.lifeline.tether.TetherManager;
import com.lifeline.waypoint.Waypoint;
import com.lifeline.waypoint.WaypointManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Safe facade for Bedrock Edition native form rendering.
 * Does not import Geyser classes directly to prevent NoClassDefFoundError
 * when running on servers without Geyser installed.
 */
public final class GeyserHook {

    private GeyserHook() {}

    /**
     * Checks if Geyser API is present in the classpath and available on the server.
     */
    public static boolean isGeyserPresent() {
        if (Bukkit.getServer() == null) {
            return false;
        }

        try {
            Class.forName("org.geysermc.geyser.api.GeyserApi");
            return org.geysermc.geyser.api.GeyserApi.api() != null
                    || (Bukkit.getPluginManager() != null && (
                        Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")
                        || Bukkit.getPluginManager().isPluginEnabled("Geyser-Paper")
                        || Bukkit.getPluginManager().isPluginEnabled("Geyser")
                        || Bukkit.getPluginManager().isPluginEnabled("geyser")
                    ));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Returns true if the player is connected via Bedrock/Pocket Edition.
     */
    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        if (!isGeyserPresent()) return false;
        try {
            return GeyserFormHandler.isBedrockPlayer(player);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Lifeline] Geyser isBedrockPlayer check error: " + t.getMessage());
            return false;
        }
    }

    /**
     * Opens the native Bedrock Waypoints Form window for a player.
     */
    public static void openWaypointsForm(Player player, WaypointManager manager, Lifeline plugin) {
        if (player == null || !isGeyserPresent()) return;
        if (plugin != null && !plugin.getPluginConfig().isBedrockFormsEnabled()) return;
        try {
            GeyserFormHandler.openWaypointsForm(player, manager, plugin);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to open Bedrock waypoints form: " + t.getMessage());
            }
        }
    }

    /**
     * Opens a CustomForm for entering the new waypoint name.
     */
    public static void openCreateWaypointForm(Player player, WaypointManager manager, Lifeline plugin) {
        if (player == null || !isGeyserPresent()) return;
        if (plugin != null && !plugin.getPluginConfig().isBedrockFormsEnabled()) return;
        try {
            GeyserFormHandler.openCreateWaypointForm(player, manager, plugin);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to open Bedrock create waypoint form: " + t.getMessage());
            }
        }
    }

    /**
     * Opens a details form for a specific waypoint allowing Teleport or Delete.
     */
    public static void openWaypointDetailsForm(Player player, Waypoint wp, WaypointManager manager, Lifeline plugin) {
        if (player == null || !isGeyserPresent()) return;
        if (plugin != null && !plugin.getPluginConfig().isBedrockFormsEnabled()) return;
        try {
            GeyserFormHandler.openWaypointDetailsForm(player, wp, manager, plugin);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to open Bedrock waypoint details form: " + t.getMessage());
            }
        }
    }

    /**
     * Opens the native Bedrock Teleport Player form (/tpq).
     */
    public static void openTetherForm(Player player, TetherManager manager, Lifeline plugin) {
        if (player == null || !isGeyserPresent()) return;
        if (plugin != null && !plugin.getPluginConfig().isBedrockFormsEnabled()) return;
        try {
            GeyserFormHandler.openTetherForm(player, manager, plugin);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to open Bedrock teleport form: " + t.getMessage());
            }
        }
    }

    /**
     * Opens the native Bedrock Personal Waypoints Form window for a player.
     */
    public static void openPersonalWaypointsForm(Player player, com.lifeline.waypoint.PersonalWaypointManager manager, Lifeline plugin) {
        if (player == null || !isGeyserPresent()) return;
        if (plugin != null && !plugin.getPluginConfig().isBedrockFormsEnabled()) return;
        try {
            GeyserFormHandler.openPersonalWaypointsForm(player, manager, plugin);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to open Bedrock personal waypoints form: " + t.getMessage());
            }
        }
    }

    /**
     * Opens a CustomForm for entering the new personal waypoint name.
     */
    public static void openCreatePersonalWaypointForm(Player player, com.lifeline.waypoint.PersonalWaypointManager manager, Lifeline plugin) {
        if (player == null || !isGeyserPresent()) return;
        if (plugin != null && !plugin.getPluginConfig().isBedrockFormsEnabled()) return;
        try {
            GeyserFormHandler.openCreatePersonalWaypointForm(player, manager, plugin);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to open Bedrock create personal waypoint form: " + t.getMessage());
            }
        }
    }

    /**
     * Opens a details form for a specific personal waypoint allowing Teleport or Delete.
     */
    public static void openPersonalWaypointDetailsForm(Player player, Waypoint wp, com.lifeline.waypoint.PersonalWaypointManager manager, Lifeline plugin) {
        if (player == null || !isGeyserPresent()) return;
        if (plugin != null && !plugin.getPluginConfig().isBedrockFormsEnabled()) return;
        try {
            GeyserFormHandler.openPersonalWaypointDetailsForm(player, wp, manager, plugin);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to open Bedrock personal waypoint details form: " + t.getMessage());
            }
        }
    }
}
