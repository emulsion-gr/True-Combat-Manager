package com.muzlik.pvpcombat.visual;

import com.muzlik.pvpcombat.combat.CombatManager;
import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import com.muzlik.pvpcombat.data.CombatSession;
import com.muzlik.pvpcombat.interfaces.IConfigManager;
import com.muzlik.pvpcombat.utils.CompatibilityAPI;
import com.muzlik.pvpcombat.utils.CompatibilityManager;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages bossbar creation, updates, and theming for combat sessions.
 */
public class BossBarManager {

    private final PvPCombatPlugin plugin;
    private final Map<String, BossBar> activeBossBars;
    private final ThemeManager themeManager;
    private final CompatibilityAPI compatibility;

    public BossBarManager(PvPCombatPlugin plugin, IConfigManager configManager) {
        this.plugin = plugin;
        this.activeBossBars = new ConcurrentHashMap<>();
        this.themeManager = new ThemeManager(configManager, plugin.getLogger());
        this.compatibility = CompatibilityManager.create(plugin);
    }

    /**
     * Displays a bossbar for a combat session.
     */
    public void displayBossBar(String sessionId) {
        CombatSession session = ((CombatManager) plugin.getCombatManager()).getSessionById(sessionId);
        if (session == null || !session.isVisualsEnabled()) return;

        // Check if bossbar is enabled in configuration
        if (!plugin.getConfig().getBoolean("visual.bossbar.enabled", true)) {
            return;
        }

        BossBar bossBar = createBossBar(session);
        if (bossBar != null) {
            Player attacker = session.getAttacker();
            Player defender = session.getDefender();

            boolean attackerEnabled = true;
            boolean defenderEnabled = true;
            if (plugin.getVisualManager() != null) {
                attackerEnabled = ((VisualManager) plugin.getVisualManager()).getPreferences(attacker.getUniqueId()).isBossBarEnabled();
                defenderEnabled = ((VisualManager) plugin.getVisualManager()).getPreferences(defender.getUniqueId()).isBossBarEnabled();
            }
            if (attackerEnabled) bossBar.addPlayer(attacker);
            if (defenderEnabled) bossBar.addPlayer(defender);
            activeBossBars.put(sessionId, bossBar);
        }
    }

    /**
     * Refreshes active bossbars for players involved, checking their custom preferences.
     */
    public void refreshPlayerParticipation(String sessionId) {
        BossBar bossBar = activeBossBars.get(sessionId);
        CombatSession session = ((CombatManager) plugin.getCombatManager()).getSessionById(sessionId);
        if (bossBar == null || session == null) return;

        Player attacker = session.getAttacker();
        Player defender = session.getDefender();

        if (attacker != null) {
            boolean enabled = true;
            if (plugin.getVisualManager() != null) {
                enabled = ((VisualManager) plugin.getVisualManager()).getPreferences(attacker.getUniqueId()).isBossBarEnabled();
            }
            if (enabled) {
                if (!bossBar.getPlayers().contains(attacker)) {
                    bossBar.addPlayer(attacker);
                }
            } else {
                bossBar.removePlayer(attacker);
            }
        }

        if (defender != null) {
            boolean enabled = true;
            if (plugin.getVisualManager() != null) {
                enabled = ((VisualManager) plugin.getVisualManager()).getPreferences(defender.getUniqueId()).isBossBarEnabled();
            }
            if (enabled) {
                if (!bossBar.getPlayers().contains(defender)) {
                    bossBar.addPlayer(defender);
                }
            } else {
                bossBar.removePlayer(defender);
            }
        }
    }

    /**
     * Updates the progress of a bossbar.
     */
    public void updateProgress(String sessionId, double progress) {
        BossBar bossBar = activeBossBars.get(sessionId);
        if (bossBar != null) {
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        }
    }

    /**
     * Updates bossbar title with current timer.
     */
    public void updateTitle(String sessionId, String title) {
        BossBar bossBar = activeBossBars.get(sessionId);
        if (bossBar != null) {
            bossBar.setTitle(title);
        }
    }

