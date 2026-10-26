package com.muzlik.pvpcombat.core;

import com.muzlik.pvpcombat.commands.AdminCommand;
import com.muzlik.pvpcombat.commands.CombatCommand;
import com.muzlik.pvpcombat.commands.ReplayCommand;
import com.muzlik.pvpcombat.config.ConfigManager;
import com.muzlik.pvpcombat.database.SQLiteDatabaseManager;
import com.muzlik.pvpcombat.database.MySQLDatabaseManager;
import com.muzlik.pvpcombat.interfaces.IDatabaseManager;
import com.muzlik.pvpcombat.logging.CombatLogger;
import com.muzlik.pvpcombat.interfaces.ICombatManager;
import com.muzlik.pvpcombat.interfaces.IConfigManager;
import com.muzlik.pvpcombat.interfaces.IRestrictionManager;
import com.muzlik.pvpcombat.interfaces.IVisualManager;
import com.muzlik.pvpcombat.visual.VisualManager;
import com.muzlik.pvpcombat.combat.CombatTracker;
import com.muzlik.pvpcombat.combat.CombatManager;
import com.muzlik.pvpcombat.performance.PerformanceMonitor;
import com.muzlik.pvpcombat.performance.TPSMonitor;
import com.muzlik.pvpcombat.utils.CacheManager;
import com.muzlik.pvpcombat.utils.VersionCompatibility;
import com.muzlik.pvpcombat.restrictions.RestrictionManager;
import com.muzlik.pvpcombat.combat.AntiInterferenceManager;
import com.muzlik.pvpcombat.protection.NewbieProtection;
import com.muzlik.pvpcombat.tracking.ServerTracker;
import com.muzlik.pvpcombat.config.ServerTrackingConfig;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import com.muzlik.pvpcombat.events.CombatEventListener;

import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Coordinates all subsystems and manages plugin lifecycle.
 */
public class PluginManager {

    private final PvPCombatPlugin plugin;

    private ICombatManager combatManager;
    private IVisualManager visualManager;
    private IRestrictionManager restrictionManager;
    private IConfigManager configManager;
    private CombatTracker combatTracker;
    private NewbieProtection newbieProtection;
    private IDatabaseManager databaseManager;
    private CombatLogger combatLogger;
    private ServerTracker serverTracker;

    public PluginManager(PvPCombatPlugin plugin) {
        this.plugin = plugin;
        initializeManagers();
    }

    private void initializeManagers() {
        // Detect and log Minecraft version
        VersionCompatibility.detectVersion();
        VersionCompatibility.logVersionInfo();
        
        // Warn if version is not supported
        if (!VersionCompatibility.isSupported()) {
            plugin.getLogger().warning("Running on an unsupported Minecraft version!");
            plugin.getLogger().warning("Some features may not work correctly.");
        }
        
        // Initialize configuration manager first as others depend on it
        this.configManager = new ConfigManager(plugin);
        
        // Initialize combat tracker with plugin reference
        this.combatTracker = new CombatTracker(plugin);
        
        // Initialize newbie protection
        this.newbieProtection = new NewbieProtection(plugin);

        // Initialize shared components
        CacheManager cacheManager = new CacheManager(plugin);
        this.combatLogger = new CombatLogger(plugin);
        TPSMonitor tpsMonitor = new TPSMonitor(plugin);
        PerformanceMonitor performanceMonitor = new PerformanceMonitor(plugin, tpsMonitor, cacheManager);

        // Initialize other managers
        this.combatManager = new CombatManager(plugin, combatLogger, null, performanceMonitor, cacheManager, configManager);
        this.visualManager = new VisualManager(plugin, configManager);
        this.restrictionManager = new RestrictionManager((CombatManager) combatManager, cacheManager);

        // Initialize server tracker (will start heartbeat after config is loaded in onEnable)
        if (configManager instanceof com.muzlik.pvpcombat.config.ConfigManager) {
            this.serverTracker = new ServerTracker(plugin, ((com.muzlik.pvpcombat.config.ConfigManager) configManager).getServerTrackingConfig());
        } else {
            plugin.getLogger().warning("ConfigManager is not of expected type — server tracking disabled.");
            this.serverTracker = null;
        }

        plugin.getLogger().info("Plugin managers initialized successfully.");
    }
    
