package com.lifeline.util;

import com.lifeline.Lifeline;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks for plugin updates against the GitHub repository releases.
 */
public final class UpdateChecker {

    public static final String GITHUB_REPO = "fahim-foysal-097/lifeline-mc";
    public static final String RELEASES_URL = "https://github.com/" + GITHUB_REPO + "/releases";
    public static final String LATEST_REDIRECT_URL = RELEASES_URL + "/latest";
    public static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    private static volatile UpdateResult cachedResult = null;

    private UpdateChecker() {
    }

    public enum UpdateStatus {
        UP_TO_DATE,
        OUTDATED,
        AHEAD,
        UNKNOWN
    }

    public record UpdateResult(
            UpdateStatus status,
            String currentVersion,
            String latestVersion,
            String releaseUrl
    ) {}

    public static UpdateResult getCachedResult() {
        return cachedResult;
    }

    public static void setCachedResult(UpdateResult result) {
        cachedResult = result;
    }

    /**
     * Checks for updates asynchronously against GitHub releases.
     *
     * @param currentVersion Current plugin version from metadata.
     * @return CompletableFuture containing the UpdateResult.
     */
    public static CompletableFuture<UpdateResult> checkForUpdates(String currentVersion) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String latestTag = fetchLatestTagFromRedirect();
                if (latestTag == null) {
                    latestTag = fetchLatestTagFromApi();
                }

                if (latestTag == null) {
                    return new UpdateResult(UpdateStatus.UNKNOWN, currentVersion, null, RELEASES_URL);
                }

                int cmp = compareVersions(currentVersion, latestTag);
                UpdateStatus status;
                if (cmp < 0) {
                    status = UpdateStatus.OUTDATED;
                } else if (cmp > 0) {
                    status = UpdateStatus.AHEAD;
                } else {
                    status = UpdateStatus.UP_TO_DATE;
                }

                String downloadUrl = RELEASES_URL + "/tag/" + latestTag;
                UpdateResult result = new UpdateResult(status, currentVersion, latestTag, downloadUrl);
                cachedResult = result;
                return result;
            } catch (Exception e) {
                UpdateResult result = new UpdateResult(UpdateStatus.UNKNOWN, currentVersion, null, RELEASES_URL);
                cachedResult = result;
                return result;
            }
        });
    }

    /**
     * Compares two semantic version strings.
     *
     * @return negative if current < latest (older), positive if current > latest (ahead), 0 if identical.
     */
    public static int compareVersions(String current, String latest) {
        if (current == null && latest == null) return 0;
        if (current == null) return -1;
        if (latest == null) return 1;

        String c = cleanVersion(current);
        String l = cleanVersion(latest);

        String[] cParts = c.split("[-+]", 2);
        String[] lParts = l.split("[-+]", 2);

        String[] cNums = cParts[0].split("\\.");
        String[] lNums = lParts[0].split("\\.");

        int length = Math.max(cNums.length, lNums.length);
        for (int i = 0; i < length; i++) {
            int cVal = i < cNums.length ? parseSafeInt(cNums[i]) : 0;
            int lVal = i < lNums.length ? parseSafeInt(lNums[i]) : 0;
            if (cVal != lVal) {
                return Integer.compare(cVal, lVal);
            }
        }

        boolean cHasQualifier = cParts.length > 1;
        boolean lHasQualifier = lParts.length > 1;
        if (cHasQualifier && !lHasQualifier) {
            return -1;
        } else if (!cHasQualifier && lHasQualifier) {
            return 1;
        }

        return 0;
    }

    private static String cleanVersion(String ver) {
        ver = ver.trim();
        if (ver.startsWith("v") || ver.startsWith("V")) {
            ver = ver.substring(1);
        }
        return ver;
    }

    private static int parseSafeInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Fast, rate-limit-free release tag resolution via HTTP redirect Location header.
     */
    private static String fetchLatestTagFromRedirect() {
        try {
            URI uri = URI.create(LATEST_REDIRECT_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Lifeline-UpdateChecker");
            conn.connect();

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                if (location != null && location.contains("/releases/tag/")) {
                    return location.substring(location.lastIndexOf('/') + 1);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Fallback resolution via GitHub REST API.
     */
    private static String fetchLatestTagFromApi() {
        try {
            URI uri = URI.create(GITHUB_API_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Lifeline-UpdateChecker");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.connect();

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    Matcher matcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(response);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Outputs notification to the server console.
     */
    public static void notifyConsole(Lifeline plugin, UpdateResult result) {
        if (result == null) return;
        switch (result.status()) {
            case OUTDATED -> {
                plugin.getLogger().warning("A new version of Lifeline is available: v" + result.latestVersion() + " (Current: v" + result.currentVersion() + ")");
                plugin.getLogger().warning("Download the latest release from: " + result.releaseUrl());
            }
            case AHEAD -> {
                plugin.getLogger().info("You are running a development build of Lifeline (v" + result.currentVersion() + "), which is ahead of the latest GitHub release (v" + result.latestVersion() + ").");
            }
            case UP_TO_DATE -> {
                plugin.getLogger().info("Lifeline is up to date (v" + result.currentVersion() + ").");
            }
            case UNKNOWN -> {
                plugin.getLogger().fine("Could not check for updates from GitHub.");
            }
        }
    }

    /**
     * Sends formatted notifications to a player.
     */
    public static void notifyPlayer(Player player, UpdateResult result) {
        if (player == null || result == null) return;
        switch (result.status()) {
            case OUTDATED -> {
                MessageUtil.sendRaw(player, "update.outdated-player-header");
                MessageUtil.sendRaw(player, "update.outdated-player-info",
                        MessageUtil.p("latest", result.latestVersion()),
                        MessageUtil.p("current", result.currentVersion()));
                MessageUtil.sendRaw(player, "update.outdated-player-link",
                        MessageUtil.p("url", result.releaseUrl()));
            }
            case AHEAD -> {
                MessageUtil.sendRaw(player, "update.ahead-player-header");
                MessageUtil.sendRaw(player, "update.ahead-player-info",
                        MessageUtil.p("latest", result.latestVersion()),
                        MessageUtil.p("current", result.currentVersion()));
            }
            case UP_TO_DATE -> {
                MessageUtil.sendPrefixed(player, "update.up-to-date",
                        MessageUtil.p("current", result.currentVersion()));
            }
            case UNKNOWN -> {
                MessageUtil.sendPrefixed(player, "update.check-failed");
            }
        }
    }

    /**
     * Sends formatted notifications to any CommandSender.
     */
    public static void notifySender(Lifeline plugin, CommandSender sender, UpdateResult result) {
        if (sender instanceof Player player) {
            notifyPlayer(player, result);
        } else {
            notifyConsole(plugin, result);
        }
    }
}
