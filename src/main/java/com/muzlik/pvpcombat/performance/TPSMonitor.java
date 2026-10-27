package com.muzlik.pvpcombat.performance;

import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import com.muzlik.pvpcombat.utils.CompatibilityAPI;
import com.muzlik.pvpcombat.utils.CompatibilityManager;

/**
 * Monitors server TPS (Ticks Per Second) and provides performance metrics.
 * Uses Minecraft's internal TPS tracking for accurate measurements.
 *
 * <p>Uses {@link CompatibilityAPI} for version-agnostic NMS server access.</p>
 */
public class TPSMonitor {

    private final PvPCombatPlugin plugin;
    private final CompatibilityAPI compatibility;

    // TPS tracking variables
    private volatile double currentTPS;
    private volatile double averageTPS;
    private volatile long lastUpdateTime;

    // TPS history for averaging (circular buffer)
    private final double[] tpsHistory;
    private int historyIndex;
    private int historySize;

    // Configuration
    private int historyLength; // How many samples to keep for averaging

    public TPSMonitor(PvPCombatPlugin plugin) {
        this.plugin = plugin;
        this.compatibility = CompatibilityManager.create(plugin);
        this.currentTPS = 20.0; // Assume perfect TPS initially
        this.averageTPS = 20.0;
        this.lastUpdateTime = System.currentTimeMillis();

        this.historyLength = plugin.getConfig().getInt("lag.tps-history-length", 60); // 60 samples = 1 minute at 1/sec updates
        this.tpsHistory = new double[historyLength];
        this.historyIndex = 0;
        this.historySize = 0;

        // Initialize history with perfect TPS
        for (int i = 0; i < historyLength; i++) {
            tpsHistory[i] = 20.0;
        }
    }

    /**
     * Updates the current TPS reading.
     * Should be called regularly (e.g., every second) by the LagManager.
     */
    public void updateTPS() {
        try {
            // Get TPS using Minecraft's internal method
            double[] tpsArray = getServerTPS();
            if (tpsArray != null && tpsArray.length > 0) {
                // TPS array typically contains [1m, 5m, 15m] averages
                // We use the 1-minute average as current TPS
                this.currentTPS = Math.min(20.0, Math.max(0.0, tpsArray[0]));

                // Update history and average
                updateTPSHistory(currentTPS);
                calculateAverageTPS();

                lastUpdateTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update TPS: " + e.getMessage());
            // Keep previous values on error
        }
    }

    /**
     * Gets the server's TPS using Minecraft internals.
     * This method uses reflection to access the TPS tracking in the Minecraft server.
     */
    private double[] getServerTPS() {
        try {
            // Access the MinecraftServer via compatibility layer
            Object minecraftServer = compatibility.getNmsServer();
            if (minecraftServer == null) {
                plugin.getLogger().fine("Could not access NMS server, using fallback method");
                return getFallbackTPS();
            }

            // Get the recent TPS values
            double[] tps = (double[]) minecraftServer.getClass()
                    .getField("recentTps").get(minecraftServer);

            return tps;
        } catch (Exception e) {
            plugin.getLogger().fine("Could not access TPS via reflection, using fallback method");
            return getFallbackTPS();
        }
    }

    /**
     * Fallback TPS calculation when direct access fails.
     * Uses system time and tick counting as a backup.
     */
    private double[] getFallbackTPS() {
        // This is a simplified fallback - in a real implementation,
        // you might track tick events or use other performance indicators
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - lastUpdateTime;

        if (timeDiff > 0) {
            // Estimate TPS based on time between updates
            // This is not accurate but provides a reasonable fallback
            double estimatedTPS = Math.min(20.0, 1000.0 / Math.max(50.0, timeDiff));
            return new double[]{estimatedTPS, estimatedTPS, estimatedTPS};
        }

        return new double[]{20.0, 20.0, 20.0}; // Default to perfect TPS
    }

    /**
     * Updates the TPS history with a new reading.
     */
    private void updateTPSHistory(double newTPS) {
        tpsHistory[historyIndex] = newTPS;
        historyIndex = (historyIndex + 1) % historyLength;
        if (historySize < historyLength) {
            historySize++;
        }
    }

    /**
     * Calculates the average TPS from history.
     */
    private void calculateAverageTPS() {
        if (historySize == 0) {
            averageTPS = 20.0;
            return;
        }

        double sum = 0.0;
        for (int i = 0; i < historySize; i++) {
            sum += tpsHistory[i];
        }
        averageTPS = sum / historySize;
    }

    /**
     * Gets the current TPS reading.
     */
    public double getCurrentTPS() {
        return currentTPS;
    }

    /**
     * Gets the average TPS over the configured history period.
     */
    public double getAverageTPS() {
        return averageTPS;
    }

    /**
     * Gets the TPS history array (for debugging/analysis).
     */
    public double[] getTPSHistory() {
        double[] history = new double[historySize];
        for (int i = 0; i < historySize; i++) {
            int index = (historyIndex - 1 - i + historyLength) % historyLength;
            history[i] = tpsHistory[index];
        }
        return history;
    }

    /**
     * Checks if TPS indicates lag based on a threshold.
     */
    public boolean isLagging(double threshold) {
        return currentTPS < threshold;
    }

    /**
     * Gets the time since last TPS update.
     */
    public long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }

    /**
     * Forces a configuration reload.
     */
    public void reloadConfiguration() {
        this.historyLength = plugin.getConfig().getInt("lag.tps-history-length", 60);
        plugin.getLogger().info("TPSMonitor configuration reloaded");
    }

    /**
     * Gets performance statistics for monitoring.
     */
    public String getPerformanceStats() {
        return String.format("TPSMonitor{current=%.2f, average=%.2f, historySize=%d/%d, lastUpdate=%dms ago}",
                currentTPS, averageTPS, historySize, historyLength, getTimeSinceLastUpdate());
    }

    /**
     * Gets TPS severity (0.0 = perfect, 1.0 = severe lag).
     */
    public double getTPSSeverity(double minAcceptableTPS) {
        if (currentTPS >= 20.0) return 0.0;
        if (currentTPS >= minAcceptableTPS) {
            return (20.0 - currentTPS) / (20.0 - minAcceptableTPS) * 0.5; // Moderate lag
        } else {
            return 0.5 + ((minAcceptableTPS - currentTPS) / minAcceptableTPS) * 0.5; // Severe lag
        }
    }

    /**
     * Resets the TPS monitor (useful for testing).
     */
    public void reset() {
        currentTPS = 20.0;
        averageTPS = 20.0;
        lastUpdateTime = System.currentTimeMillis();
        historyIndex = 0;
        historySize = 0;

        for (int i = 0; i < historyLength; i++) {
            tpsHistory[i] = 20.0;
        }
    }
}