    /**
     * Initialize the database manager based on configuration.
     */
    public void initializeDatabase() {
        FileConfiguration config = plugin.getConfig();
        String databaseType = config.getString("database.type", "sqlite").toLowerCase();
        
        try {
            if (databaseType.equals("mysql")) {
                String host = config.getString("database.mysql.host", "localhost");
                int port = config.getInt("database.mysql.port", 3306);
                String database = config.getString("database.mysql.database", "pvpcombat");
                String username = config.getString("database.mysql.username", "root");
                String password = config.getString("database.mysql.password", "");
                
                databaseManager = new MySQLDatabaseManager(
                    plugin.getLogger(), host, port, database, username, password
                );
                plugin.getLogger().info("Using MySQL database");
            } else {
                databaseManager = new SQLiteDatabaseManager(
                    plugin.getLogger(), plugin.getDataFolder()
                );
                plugin.getLogger().info("Using SQLite database");
            }
            
            databaseManager.initialize();

            // Re-inject database manager into combat tracker
            if (combatTracker != null) {
                combatTracker.setDatabaseManager(databaseManager);
                combatTracker.initialize();
            }

            plugin.getLogger().info("Database initialized successfully");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            plugin.getLogger().warning("Plugin will continue without persistent storage");
            databaseManager = null;
        }
    }

    /**
     * Registers all event listeners.
     */
    public void registerEvents() {
        // Initialize shared components for event listener
        CacheManager cacheManager = new CacheManager(plugin);
        TPSMonitor tpsMonitor = new TPSMonitor(plugin);
        PerformanceMonitor performanceMonitor = new PerformanceMonitor(plugin, tpsMonitor, cacheManager);

        // Register CombatEventListener with shared newbieProtection
        CombatEventListener combatListener = new CombatEventListener(
            plugin, 
            (CombatManager) combatManager, 
            combatTracker, 
            new AntiInterferenceManager(plugin, combatManager),
            (RestrictionManager) restrictionManager,
            combatLogger,
            performanceMonitor,
            cacheManager,
            newbieProtection
        );
        Bukkit.getPluginManager().registerEvents(combatListener, plugin);
        
        // Register SafeZoneManager
        com.muzlik.pvpcombat.restrictions.SafeZoneManager safeZoneManager = 
            new com.muzlik.pvpcombat.restrictions.SafeZoneManager(plugin, combatManager);
        Bukkit.getPluginManager().registerEvents(safeZoneManager, plugin);
        
        plugin.getLogger().info("Event listeners registered.");

        // Start server tracking heartbeat after config is loaded
        if (serverTracker != null) {
            serverTracker.start();
        }
    }

    /**
     * Registers all commands.
     */
    public void registerCommands() {
        // Register main combat command
        CombatCommand combatCommand = new CombatCommand(plugin);
        if (plugin.getCommand("combat") != null) {
            plugin.getCommand("combat").setExecutor(combatCommand);
            plugin.getCommand("combat").setTabCompleter(combatCommand);
        } else {
            plugin.getLogger().warning("Combat command not found in plugin.yml!");
        }

        // Register combatadmin command with dedicated AdminCommand executor
        if (plugin.getCommand("combatadmin") != null) {
            AdminCommand adminCommand = new AdminCommand(plugin);
            plugin.getCommand("combatadmin").setExecutor(adminCommand);
            plugin.getCommand("combatadmin").setTabCompleter(adminCommand);
        } else {
            plugin.getLogger().warning("Combatadmin command not found in plugin.yml!");
        }

        // Register replay command with dedicated ReplayCommand executor
        if (plugin.getCommand("replay") != null) {
            ReplayCommand replayCommand = new ReplayCommand(plugin, combatLogger);
            plugin.getCommand("replay").setExecutor(replayCommand);
            plugin.getCommand("replay").setTabCompleter(replayCommand);
        } else {
            plugin.getLogger().warning("Replay command not found in plugin.yml!");
        }

        plugin.getLogger().info("Commands registered successfully.");
    }

    /**
     * Shuts down all subsystems.
     */
    public void shutdown() {
        // Shutdown server tracker
        if (serverTracker != null) {
            serverTracker.stop();
        }

        // Shutdown combat tracker (saves all data)
        if (combatTracker != null) {
            combatTracker.shutdown();
        }
        
        // Shutdown database
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        
        // Cleanup combat manager (includes disconnect tracker)
        if (combatManager instanceof CombatManager) {
            ((CombatManager) combatManager).cleanup();
        }
        
        plugin.getLogger().info("Plugin subsystems shut down.");
    }

    // Getters for managers
    public ICombatManager getCombatManager() {
        return combatManager;
    }

    public CombatTracker getCombatTracker() {
        return combatTracker;
    }

    public IVisualManager getVisualManager() {
        return visualManager;
    }

    public IRestrictionManager getRestrictionManager() {
        return restrictionManager;
    }

    public IConfigManager getConfigManager() {
        return configManager;
    }
    
    public NewbieProtection getNewbieProtection() {
        return newbieProtection;
    }
    
    public IDatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ServerTracker getServerTracker() {
        return serverTracker;
    }
}
