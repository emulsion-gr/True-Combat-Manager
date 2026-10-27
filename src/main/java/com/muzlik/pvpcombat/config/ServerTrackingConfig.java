package com.muzlik.pvpcombat.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Server tracking configuration settings.
 * Handles periodic heartbeat reporting to a remote tracking website.
 *
 * <p>Server tracking is <b>always enabled</b> and cannot be disabled via configuration.
 * The API endpoint is hardcoded in {@link com.muzlik.pvpcombat.tracking.ServerTracker}
 * and cannot be overridden via this configuration.</p>
 */
public class ServerTrackingConfig extends SubConfig {

    private boolean enabled;
    private String apiKey;
    private String serverId;
    private int heartbeatInterval;
    private int timeout;
    private boolean includeStats;

    public ServerTrackingConfig(ConfigurationValidator validator, ConfigurationSection config) {
        super(validator, config, "server-tracking");
    }

    @Override
    public void load() {
        // Server tracking is always enabled — cannot be disabled via config
        enabled = true;
        apiKey = getString("api-key", "");
        serverId = getString("server-id", "");
        heartbeatInterval = Math.max(getInt("heartbeat-interval", 60), 30);
        timeout = Math.max(getInt("timeout", 10), 5);
        includeStats = getBoolean("include-stats", true);
    }

    @Override
    public void reload() {
        load();
    }

    @Override
    public ConfigurationValidator.ValidationResult validate() {
        java.util.List<ConfigurationValidator.ConfigValidationError> errors = new java.util.ArrayList<>();
        java.util.List<ConfigurationValidator.ConfigValidationError> warnings = new java.util.ArrayList<>();
        java.util.List<ConfigurationValidator.ConfigValidationError> info = new java.util.ArrayList<>();

        // Tracking is always enabled; validate required API key
        if (apiKey == null || apiKey.isEmpty()) {
            errors.add(new ConfigurationValidator.ConfigValidationError(
                "server-tracking.api-key",
                "Server tracking is always enabled but no API key is configured. Set server-tracking.api-key in config.yml.",
                apiKey, "<your-secret-key>",
                ConfigurationValidator.ConfigValidationError.Severity.ERROR));
        }
        if (heartbeatInterval < 30) {
            warnings.add(new ConfigurationValidator.ConfigValidationError(
                "server-tracking.heartbeat-interval",
                "Heartbeat interval too low: " + heartbeatInterval + "s, minimum enforced: 30s",
                heartbeatInterval, 30,
                ConfigurationValidator.ConfigValidationError.Severity.WARNING));
        }

        return new ConfigurationValidator.ValidationResult(errors.isEmpty(), errors, warnings, info);
    }

    @Override
    public boolean isEnabled() {
        return true; // Always enabled
    }

    public boolean isEnabledTracking() {
        return true; // Always enabled
    }

    /**
     * @deprecated The API endpoint is hardcoded in ServerTracker and no longer configurable.
     */
    @Deprecated
    public String getEndpoint() {
        return com.muzlik.pvpcombat.tracking.ServerTracker.getApiEndpoint();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getServerId() {
        return serverId;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public int getTimeout() {
        return timeout;
    }

    public boolean isIncludeStats() {
        return includeStats;
    }

    @Override
    public int getLoadPriority() {
        return 10; // Load near the end
    }
}
