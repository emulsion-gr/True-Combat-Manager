package com.muzlik.pvpcombat.utils;

import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * Factory and detector for {@link CompatibilityAPI} implementations.
 *
 * <p>Inspects the running Minecraft version at startup and selects the
 * appropriate adapter. The selected adapter is cached for the lifetime of
 * the plugin.</p>
 */
public class CompatibilityManager {

    private static final Logger logger = Logger.getLogger("TrueCombatManager");
    private static CompatibilityAPI api;

    private CompatibilityManager() {
        // Prevent instantiation
    }

    /**
     * Detects the Minecraft version and returns the appropriate adapter.
     * Results are cached after the first call.
     *
     * @param plugin the plugin instance (used for logging)
     * @return a version-appropriate CompatibilityAPI
     */
    public static synchronized CompatibilityAPI create(PvPCombatPlugin plugin) {
        if (api != null) return api;

        VersionCompatibility.MinecraftVersion v = VersionCompatibility.getVersion();

        if (VersionCompatibility.isAtLeast(1, 20, 0)) {
            api = new ModernCompatibilityAdapter(plugin);
            logger.info("Using ModernCompatibilityAdapter (Minecraft 1.20+ Adventure API)");
        } else {
            api = new LegacyCompatibilityAdapter(plugin);
            logger.info("Using LegacyCompatibilityAdapter (Minecraft 1.19.x ChatColor API)");
        }

        logger.info("Compatibility tier: " + api.getTier());
        return api;
    }

    /**
     * Returns the currently active adapter, or null if not yet initialized.
     * Call {@link #create(PvPCombatPlugin)} first.
     */
    public static CompatibilityAPI get() {
        return api;
    }

    /**
     * Resets the cached adapter (useful for testing).
     */
    static synchronized void reset() {
        api = null;
    }
}
