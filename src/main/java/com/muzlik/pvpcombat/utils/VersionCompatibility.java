package com.muzlik.pvpcombat.utils;

import org.bukkit.Bukkit;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles version compatibility detection and management.
 * Supports Minecraft versions 1.19.4 through 1.21+
 */
public class VersionCompatibility {
    
    private static final Logger logger = Logger.getLogger("TrueCombatManager");
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\(MC: (\\d+)\\.(\\d+)(?:\\.(\\d+))?\\)");
    
    private static MinecraftVersion detectedVersion;
    private static boolean isSupported = false;
    
    /**
     * Represents a Minecraft version.
     */
    public static class MinecraftVersion implements Comparable<MinecraftVersion> {
        private final int major;
        private final int minor;
        private final int patch;
        
        public MinecraftVersion(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }
        
        public int getMajor() { return major; }
        public int getMinor() { return minor; }
        public int getPatch() { return patch; }
        
        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
        
        @Override
        public int compareTo(MinecraftVersion other) {
            if (this.major != other.major) {
                return Integer.compare(this.major, other.major);
            }
            if (this.minor != other.minor) {
                return Integer.compare(this.minor, other.minor);
            }
            return Integer.compare(this.patch, other.patch);
        }
        
        public boolean isAtLeast(MinecraftVersion other) {
            return this.compareTo(other) >= 0;
        }
        
        public boolean isAtMost(MinecraftVersion other) {
            return this.compareTo(other) <= 0;
        }
    }
    
    // Supported version range
    static final MinecraftVersion MIN_SUPPORTED = new MinecraftVersion(1, 19, 4);
    static final MinecraftVersion MAX_SUPPORTED = new MinecraftVersion(1, 23, 99); // 1.23.x and below
    
    // Version tiers for compatibility decisions
    static final MinecraftVersion V1_20_COMPONENT_API = new MinecraftVersion(1, 20, 0); // Adventure Component API introduced
    static final MinecraftVersion V1_22_ACTIONBAR_CHANGES = new MinecraftVersion(1, 22, 0); // ActionBar API refinements
    static final MinecraftVersion V26_MAJOR_REWORK = new MinecraftVersion(26, 0, 0); // Major rework version
    
    /**
     * Detects the current Minecraft version on server startup.
     * Logs warnings for unsupported versions.
     */
    public static void detectVersion() {
        String bukkitVersion;
        try {
            bukkitVersion = Bukkit.getVersion();
        } catch (Exception e) {
            // Bukkit server not available (e.g., in test environments)
            bukkitVersion = "(MC: 1.20.0)";
            logger.warning("Could not detect Minecraft version, defaulting to 1.20.0: " + e.getMessage());
        }
        logger.info("Detecting Minecraft version from: " + bukkitVersion);
        
        Matcher matcher = VERSION_PATTERN.matcher(bukkitVersion);
        
        if (matcher.find()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            
            detectedVersion = new MinecraftVersion(major, minor, patch);
            logger.info("Detected Minecraft version: " + detectedVersion);
            
            // Check if version is supported
            if (detectedVersion.isAtLeast(MIN_SUPPORTED) && detectedVersion.isAtMost(MAX_SUPPORTED)) {
                isSupported = true;
                logger.info("✓ This Minecraft version is officially supported!");
            } else if (detectedVersion.compareTo(MIN_SUPPORTED) < 0) {
                isSupported = false;
                logger.warning("╔════════════════════════════════════════════════════════════════╗");
                logger.warning("║  WARNING: UNSUPPORTED MINECRAFT VERSION                       ║");
                logger.warning("║                                                                ║");
                logger.warning("║  Detected: " + String.format("%-49s", detectedVersion) + "║");
                logger.warning("║  Minimum:  " + String.format("%-49s", MIN_SUPPORTED) + "║");
                logger.warning("║                                                                ║");
                logger.warning("║  This version is TOO OLD and may not work correctly.          ║");
                logger.warning("║  Please upgrade your server to Minecraft 1.19.4 or newer.    ║");
                logger.warning("║                                                                ║");
                logger.warning("║  The plugin will attempt to run, but errors may occur.        ║");
                logger.warning("╚════════════════════════════════════════════════════════════════╝");
            } else {
                isSupported = false;
                logger.warning("╔════════════════════════════════════════════════════════════════╗");
                logger.warning("║  WARNING: UNTESTED MINECRAFT VERSION                          ║");
                logger.warning("║                                                                ║");
                logger.warning("║  Detected: " + String.format("%-49s", detectedVersion) + "║");
                logger.warning("║  Maximum:  " + String.format("%-49s", MAX_SUPPORTED) + "║");
                logger.warning("║                                                                ║");
                logger.warning("║  This version is NEWER than tested versions.                  ║");
                logger.warning("║  The plugin may work, but has not been tested.                ║");
                logger.warning("║                                                                ║");
                logger.warning("║  Please report any issues to the plugin developer.            ║");
                logger.warning("╚════════════════════════════════════════════════════════════════╝");
            }
        } else {
            logger.severe("╔════════════════════════════════════════════════════════════════╗");
            logger.severe("║  ERROR: COULD NOT DETECT MINECRAFT VERSION                    ║");
            logger.severe("║                                                                ║");
            logger.severe("║  Bukkit version string: " + String.format("%-38s", bukkitVersion) + "║");
            logger.severe("║                                                                ║");
            logger.severe("║  The plugin may not work correctly.                           ║");
            logger.severe("║  Please contact the plugin developer.                         ║");
            logger.severe("╚════════════════════════════════════════════════════════════════╝");
            
            // Assume a reasonable default
            detectedVersion = new MinecraftVersion(1, 20, 0);
            isSupported = false;
        }
    }
    
