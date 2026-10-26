package com.muzlik.pvpcombat.config;

import com.muzlik.pvpcombat.interfaces.IConfigManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;

/**
 * Main configuration orchestrator that manages all sub-configurations.
 * Provides centralized access to all configuration settings with hot-reload capability.
 */
public class ConfigManager implements IConfigManager {

    private final Plugin plugin;
    private final Logger logger;
    private final ConfigurationValidator validator;

    // Configuration files
    private File mainConfigFile;
    private File messagesConfigFile;
    private FileConfiguration mainConfig;
    private FileConfiguration messagesConfig;

    // Sub-configurations
    private final Map<String, SubConfig> subConfigs;
    private CombatConfig combatConfig;
    private VisualConfig visualConfig;
    private RestrictionConfig restrictionConfig;
    private PerformanceConfig performanceConfig;
    private IntegrationConfig integrationConfig;
    private LoggingConfig loggingConfig;
    private AntiCheatConfig antiCheatConfig;
    private ReplayConfig replayConfig;
    private DisconnectConfig disconnectConfig;
    private ServerTrackingConfig serverTrackingConfig;

    // Reload listeners
    private final List<Runnable> reloadListeners;
    private boolean isReloading = false;

    /**
     * Creates a new configuration manager.
     *
     * @param plugin The plugin instance
     */
    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.validator = new ConfigurationValidator();
        this.subConfigs = new ConcurrentHashMap<>();
        this.reloadListeners = Collections.synchronizedList(new ArrayList<>());
    }

    public void loadConfig() {
        try {
            // Create config directory if it doesn't exist
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Initialize config files
            mainConfigFile = new File(plugin.getDataFolder(), "config.yml");
            messagesConfigFile = new File(plugin.getDataFolder(), "messages.yml");

            // Load main config
            if (!mainConfigFile.exists()) {
                plugin.saveResource("config.yml", false);
                logger.info("Created new config.yml with version 2.0");
            } else {
                // Check config version
                FileConfiguration existingConfig = YamlConfiguration.loadConfiguration(mainConfigFile);
                int configVersion = existingConfig.getInt("config-version", 1);
                
                if (configVersion < 2) {
                    logger.warning("Old config version detected (" + configVersion + "). Backing up and creating new config...");
                    
                    // Backup old config
                    File backupFile = new File(plugin.getDataFolder(), "config.yml.backup");
                    if (mainConfigFile.renameTo(backupFile)) {
                        logger.info("Old config backed up to config.yml.backup");
                    }
                    
                    // Create new config
                    plugin.saveResource("config.yml", true);
                    logger.info("Created new config.yml with version 2.0");
                }
            }
            mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);

            // Load messages config
            if (!messagesConfigFile.exists()) {
                plugin.saveResource("messages.yml", false);
            }
            messagesConfig = YamlConfiguration.loadConfiguration(messagesConfigFile);

            // Initialize sub-configurations
            initializeSubConfigs();

            // Validate configurations
            ConfigurationValidator.ValidationResult result = validateConfigInternal();
            if (result.hasErrors()) {
                logger.warning("Configuration validation errors found:");
                for (ConfigurationValidator.ConfigValidationError error : result.getErrors()) {
                    logger.warning("ERROR: " + error.toString());
                }
            }
            if (result.hasWarnings()) {
                logger.info("Configuration validation warnings:");
                for (ConfigurationValidator.ConfigValidationError warning : result.getWarnings()) {
                    logger.info("WARNING: " + warning.toString());
                }
            }

            logger.info("Configuration loaded successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load configuration", e);
            throw new RuntimeException("Configuration loading failed", e);
        }
    }

    public void reloadConfig() {
        isReloading = true;
        try {
            // Reload Bukkit's own config cache first so plugin.getConfig() stays in sync
            plugin.reloadConfig();

            // Reload main configs
            mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);
            messagesConfig = YamlConfiguration.loadConfiguration(messagesConfigFile);

            // Reload all sub-configs
            for (SubConfig subConfig : subConfigs.values()) {
                subConfig.reload();
            }

            // Notify listeners
            synchronized (reloadListeners) {
                for (Runnable listener : reloadListeners) {
                    try {
                        listener.run();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error in reload listener", e);
                    }
                }
            }

            // Reload all managers
            com.muzlik.pvpcombat.core.PvPCombatPlugin plugin = com.muzlik.pvpcombat.core.PvPCombatPlugin.getInstance();
            if (plugin != null) {
                // Reload combat manager
                if (plugin.getCombatManager() instanceof com.muzlik.pvpcombat.combat.CombatManager) {
                    ((com.muzlik.pvpcombat.combat.CombatManager) plugin.getCombatManager()).reloadConfig();
                }
                
                // Reload visual manager
                if (plugin.getVisualManager() instanceof com.muzlik.pvpcombat.visual.VisualManager) {
                    ((com.muzlik.pvpcombat.visual.VisualManager) plugin.getVisualManager()).reloadConfig();
                }
                
                // Reload restriction manager
                if (plugin.getRestrictionManager() instanceof com.muzlik.pvpcombat.restrictions.RestrictionManager) {
                    ((com.muzlik.pvpcombat.restrictions.RestrictionManager) plugin.getRestrictionManager()).reloadConfig();
                }

                // Reload logging manager
                if (plugin.getLoggingManager() != null) {
                    plugin.getLoggingManager().reloadConfig();
                }

                // Reload disconnect tracker
                if (plugin.getCombatManager() instanceof com.muzlik.pvpcombat.combat.CombatManager) {
                    com.muzlik.pvpcombat.combat.DisconnectTracker dt =
                        ((com.muzlik.pvpcombat.combat.CombatManager) plugin.getCombatManager()).getDisconnectTracker();
                    if (dt != null) dt.reloadConfig();
                }
            }

            logger.info("Configuration reloaded successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to reload configuration", e);
        } finally {
            isReloading = false;
        }
    }

    public ConfigurationValidator.ValidationResult validateConfigInternal() {
        ConfigurationValidator.ValidationResult result = validator.validateConfig(mainConfig);

        // Validate sub-configs
        for (SubConfig subConfig : subConfigs.values()) {
            ConfigurationValidator.ValidationResult subResult = subConfig.validate();
            result.getErrors().addAll(subResult.getErrors());
            result.getWarnings().addAll(subResult.getWarnings());
        }

        return result;
    }

    public boolean validateConfig() {
        return !validateConfigInternal().hasErrors();
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public void saveConfig() {
        try {
            mainConfig.save(mainConfigFile);
            messagesConfig.save(messagesConfigFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save configuration", e);
        }
    }

    public Object getConfigValue(String path) {
        return mainConfig.get(path);
    }

    public void setConfigValue(String path, Object value) {
        mainConfig.set(path, value);
    }

    /**
     * Initializes all sub-configurations in priority order.
     */
    private void initializeSubConfigs() {
        List<SubConfig> configs = Arrays.asList(
            combatConfig = new CombatConfig(validator, mainConfig),
            visualConfig = new VisualConfig(validator, mainConfig),
            restrictionConfig = new RestrictionConfig(validator, mainConfig),
            performanceConfig = new PerformanceConfig(validator, mainConfig),
            integrationConfig = new IntegrationConfig(validator, mainConfig),
            loggingConfig = new LoggingConfig(validator, mainConfig),
            antiCheatConfig = new AntiCheatConfig(validator, mainConfig),
            replayConfig = new ReplayConfig(validator, mainConfig),
            disconnectConfig = new DisconnectConfig(validator, mainConfig),
            serverTrackingConfig = new ServerTrackingConfig(validator, mainConfig)
        );

        // Sort by load priority and load
        configs.stream()
            .sorted(Comparator.comparingInt(SubConfig::getLoadPriority))
            .forEach(config -> {
                config.load();
                subConfigs.put(config.getSectionName(), config);
            });
    }

    /**
     * Registers a reload listener that gets called when configuration is reloaded.
     *
     * @param listener The listener to register
     */
    public void addReloadListener(Runnable listener) {
        reloadListeners.add(listener);
    }

    /**
     * Removes a reload listener.
     *
     * @param listener The listener to remove
     */
    public void removeReloadListener(Runnable listener) {
        reloadListeners.remove(listener);
    }

    /**
     * Gets a sub-configuration by section name.
     *
     * @param sectionName The section name
     * @return The sub-config, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends SubConfig> T getSubConfig(String sectionName) {
        return (T) subConfigs.get(sectionName);
    }

    /**
     * Checks if the configuration is currently being reloaded.
     *
     * @return true if reloading, false otherwise
     */
    public boolean isReloading() {
        return isReloading;
    }

    // Convenience getters for sub-configs
    public CombatConfig getCombatConfig() {
        return combatConfig;
    }

    public VisualConfig getVisualConfig() {
        return visualConfig;
    }

    public RestrictionConfig getRestrictionConfig() {
        return restrictionConfig;
    }

    public PerformanceConfig getPerformanceConfig() {
        return performanceConfig;
    }

    public IntegrationConfig getIntegrationConfig() {
        return integrationConfig;
    }

    public LoggingConfig getLoggingConfig() {
        return loggingConfig;
    }

    public AntiCheatConfig getAntiCheatConfig() {
        return antiCheatConfig;
    }

    public ReplayConfig getReplayConfig() {
        return replayConfig;
    }

    public DisconnectConfig getDisconnectConfig() {
        return disconnectConfig;
    }

    public ServerTrackingConfig getServerTrackingConfig() {
        return serverTrackingConfig;
    }

    /**
     * Gets all loaded sub-configurations.
     *
     * @return Immutable map of sub-configurations
     */
    public Map<String, SubConfig> getAllSubConfigs() {
        return Collections.unmodifiableMap(subConfigs);
    }

    /**
     * Gets configuration statistics for debugging.
     *
     * @return Map of statistics
     */
    public Map<String, Object> getConfigStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_sub_configs", subConfigs.size());
        stats.put("reload_listeners", reloadListeners.size());
        stats.put("is_reloading", isReloading);

        List<String> enabledSections = new ArrayList<>();
        for (SubConfig config : subConfigs.values()) {
            if (config.isEnabled()) {
                enabledSections.add(config.getSectionName());
            }
        }
        stats.put("enabled_sections", enabledSections);

        return stats;
    }
}