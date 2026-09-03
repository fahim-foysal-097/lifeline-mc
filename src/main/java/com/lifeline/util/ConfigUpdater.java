package com.lifeline.util;

import com.lifeline.Lifeline;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Utility for automatically migrating, updating, and synchronizing YAML configurations
 * (such as config.yml and messages.yml) when the plugin is updated, while preserving
 * all existing user-customized values.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    /**
     * Updates the specified configuration file on disk by comparing it with the embedded jar resource.
     * Appends any missing keys/sections and preserves all user-configured values.
     *
     * @param plugin       The Lifeline plugin instance.
     * @param resourcePath The relative path of the file (e.g. "config.yml", "messages.yml").
     * @return The updated YamlConfiguration instance, or null if an error occurred.
     */
    public static YamlConfiguration update(Lifeline plugin, String resourcePath) {
        if (plugin == null || resourcePath == null) {
            return null;
        }

        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
            return YamlConfiguration.loadConfiguration(file);
        }

        InputStream defaultStream = plugin.getResource(resourcePath);
        if (defaultStream == null) {
            return YamlConfiguration.loadConfiguration(file);
        }

        try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(file);

            List<String> addedKeys = new ArrayList<>();
            int[] commentsAdded = new int[1];

            if (currentConfig.options().getHeader().isEmpty() && !defaultConfig.options().getHeader().isEmpty()) {
                currentConfig.options().setHeader(defaultConfig.options().getHeader());
                commentsAdded[0]++;
            }

            mergeSections(defaultConfig, currentConfig, "", addedKeys, commentsAdded);

            if (!addedKeys.isEmpty() || commentsAdded[0] > 0) {
                currentConfig.options().copyDefaults(true);
                currentConfig.setDefaults(defaultConfig);
                currentConfig.save(file);

                plugin.getLogger().info("Updated " + resourcePath + " with " + addedKeys.size()
                        + " new setting(s) and comments from latest update.");
            }

            return currentConfig;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update configuration file: " + resourcePath, e);
            return YamlConfiguration.loadConfiguration(file);
        }
    }

    /**
     * Merges defaults into a target configuration in-memory, returning the list of added keys.
     * Useful for isolated unit testing.
     */
    public static List<String> merge(YamlConfiguration defaultConfig, YamlConfiguration targetConfig) {
        List<String> addedKeys = new ArrayList<>();
        if (defaultConfig == null || targetConfig == null) {
            return addedKeys;
        }
        if (targetConfig.options().getHeader().isEmpty() && !defaultConfig.options().getHeader().isEmpty()) {
            targetConfig.options().setHeader(defaultConfig.options().getHeader());
        }
        mergeSections(defaultConfig, targetConfig, "", addedKeys, new int[1]);
        return addedKeys;
    }

    private static void mergeSections(ConfigurationSection defaultSection, ConfigurationSection targetSection, String currentPath, List<String> addedKeys, int[] commentsAdded) {
        for (String key : defaultSection.getKeys(false)) {
            String fullPath = currentPath.isEmpty() ? key : currentPath + "." + key;

            if (defaultSection.isConfigurationSection(key)) {
                ConfigurationSection childDefault = defaultSection.getConfigurationSection(key);
                if (childDefault != null) {
                    ConfigurationSection childTarget;
                    if (!targetSection.contains(key, false) || !targetSection.isConfigurationSection(key)) {
                        childTarget = targetSection.createSection(key);
                        addedKeys.add(fullPath);
                    } else {
                        childTarget = targetSection.getConfigurationSection(key);
                    }
                    if (childTarget != null) {
                        mergeSections(childDefault, childTarget, fullPath, addedKeys, commentsAdded);
                    }
                }
            } else {
                if (!targetSection.contains(key, false) || targetSection.isConfigurationSection(key)) {
                    Object defaultValue = defaultSection.get(key);
                    targetSection.set(key, defaultValue);
                    addedKeys.add(fullPath);
                }
            }

            // Copy block comments if target is missing them
            List<String> comments = defaultSection.getComments(key);
            if (comments != null && !comments.isEmpty()) {
                List<String> targetComments = targetSection.getComments(key);
                if (targetComments == null || targetComments.isEmpty()) {
                    targetSection.setComments(key, comments);
                    commentsAdded[0]++;
                }
            }

            // Copy inline comments if target is missing them
            List<String> inlineComments = defaultSection.getInlineComments(key);
            if (inlineComments != null && !inlineComments.isEmpty()) {
                List<String> targetInlineComments = targetSection.getInlineComments(key);
                if (targetInlineComments == null || targetInlineComments.isEmpty()) {
                    targetSection.setInlineComments(key, inlineComments);
                    commentsAdded[0]++;
                }
            }
        }
    }
}
