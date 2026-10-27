package com.muzlik.pvpcombat.utils;

import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.Sound;

import java.util.logging.Logger;

/**
 * Abstract base implementation of {@link CompatibilityAPI}.
 *
 * <p>Subclasses override version-specific behavior. Common helper methods
 * are provided here so subclasses don't have to reimplement them.</p>
 */
public abstract class CompatibilityAdapter implements CompatibilityAPI {

    protected final PvPCombatPlugin plugin;
    protected final Logger logger;

    public CompatibilityAdapter(PvPCombatPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin != null ? plugin.getLogger() : java.util.logging.Logger.getGlobal();
    }

    // -----------------------------------------------------------------------
    // Default implementations (work on all versions)
    // -----------------------------------------------------------------------

    @Override
    public String stripColor(String message) {
        if (message == null) return "";
        // ChatColor.stripColor expects '§' color codes, but the plugin uses '&'.
        // Translate '&' to '§' first, then strip.
        String translated = message.replace('&', '§');
        return ChatColor.stripColor(translated);
    }

    @Override
    public BossBar createBossBar(String title, BarColor color, BarStyle style) {
        return org.bukkit.Bukkit.createBossBar(
                translateColor(title), color, style);
    }

    @Override
    public void clearActionBar(Player player) {
        if (player == null || !player.isOnline()) return;
        player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(""));
    }

    @Override
    public org.bukkit.Sound resolveSound(String soundName) {
        if (soundName == null || soundName.isEmpty()) return null;
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown sound: " + soundName);
            return null;
        }
    }

    @Override
    public Object getNmsServer() {
        try {
            return org.bukkit.Bukkit.getServer().getClass()
                    .getMethod("getServer").invoke(org.bukkit.Bukkit.getServer());
        } catch (Exception e) {
            logger.warning("Could not access NMS server: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void sendTitle(Player player, String title, String subtitle,
                          int fadeIn, int stay, int fadeOut) {
        if (player == null || !player.isOnline()) return;
        player.sendTitle(
                translateColor(title),
                subtitle != null ? translateColor(subtitle) : "",
                fadeIn, stay, fadeOut
        );
    }
}
