package com.muzlik.pvpcombat.visual;

import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import com.muzlik.pvpcombat.data.VisualPreferences;
import com.muzlik.pvpcombat.utils.CompatibilityAPI;
import com.muzlik.pvpcombat.utils.CompatibilityManager;
import com.muzlik.pvpcombat.utils.VersionCompatibility;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced message formatter with HEX color support, placeholders, and theme-based styling.
 *
 * <p>Delegates color code translation to {@link CompatibilityAPI} so the same
 * formatter works across Minecraft versions (legacy ChatColor on 1.19.x,
 * Adventure Component API on 1.20+).</p>
 */
public class MessageFormatter {

    private final PvPCombatPlugin plugin;
    private final Map<String, MessageStyle> messageStyles;
    private final CompatibilityAPI compatibility;

    // Regex patterns for HEX colors and placeholders
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    public MessageFormatter(PvPCombatPlugin plugin) {
        this.plugin = plugin;
        this.messageStyles = new HashMap<>();
        this.compatibility = CompatibilityManager.create(plugin);
        loadMessageStyles();
    }

    /**
     * Formats a message with placeholders and colors.
     * Color code translation is delegated to the version-appropriate adapter.
     */
    public String formatMessage(String message, Player player, Map<String, Object> placeholders) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Replace placeholders
        message = replacePlaceholders(message, player, placeholders);

        // Apply HEX colors
        message = applyHexColors(message);

        // Convert legacy color codes via compatibility layer
        message = compatibility.translateColor(message);

        return message;
    }

    /**
     * Formats a message using a specific style.
     */
    public String formatMessageWithStyle(String message, String styleName, Player player, Map<String, Object> placeholders) {
        MessageStyle style = messageStyles.get(styleName);
        if (style != null) {
            message = style.applyStyle(message);
        }

        return formatMessage(message, player, placeholders);
    }

    /**
     * Formats a message using player-specific preferences.
     */
    public String formatMessageWithPreferences(String message, VisualPreferences preferences, Player player, Map<String, Object> placeholders) {
        if (preferences != null) {
            return formatMessageWithStyle(message, preferences.getSelectedMessageStyle(), player, placeholders);
        }
        return formatMessage(message, player, placeholders);
    }

    /**
     * Replaces placeholders in a message.
     */
    private String replacePlaceholders(String message, Player player, Map<String, Object> placeholders) {
        if (placeholders == null) {
            placeholders = new HashMap<>();
        }

        // Add default placeholders
        addDefaultPlaceholders(placeholders, player);

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = placeholders.get(placeholder);

            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value.toString()));
            } else {
                // Keep unknown placeholders
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Adds default placeholders for combat messages.
     */
    private void addDefaultPlaceholders(Map<String, Object> placeholders, Player player) {
        if (player == null) {
            // Set default values when player is null
            placeholders.putIfAbsent("player", "Unknown");
            placeholders.putIfAbsent("display_name", "Unknown");
            placeholders.putIfAbsent("world", "Unknown");
            placeholders.putIfAbsent("time_left", "30");
            placeholders.putIfAbsent("opponent", "Unknown");
            placeholders.putIfAbsent("health", "0.0");
            placeholders.putIfAbsent("max_health", "20.0");
            return;
        }
        
        placeholders.putIfAbsent("player", player.getName());
        placeholders.putIfAbsent("display_name", player.getDisplayName());
        placeholders.putIfAbsent("world", player.getWorld().getName());
        placeholders.putIfAbsent("time_left", "30"); // Default, should be overridden
        placeholders.putIfAbsent("opponent", "Unknown");
        placeholders.putIfAbsent("health", String.format("%.1f", player.getHealth()));
        double maxHealth = ((LivingEntity) player).getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        placeholders.putIfAbsent("max_health", String.valueOf(maxHealth));
    }

    /**
     * Applies HEX color codes to messages.
     * HEX colors are supported on Minecraft 1.16+.
     */
    private String applyHexColors(String message) {
        if (!VersionCompatibility.isAtLeast(1, 16, 0)) {
            // HEX colors not supported in older versions
            return message;
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String hexColor = matcher.group(1);
            StringBuilder hexStr = new StringBuilder("&x");
            for (char c : hexColor.toCharArray()) {
                hexStr.append('&').append(c);
            }
            matcher.appendReplacement(sb, hexStr.toString());
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Loads message styles from configuration.
     */
    private void loadMessageStyles() {
        messageStyles.clear();

        // Load from config if available
        // For now, create built-in styles
        createBuiltInMessageStyles();
    }

    /**
     * Creates built-in message styles.
     */
    private void createBuiltInMessageStyles() {
        // Minimal style - Clean and simple
        messageStyles.put("minimal", new MessageStyle("minimal",
            "&7", "&r", false, false));

        // Detailed style - Comprehensive information
        messageStyles.put("detailed", new MessageStyle("detailed",
            "&a[COMBAT] &f", "&r", true, true));

        // Funny style - Playful and entertaining
        messageStyles.put("funny", new MessageStyle("funny",
            "&d⚔ &eLOL! &f", "&r &d⚔", true, true));

        // Medieval style - Fantasy-themed
        messageStyles.put("medieval", new MessageStyle("medieval",
            "&6[&4BATTLE&6] &f", "&r &6[&4ENDED&6]", true, false));

        // Competitive style - Focused on stats
        messageStyles.put("competitive", new MessageStyle("competitive",
            "&c[&4RANKED&c] &f", "&r &c[&4FINISHED&c]", true, true));
    }

    /**
     * MessageStyle class for styling messages.
     */
    public static class MessageStyle {
        private final String name;
        private final String prefix;
        private final String suffix;
        private final boolean showHealth;
        private final boolean showOpponent;

        public MessageStyle(String name, String prefix, String suffix, boolean showHealth, boolean showOpponent) {
            this.name = name;
            this.prefix = prefix;
            this.suffix = suffix;
            this.showHealth = showHealth;
            this.showOpponent = showOpponent;
        }

        public String applyStyle(String message) {
            StringBuilder styled = new StringBuilder();

            if (prefix != null && !prefix.isEmpty()) {
                styled.append(prefix);
            }

            styled.append(message);

            if (suffix != null && !suffix.isEmpty()) {
                styled.append(suffix);
            }

            return styled.toString();
        }

        public String getName() { return name; }
        public String getPrefix() { return prefix; }
        public String getSuffix() { return suffix; }
        public boolean shouldShowHealth() { return showHealth; }
        public boolean shouldShowOpponent() { return showOpponent; }
    }
}