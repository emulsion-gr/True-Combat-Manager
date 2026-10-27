package com.muzlik.pvpcombat.utils;

import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * Version-agnostic compatibility API for TrueCombatManager.
 *
 * <p>This interface abstracts away Minecraft version differences so the rest of
 * the plugin can call the same methods regardless of whether the server is
 * running 1.19.x (legacy ChatColor), 1.20+ (Adventure Component API),
 * 1.22+ (refined ActionBar), or 26.xx (major rework).</p>
 *
 * <p>Implementations are selected at runtime by {@link CompatibilityManager}
 * based on the detected Minecraft version.</p>
 */
public interface CompatibilityAPI {

    // -----------------------------------------------------------------------
    // Version info
    // -----------------------------------------------------------------------

    /**
     * Returns the compatibility tier name (e.g. "LEGACY (1.19.x)",
     * "MODERN (1.20+)", "REFINED (1.22+)", "MAJOR_REWORK (26+)").
     */
    String getTier();

    /**
     * Returns true if this version uses the Adventure Component API (1.20+).
     */
    boolean usesComponentAPI();

    /**
     * Returns true if this version has ActionBar API refinements (1.22+).
     */
    boolean usesRefinedActionBar();

    /**
     * Returns true if this is a major rework version (26+).
     */
    boolean isMajorRework();

    // -----------------------------------------------------------------------
    // Chat / text formatting
    // -----------------------------------------------------------------------

    /**
     * Translates alternate color codes ('&') to the appropriate chat format
     * for this version. On 1.20+ this returns an Adventure Component string
     * wrapper; on older versions it returns ChatColor-translated text.
     */
    String translateColor(String message);

    /**
     * Strips all color/format codes from a message.
     */
    String stripColor(String message);

    // -----------------------------------------------------------------------
    // BossBar
    // -----------------------------------------------------------------------

    /**
     * Creates a BossBar with the given title, color, and style.
     * The returned BossBar is ready to have players added.
     */
    BossBar createBossBar(String title, BarColor color, BarStyle style);

    // -----------------------------------------------------------------------
    // Action Bar
    // -----------------------------------------------------------------------

    /**
     * Sends an action bar message to a player.
     */
    void sendActionBar(Player player, String message);

    /**
     * Clears the action bar for a player (sends an empty message).
     */
    void clearActionBar(Player player);

    // -----------------------------------------------------------------------
    // Sound
    // -----------------------------------------------------------------------

    /**
     * Resolves a sound name to the appropriate {@link org.bukkit.Sound} for
     * this version. Returns null if the sound does not exist.
     */
    org.bukkit.Sound resolveSound(String soundName);

    // -----------------------------------------------------------------------
    // Title / Subtitle
    // -----------------------------------------------------------------------

    /**
     * Sends a title and subtitle to a player.
     *
     * @param player    the player
     * @param title     the title text (with color codes)
     * @param subtitle  the subtitle text (with color codes), or null
     * @param fadeIn    fade-in ticks
     * @param stay      stay ticks
     * @param fadeOut   fade-out ticks
     */
    void sendTitle(Player player, String title, String subtitle,
                    int fadeIn, int stay, int fadeOut);

    // -----------------------------------------------------------------------
    // NMS / server access (for TPS, etc.)
    // -----------------------------------------------------------------------

    /**
     * Returns the NMS MinecraftServer object, or null if unavailable.
     * Used by TPSMonitor and similar tools.
     */
    Object getNmsServer();

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Creates the appropriate CompatibilityAPI implementation for the
     * currently detected Minecraft version.
     *
     * @param plugin the plugin instance
     */
    static CompatibilityAPI create(PvPCombatPlugin plugin) {
        return CompatibilityManager.create(plugin);
    }
}