    /**
     * Gets the detected Minecraft version.
     */
    public static MinecraftVersion getVersion() {
        if (detectedVersion == null) {
            detectVersion();
        }
        return detectedVersion;
    }
    
    /**
     * Checks if the current version is officially supported.
     */
    public static boolean isSupported() {
        if (detectedVersion == null) {
            detectVersion();
        }
        return isSupported;
    }
    
    /**
     * Checks if the current version is at least the specified version.
     */
    public static boolean isAtLeast(int major, int minor, int patch) {
        MinecraftVersion version = getVersion();
        return version.isAtLeast(new MinecraftVersion(major, minor, patch));
    }

    /**
     * Checks if the current version uses the Adventure Component API (1.20+).
     * In 1.20+, ChatColor is deprecated in favor of net.kyori.adventure.text.Component.
     */
    public static boolean usesComponentAPI() {
        return isAtLeast(1, 20, 0);
    }

    /**
     * Checks if the current version has ActionBar API refinements (1.22+).
     */
    public static boolean usesRefinedActionBar() {
        return isAtLeast(1, 22, 0);
    }

    /**
     * Checks if the current version is a major rework version (26+).
     */
    public static boolean isMajorRework() {
        return isAtLeast(26, 0, 0);
    }

    /**
     * Gets a human-readable compatibility tier description.
     */
    public static String getCompatibilityTier() {
        MinecraftVersion v = getVersion();
        if (v.isAtLeast(V26_MAJOR_REWORK)) return "MAJOR_REWORK (26+)";
        if (v.isAtLeast(V1_22_ACTIONBAR_CHANGES)) return "REFINED (1.22+)";
        if (v.isAtLeast(V1_20_COMPONENT_API)) return "MODERN (1.20+)";
        return "LEGACY (1.19.x)";
    }
    
    /**
     * Checks if the current version is at most the specified version.
     */
    public static boolean isAtMost(int major, int minor, int patch) {
        MinecraftVersion version = getVersion();
        return version.isAtMost(new MinecraftVersion(major, minor, patch));
    }
    
    /**
     * Checks if the current version is between two versions (inclusive).
     */
    public static boolean isBetween(int minMajor, int minMinor, int minPatch,
                                   int maxMajor, int maxMinor, int maxPatch) {
        return isAtLeast(minMajor, minMinor, minPatch) && isAtMost(maxMajor, maxMinor, maxPatch);
    }
    
    /**
     * Gets a version-compatible method name or fallback.
     * Useful for handling deprecated API methods.
     */
    public static String getCompatibleMethodName(String modernMethod, String legacyMethod) {
        // For future use when we need to handle deprecated methods
        if (isAtLeast(1, 20, 0)) {
            return modernMethod;
        } else {
            return legacyMethod;
        }
    }
    
    /**
     * Logs version information to console.
     */
    public static void logVersionInfo() {
        logger.info("════════════════════════════════════════════════════════════════");
        logger.info("  TrueCombatManager Version Compatibility");
        logger.info("════════════════════════════════════════════════════════════════");
        logger.info("  Minecraft Version: " + getVersion());
        logger.info("  Supported Range:   " + MIN_SUPPORTED + " - " + MAX_SUPPORTED);
        logger.info("  Compatibility Tier:" + getCompatibilityTier());
        logger.info("  Status:            " + (isSupported() ? "✓ SUPPORTED" : "⚠ UNSUPPORTED"));
        logger.info("  Server Software:   " + Bukkit.getName() + " " + Bukkit.getVersion());
        logger.info("════════════════════════════════════════════════════════════════");
    }
}
