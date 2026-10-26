package com.muzlik.pvpcombat.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Server tracking configuration settings.
 * Handles periodic heartbeat reporting to a remote tracking website.
 */
public class ServerTrackingConfig extends SubConfig {

    private boolean enabled;
    private String endpoint;
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
        enabled = getBoolean("enabled", false);
        endpoint = getString("endpoint", "");
        apiKey = getString("api-key", "");
        serverId = getString("server-id", "");
        heartbeatInterval = getInt("heartbeat-interval", 60);
        timeout = getInt("timeout", 10);
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

        if (enabled) {
            if (endpoint == null || endpoint.isEmpty()) {
                errors.add(new ConfigurationValidator.ConfigValidationError(
                    "server-tracking.endpoint",
                    "Server tracking is enabled but no endpoint URL is configured.",
                    endpoint, "https://example.com/api/servers/heartbeat",
                    ConfigurationValidator.ConfigValidationError.Severity.ERROR));
            }
            if (apiKey == null || apiKey.isEmpty()) {
                errors.add(new ConfigurationValidator.ConfigValidationError(
                    "server-tracking.api-key",
                    "Server tracking is enabled but no API key is configured.",
                    apiKey, "<your-secret-key>",
                    ConfigurationValidator.ConfigValidationError.Severity.ERROR));
            }
            if (heartbeatInterval < 30) {
                warnings.add(new ConfigurationValidator.ConfigValidationError(
                    "server-tracking.heartbeat-interval",
                    "Heartbeat interval too low: " + heartbeatInterval + "s, recommended minimum: 30s",
                    heartbeatInterval, 30,
                    ConfigurationValidator.ConfigValidationError.Severity.WARNING));
            }
        }

        return new ConfigurationValidator.ValidationResult(errors.isEmpty(), errors, warnings, info);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnabledTracking() {
        return enabled;
    }

    public String getEndpoint() {
        return endpoint;
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
