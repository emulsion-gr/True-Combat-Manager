package com.muzlik.pvpcombat.tracking;

import com.muzlik.pvpcombat.config.ServerTrackingConfig;
import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends periodic heartbeat requests to the True Combat Manager tracking website.
 * Reports server IP, online status, player count, version, and other stats.
 *
 * <p>The API endpoint is hardcoded to prevent tampering. Authentication is done
 * via an {@code X-API-Key} header — only the plugin (with the correct key) can
 * submit data; the tracking website rejects all unauthenticated requests.</p>
 */
public class ServerTracker {

    /** Hardcoded tracking website API endpoint — cannot be overridden via config. */
    private static final String API_ENDPOINT = "https://vercel-server-tracker.vercel.app/api/servers/heartbeat";

    private final PvPCombatPlugin plugin;
    private final ServerTrackingConfig config;
    private final Logger logger;
    private final HttpClient httpClient;
    private final String serverId;

    private BukkitTask heartbeatTask;
    private long startTime;

    public ServerTracker(PvPCombatPlugin plugin, ServerTrackingConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        this.startTime = System.currentTimeMillis();

        // Build a server ID if none configured: use IP:port
        String configuredId = config.getServerId();
        if (configuredId != null && !configuredId.isEmpty()) {
            this.serverId = configuredId;
        } else {
            String ip = Bukkit.getIp() != null ? Bukkit.getIp() : "unknown";
            int port = Bukkit.getPort();
            this.serverId = ip + ":" + port;
            logger.info("No server-id configured, generated: " + this.serverId);
        }

        // Create HTTP client with timeout from config
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.getTimeout()))
            .build();

        logger.info("ServerTracker initialized with hardcoded API endpoint: " + API_ENDPOINT);
    }

    /**
     * Starts the periodic heartbeat task.
     */
    public void start() {
        if (!config.isEnabledTracking()) {
            logger.info("Server tracking is disabled — heartbeat not started.");
            return;
        }

        long intervalSeconds = Math.max(config.getHeartbeatInterval(), 30);
        long intervalTicks = intervalSeconds * 20L;

        heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sendHeartbeat, intervalTicks, intervalTicks);
        logger.info("Server tracking heartbeat started (interval: " + intervalSeconds + "s, serverId: " + serverId + ")");

        // Send an immediate heartbeat on startup
        sendHeartbeat();
    }

    /**
     * Stops the heartbeat task.
     */
    public void stop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
            logger.info("Server tracking heartbeat stopped.");
        }
    }

    /**
     * Sends a single heartbeat request asynchronously to the hardcoded endpoint.
     */
    private void sendHeartbeat() {
        String apiKey = config.getApiKey();

        ServerInfo info = buildServerInfo();
        String json = info.toJson();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_ENDPOINT))
            .header("Content-Type", "application/json")
            .header("X-API-Key", apiKey != null ? apiKey : "")
            .header("User-Agent", "TrueCombatManager/" + plugin.getDescription().getVersion())
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(config.getTimeout()))
            .build();

        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        future.thenAccept(response -> {
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                logger.fine("Heartbeat sent successfully (HTTP " + status + ")");
            } else {
                logger.warning("Heartbeat rejected by server (HTTP " + status + "): " + response.body());
            }
        }).exceptionally(ex -> {
            logger.log(Level.WARNING, "Failed to send heartbeat to " + API_ENDPOINT, ex);
            return null;
        });
    }

    /**
     * Builds the current ServerInfo from the running server.
     */
    private ServerInfo buildServerInfo() {
        String host = Bukkit.getIp() != null ? Bukkit.getIp() : "0.0.0.0";
        int port = Bukkit.getPort();
        String version = Bukkit.getVersion();
        String bukkitVersion = Bukkit.getBukkitVersion();
        String minecraftVersion = extractMinecraftVersion(version);
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        long uptime = System.currentTimeMillis() - startTime;
        String pluginVersion = plugin.getDescription().getVersion();
        String serverName = "Minecraft Server (" + host + ":" + port + ")";

        return new ServerInfo(serverId, serverName, host, port,
            version, bukkitVersion, minecraftVersion,
            onlinePlayers, maxPlayers, true,
            uptime, pluginVersion);
    }

    /**
     * Extracts the Minecraft version from a Bukkit version string.
     * Handles formats like "1.20.4", "MC: 1.20.4", "git-Paper-...", etc.
     */
    private String extractMinecraftVersion(String version) {
        if (version == null || version.isEmpty()) {
            return "unknown";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(version);
        if (m.find()) {
            return m.group(1);
        }
        return version;
    }

    /**
     * Returns the server ID being used for tracking.
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Returns whether tracking is currently enabled.
     */
    public boolean isTracking() {
        return config.isEnabledTracking() && heartbeatTask != null;
    }

    /**
     * Returns the hardcoded API endpoint URL.
     */
    public static String getApiEndpoint() {
        return API_ENDPOINT;
    }
}
