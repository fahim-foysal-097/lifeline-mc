package com.lifeline.util;

import com.lifeline.Lifeline;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Utility class for managing translatable messages, Adventure MiniMessage formatting,
 * placeholder substitutions, and broadcasting across the Lifeline plugin.
 */
public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static FileConfiguration messagesConfig;
    private static String prefix = "<gradient:#00FFA3:#00B8D9><bold>Lifeline</bold></gradient> <dark_gray>»</dark_gray> ";

    private MessageUtil() {
    }

    /**
     * Initializes, updates/merges, and loads messages.yml from the plugin data folder.
     */
    public static void load(Lifeline plugin) {
        if (plugin == null) return;
        YamlConfiguration config = ConfigUpdater.update(plugin, "messages.yml");
        if (config != null) {
            load(config);
        }
    }

    /**
     * Loads messages directly from a FileConfiguration (useful for unit testing).
     */
    public static void load(FileConfiguration config) {
        messagesConfig = config;
        if (config != null) {
            prefix = config.getString("prefix", prefix);
        }
    }

    /**
     * Returns the configured prefix string.
     */
    public static String getPrefix() {
        return prefix;
    }

    /**
     * Retrieves the raw string from messages.yml by key, or fallback if absent.
     */
    public static String getRaw(String key, String fallback) {
        if (messagesConfig == null || key == null) {
            return fallback;
        }
        return messagesConfig.getString(key, fallback);
    }

    /**
     * Retrieves the raw string from messages.yml by key.
     */
    public static String getRaw(String key) {
        return getRaw(key, key);
    }

    /**
     * Retrieves a list of strings from messages.yml by key.
     */
    public static List<String> getRawList(String key) {
        if (messagesConfig == null || key == null) {
            return Collections.emptyList();
        }
        return messagesConfig.getStringList(key);
    }

    /**
     * Parses a MiniMessage formatted string into an Adventure Component.
     */
    public static Component parse(String message, TagResolver... tagResolvers) {
        if (message == null) {
            return Component.empty();
        }
        if (tagResolvers == null || tagResolvers.length == 0) {
            return MINI_MESSAGE.deserialize(message);
        }
        return MINI_MESSAGE.deserialize(message, TagResolver.resolver(tagResolvers));
    }

    /**
     * Resolves a key from messages.yml and parses it into an Adventure Component.
     */
    public static Component get(String key, TagResolver... tagResolvers) {
        String raw = getRaw(key, key);
        return parse(raw, tagResolvers);
    }

    /**
     * Resolves a key from messages.yml as a list of strings and parses each into a Component.
     */
    public static List<Component> getList(String key, TagResolver... tagResolvers) {
        List<String> rawList = getRawList(key);
        if (rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Component> components = new ArrayList<>(rawList.size());
        for (String raw : rawList) {
            components.add(parse(raw, tagResolvers));
        }
        return components;
    }

    /**
     * Sends a prefixed MiniMessage to an Audience.
     * If keyOrMessage exists in messages.yml, uses the configured string; otherwise treats as raw text.
     */
    public static void sendPrefixed(Audience audience, String keyOrMessage, TagResolver... tagResolvers) {
        if (audience == null || keyOrMessage == null) return;
        String raw = getRaw(keyOrMessage, keyOrMessage);
        audience.sendMessage(parse(prefix + raw, tagResolvers));
    }

    /**
     * Sends a raw MiniMessage to an Audience without the prefix.
     */
    public static void sendRaw(Audience audience, String keyOrMessage, TagResolver... tagResolvers) {
        if (audience == null || keyOrMessage == null) return;
        String raw = getRaw(keyOrMessage, keyOrMessage);
        audience.sendMessage(parse(raw, tagResolvers));
    }

    /**
     * Sends an action bar message using MiniMessage.
     */
    public static void sendActionBar(Audience audience, String keyOrMessage, TagResolver... tagResolvers) {
        if (audience == null || keyOrMessage == null) return;
        String raw = getRaw(keyOrMessage, keyOrMessage);
        audience.sendActionBar(parse(raw, tagResolvers));
    }

    /**
     * Broadcasts a prefixed message to the entire server.
     */
    public static void broadcast(String keyOrMessage, TagResolver... tagResolvers) {
        String raw = getRaw(keyOrMessage, keyOrMessage);
        Bukkit.broadcast(parse(prefix + raw, tagResolvers));
    }

    /**
     * Helper for creating a parsed MiniMessage placeholder tag.
     */
    public static TagResolver p(String key, String value) {
        return Placeholder.parsed(key, value != null ? value : "");
    }

    /**
     * Helper for creating an unparsed MiniMessage placeholder tag (escapes special tags).
     */
    public static TagResolver unparsed(String key, String value) {
        return Placeholder.unparsed(key, value != null ? value : "");
    }
}
