package com.muzlik.pvpcombat.utils;

import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * Compatibility adapter for Minecraft 1.20+.
 *
 * <p>Uses Adventure's {@link Component} for text formatting instead of the
 * legacy {@link org.bukkit.ChatColor}. Action bar uses
 * {@link ChatMessageType#ACTION_BAR} via Spigot.</p>
 */
public class ModernCompatibilityAdapter extends CompatibilityAdapter {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.legacySection();

    public ModernCompatibilityAdapter(PvPCombatPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getTier() {
        return "MODERN (1.20+)";
    }

    @Override
    public boolean usesComponentAPI() {
        return true;
    }

    @Override
    public boolean usesRefinedActionBar() {
        return VersionCompatibility.isAtLeast(1, 22, 0);
    }

    @Override
    public boolean isMajorRework() {
        return VersionCompatibility.isAtLeast(26, 0, 0);
    }

    @Override
    public String translateColor(String message) {
        if (message == null) return "";
        // LegacyComponentSerializer uses '§' as the color code character,
        // but the plugin uses '&'. Convert '&' to '§' before deserializing.
        String legacySection = message.replace('&', '§');
        Component component = SERIALIZER.deserialize(legacySection);
        return SERIALIZER.serialize(component);
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
