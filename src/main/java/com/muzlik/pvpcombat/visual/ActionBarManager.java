package com.muzlik.pvpcombat.visual;

import com.muzlik.pvpcombat.combat.CombatManager;
import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import com.muzlik.pvpcombat.data.CombatSession;
import com.muzlik.pvpcombat.utils.CompatibilityAPI;
import com.muzlik.pvpcombat.utils.CompatibilityManager;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages action bar messages with HEX color support and real-time updates.
 *
 * <p>Uses {@link CompatibilityAPI} to send action bars in a version-agnostic way,
 * supporting Minecraft 1.19.x through 1.23+ and future major versions.</p>
 */
public class ActionBarManager {

    private final PvPCombatPlugin plugin;
    private final Map<UUID, BukkitRunnable> activeActionBars;
    private final CompatibilityAPI compatibility;

    public ActionBarManager(PvPCombatPlugin plugin) {
        this.plugin = plugin;
        this.activeActionBars = new ConcurrentHashMap<>();
        this.compatibility = CompatibilityManager.create(plugin);
    }

    /**
     * Sends an action bar message to a player.
     */
    public void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (!plugin.getConfig().getBoolean("visual.actionbar.enabled", true)) {
            return;
        }
        if (plugin.getVisualManager() != null) {
            if (!((VisualManager) plugin.getVisualManager()).getPreferences(player.getUniqueId()).isActionBarEnabled()) {
                return;
            }
        }

        compatibility.sendActionBar(player, message);
    }

    /**
     * Starts real-time action bar updates for a combat session.
     */
    public void startActionBarUpdates(String sessionId, Player player1, Player player2) {
        if (!plugin.getConfig().getBoolean("visual.actionbar.enabled", true)) {
            return;
        }

        int updateInterval = plugin.getConfig().getInt("visual.actionbar.update-interval", 20);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                // Check if players are still online and in combat
                if (player1 == null || player2 == null ||
                    !player1.isOnline() || !player2.isOnline() ||
                    !plugin.getCombatManager().isInCombat(player1) ||
                    !plugin.getCombatManager().isInCombat(player2)) {
                    cancel();
                    return;
                }

                boolean player1Enabled = true;
                boolean player2Enabled = true;
                if (plugin.getVisualManager() != null) {
                    player1Enabled = ((VisualManager) plugin.getVisualManager()).getPreferences(player1.getUniqueId()).isActionBarEnabled();
                    player2Enabled = ((VisualManager) plugin.getVisualManager()).getPreferences(player2.getUniqueId()).isActionBarEnabled();
                }

                if (player1Enabled) {
                    String message1 = buildActionBarMessage(player1, player2);
                    sendActionBar(player1, message1);
                } else {
                    // Clear if it was turned off during combat
                    compatibility.clearActionBar(player1);
                }

                if (player2Enabled) {
                    String message2 = buildActionBarMessage(player2, player1);
                    sendActionBar(player2, message2);
                } else {
                    // Clear if it was turned off during combat
                    compatibility.clearActionBar(player2);
                }
            }
        };

        task.runTaskTimer(plugin, 0L, updateInterval);
        activeActionBars.put(player1.getUniqueId(), task);
        activeActionBars.put(player2.getUniqueId(), task);
    }

    /**
     * Clears action bar for a specific player.
     */
    public void clearActionBar(Player player) {
        BukkitRunnable task = activeActionBars.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        // Send empty message to clear action bar
        compatibility.clearActionBar(player);
    }

    /**
     * Clears all active action bars.
     */
    public void clearAllActionBars() {
        for (BukkitRunnable task : activeActionBars.values()) {
            task.cancel();
        }
        activeActionBars.clear();
    }

    /**
     * Builds the action bar message with opponent and time information.
     */
    private String buildActionBarMessage(Player player, Player opponent) {
        String format = plugin.getConfig().getString("visual.actionbar.format",
            "&cCombat with &f{opponent} &c- &f{time_left}s");

        int timeLeft = getCombatTimeLeft(player);

        Map<String, Object> placeholders = new java.util.HashMap<>();
        placeholders.put("opponent", opponent != null ? opponent.getName() : "Unknown");
        placeholders.put("time_left", String.valueOf(timeLeft));

        MessageFormatter formatter = new MessageFormatter(plugin);
        return compatibility.translateColor(formatter.formatMessage(format, player, placeholders));
    }

    /**
     * Gets remaining combat time for a player.
     */
    private int getCombatTimeLeft(Player player) {
        CombatManager cm = (CombatManager) plugin.getCombatManager();
        for (CombatSession session : cm.getActiveSessions().values()) {
            if (session.getAttacker().getUniqueId().equals(player.getUniqueId()) ||
                session.getDefender().getUniqueId().equals(player.getUniqueId())) {
                return session.getRemainingTime();
            }
        }
        return 0;
    }

    /**
     * Gets active action bar tasks for cleanup.
     */
    public Map<UUID, BukkitRunnable> getActiveActionBars() {
        return new ConcurrentHashMap<>(activeActionBars);
    }
}
