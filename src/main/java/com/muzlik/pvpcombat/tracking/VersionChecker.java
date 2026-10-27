package com.muzlik.pvpcombat.tracking;

import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks the tracking website for new plugin versions and notifies server admins.
 *
 * <p>Periodically polls {@code /api/plugin/update} on the tracking website to see if
 * a newer version of TrueCombatManager is available. When an update is found, a message
 * is printed to the server console and broadcast to all players with the
 * {@code pvpcombat.admin} permission.</p>
 */
public class VersionChecker {

    private static final String CURRENT_VERSION = "1.3.0-alpha";
    private static final String UPDATE_ENDPOINT = "https://vercel-server-tracker.vercel.app/api/plugin/update";

    private final PvPCombatPlugin plugin;
    private final Logger logger;
    private final HttpClient httpClient;
    private String latestVersion;
    private String updateUrl;
    private String updateNotes;
    private boolean updateAvailable;
    private long lastCheckTime;

    public VersionChecker(PvPCombatPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin != null ? plugin.getLogger() : Logger.getGlobal();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.latestVersion = CURRENT_VERSION;
        this.updateAvailable = false;
        this.lastCheckTime = 0;
    }

    /**
     * Checks the tracking website for a new plugin version.
     * Runs asynchronously and notifies admins if an update is available.
     */
    public void checkForUpdates() {
        long now = System.currentTimeMillis();
        // Only check once every 6 hours to avoid hammering the website
        if (lastCheckTime > 0 && (now - lastCheckTime) < Duration.ofHours(6).toMillis()) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UPDATE_ENDPOINT))
                .header("Accept", "application/json")
                .header("User-Agent", "TrueCombatManager/" + CURRENT_VERSION)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        parseUpdateResponse(response.body());
                    } else {
                        logger.fine("Update check returned HTTP " + response.statusCode());
                    }
                    lastCheckTime = System.currentTimeMillis();
                })
                .exceptionally(ex -> {
                    logger.log(Level.FINE, "Failed to check for plugin updates", ex);
                    lastCheckTime = System.currentTimeMillis();
                    return null;
                });
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to check for plugin updates", e);
            lastCheckTime = System.currentTimeMillis();
        }
    }

    private void parseUpdateResponse(String json) {
        try {
            // Simple JSON parsing without external dependencies
            latestVersion = extractJsonField(json, "latest_version");
            updateUrl = extractJsonField(json, "update_url");
            updateNotes = extractJsonField(json, "notes");

            // Determine if an update is available by comparing versions.
            // The website reports the latest version from Modrinth; the plugin
            // compares it against its own hardcoded CURRENT_VERSION.
            updateAvailable = latestVersion != null
                && !latestVersion.equals(CURRENT_VERSION)
                && isNewerVersion(latestVersion, CURRENT_VERSION);

            if (updateAvailable) {
                notifyAdminsOfUpdate();
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to parse update response", e);
        }
    }

    private String extractJsonField(String json, String field) {
        if (json == null || json.isEmpty()) return null;
        String pattern = "\"" + field + "\"\\s*:\\s*\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    boolean isNewerVersion(String candidate, String current) {
        String[] cParts = candidate.split("\\.");
        String[] nParts = current.split("\\.");
        int len = Math.max(cParts.length, nParts.length);
        for (int i = 0; i < len; i++) {
            int cVal = i < cParts.length ? parseVersionPart(cParts[i]) : 0;
            int nVal = i < nParts.length ? parseVersionPart(nParts[i]) : 0;
            if (cVal > nVal) return true;
            if (cVal < nVal) return false;
        }
        return false;
    }

    int parseVersionPart(String part) {
        try {
            // Strip non-numeric suffixes (e.g. "alpha", "beta", "rc1")
            String num = part.replaceAll("[^0-9].*", "");
            return num.isEmpty() ? 0 : Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void notifyAdminsOfUpdate() {
        String prefix = "§6§l[TrueCombatManager] §f";
        String msg = prefix + "A new version of TrueCombatManager is available: §a" + latestVersion
            + " §f(current: §c" + CURRENT_VERSION + "§f).";

        // Log to console
        logger.info("------------------------------------------------------------");
        logger.info("A new version of TrueCombatManager is available!");
        logger.info("  Current version: " + CURRENT_VERSION);
        logger.info("  Latest version:  " + latestVersion);
        if (updateNotes != null && !updateNotes.isEmpty()) {
            logger.info("  Notes: " + updateNotes);
        }
        if (updateUrl != null && !updateUrl.isEmpty()) {
            logger.info("  Download: " + updateUrl);
        }
        logger.info("------------------------------------------------------------");

        // Broadcast to admins
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("pvpcombat.admin")) {
                    player.sendMessage(msg);
                    if (updateUrl != null && !updateUrl.isEmpty()) {
                        player.sendMessage(prefix + "Download: §e" + updateUrl);
                    }
                }
            }
        });
    }

    /**
     * @return true if a newer version is available
     */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    /**
     * @return the latest version string, or current version if unknown
     */
    public String getLatestVersion() {
        return latestVersion != null ? latestVersion : CURRENT_VERSION;
    }

    /**
     * @return the current plugin version
     */
    public String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    /**
     * @return the update notes, or null if none
     */
    public String getUpdateNotes() {
        return updateNotes;
    }

    /**
     * @return the update download URL, or null if none
     */
    public String getUpdateUrl() {
        return updateUrl;
    }

    /**
     * @return milliseconds since last update check, or 0 if never checked
     */
    public long getLastCheckTime() {
        return lastCheckTime;
    }
}
