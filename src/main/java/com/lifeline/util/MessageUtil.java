package com.lifeline.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;

/**
 * Utility class for Adventure MiniMessage text formatting and broadcasting.
 */
public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String PREFIX = "<gradient:#00FFA3:#00B8D9><bold>Lifeline</bold></gradient> <dark_gray>»</dark_gray> ";

    private MessageUtil() {
    }

    /**
     * Parses a MiniMessage formatted string into an Adventure Component.
     */
    public static Component parse(String message, TagResolver... tagResolvers) {
        if (message == null) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(message, tagResolvers);
    }

    /**
     * Sends a prefixed MiniMessage to an Audience.
     */
    public static void sendPrefixed(Audience audience, String message, TagResolver... tagResolvers) {
        if (audience != null && message != null) {
            audience.sendMessage(parse(PREFIX + message, tagResolvers));
        }
    }

    /**
     * Sends a raw MiniMessage to an Audience without the prefix.
     */
    public static void sendRaw(Audience audience, String message, TagResolver... tagResolvers) {
        if (audience != null && message != null) {
            audience.sendMessage(parse(message, tagResolvers));
        }
    }

    /**
     * Sends an action bar message using MiniMessage.
     */
    public static void sendActionBar(Audience audience, String message, TagResolver... tagResolvers) {
        if (audience != null && message != null) {
            audience.sendActionBar(parse(message, tagResolvers));
        }
    }

    /**
     * Broadcasts a prefixed message to the entire server.
     */
    public static void broadcast(String message, TagResolver... tagResolvers) {
        Bukkit.broadcast(parse(PREFIX + message, tagResolvers));
    }
}