    /**
     * Clears bossbar for a specific player.
     */
    public void clearBossBar(Player player) {
        for (BossBar bossBar : activeBossBars.values()) {
            bossBar.removePlayer(player);
        }
    }

    /**
     * Clears bossbar for a session.
     */
    public void clearBossBar(String sessionId) {
        BossBar bossBar = activeBossBars.remove(sessionId);
        if (bossBar != null) {
            bossBar.setVisible(false);
            bossBar.removeAll();
        }
    }

    /**
     * Clears all active bossbars.
     */
    public void clearAllBossBars() {
        for (BossBar bossBar : activeBossBars.values()) {
            bossBar.setVisible(false);
            bossBar.removeAll();
        }
        activeBossBars.clear();
    }

    /**
     * Applies theme to an existing bossbar with optional animation.
     */
    public void applyTheme(String sessionId, String themeName) {
        applyTheme(sessionId, themeName, false);
    }

    /**
     * Applies theme with optional animated transition.
     */
    public void applyTheme(String sessionId, String themeName, boolean animated) {
        BossBar bossBar = activeBossBars.get(sessionId);
        CombatSession session = ((CombatManager) plugin.getCombatManager()).getSessionById(sessionId);

        if (bossBar != null && session != null) {
            ThemeManager.Theme theme = themeManager.getTheme(themeName);
            if (theme != null) {
                if (animated && theme.hasAnimatedTransitions()) {
                    animateThemeTransition(bossBar, theme);
                } else {
                    bossBar.setColor(theme.getBossBarColor());
                    bossBar.setStyle(theme.getBossBarStyle());
                }
                session.setCurrentTheme(themeName);
            }
        }
    }

    /**
     * Animates theme transition with smooth color/style changes.
     */
    private void animateThemeTransition(BossBar bossBar, ThemeManager.Theme newTheme) {
        // Store original values
        BarColor originalColor = bossBar.getColor();
        BarStyle originalStyle = bossBar.getStyle();

        // Animation duration in ticks (1 second = 20 ticks)
        int animationDuration = plugin.getConfig().getInt("visual.animations.theme-transition-duration", 20);
        int steps = plugin.getConfig().getInt("visual.animations.theme-transition-steps", 5);

        if (animationDuration <= 0 || steps <= 1) {
            // No animation, apply immediately
            bossBar.setColor(newTheme.getBossBarColor());
            bossBar.setStyle(newTheme.getBossBarStyle());
            return;
        }

        // For now, implement simple style change animation
        // Full color interpolation would require more complex logic
        int delayPerStep = animationDuration / steps;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            bossBar.setColor(newTheme.getBossBarColor());
            bossBar.setStyle(newTheme.getBossBarStyle());
        }, delayPerStep);
    }

    /**
     * Creates a bossbar with appropriate theming and enhanced formatting.
     */
    private BossBar createBossBar(CombatSession session) {
        ThemeManager.Theme theme = themeManager.getTheme(session.getCurrentTheme());
        if (theme == null) {
            theme = themeManager.getDefaultTheme();
        }

        // Use MessageFormatter for enhanced placeholder replacement
        MessageFormatter formatter = new MessageFormatter(plugin);
        java.util.Map<String, Object> placeholders = new java.util.HashMap<>();
        placeholders.put("time_left", session.getRemainingTime());
        placeholders.put("opponent", session.getAttacker().getName().equals(session.getDefender().getName()) ?
            "Unknown" : session.getDefender().getName());

        String title = formatter.formatMessage(theme.getBossBarTitle(), session.getAttacker(), placeholders);

        BossBar bossBar = compatibility.createBossBar(
            title,
            theme.getBossBarColor(),
            theme.getBossBarStyle()
        );

        bossBar.setProgress(session.getTimerData().getProgress());
        bossBar.setVisible(true);

        return bossBar;
    }

    /**
     * Gets the active bossbars map for cleanup purposes.
     */
    public Map<String, BossBar> getActiveBossBars() {
        return new ConcurrentHashMap<>(activeBossBars);
    }

    /**
     * Reloads configuration for bossbar manager.
     */
    public void reloadConfig() {
        themeManager.loadThemes();
        plugin.getLogger().fine("BossBarManager configuration reloaded");
    }
}