package com.muzlik.pvpcombat.utils;

import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * Compatibility adapter for Minecraft 1.19.x (legacy).
 *
 * <p>Uses the legacy {@link ChatColor} for text formatting. On these older
 * versions, Adventure's Component API is not available.</p>
 */
public class LegacyCompatibilityAdapter extends CompatibilityAdapter {

    public LegacyCompatibilityAdapter(PvPCombatPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getTier() {
        return "LEGACY (1.19.x)";
    }

    @Override
    public boolean usesComponentAPI() {
        return false;
    }

    @Override
    public boolean usesRefinedActionBar() {
        return false;
    }

    @Override
    public boolean isMajorRework() {
        return false;
    }

    @Override
    public String translateColor(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @Override
    public void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        String formatted = translateColor(message);
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(formatted)
        );
    }

    @Override
    public BossBar createBossBar(String title, BarColor color, BarStyle style) {
        return org.bukkit.Bukkit.createBossBar(
                translateColor(title), color, style);
    }
}
