package com.muzlik.pvpcombat.commands;

import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import com.muzlik.pvpcombat.admin.CombatInspector;
import com.muzlik.pvpcombat.admin.DebugManager;
import com.muzlik.pvpcombat.combat.CombatTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles administrative combat commands with enhanced error handling and permission checking.
 *
 * @author PvPCombat Plugin Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final PvPCombatPlugin plugin;
    private final CombatInspector combatInspector;
    private final DebugManager debugManager;

    /**
     * Constructs a new AdminCommand handler.
     *
     * @param plugin The main plugin instance
     * @throws IllegalArgumentException if plugin is null
     */
    public AdminCommand(PvPCombatPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        this.combatInspector = new CombatInspector(plugin);
        this.debugManager = new DebugManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThis command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("pvpcombat.admin")) {
                player.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }

            if (args.length < 1) {
                return false; // Show usage
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "inspect":
                    return handleInspectCommand(player, args);
                case "summary":
                    return handleAdminSummaryCommand(player, args);
                case "reload":
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("§cThis command can only be used by players.");
                        return true;
                    }
                    boolean result = handleReloadCommand((Player) sender);
                    if (result && plugin.getGuiManager() != null) {
                        plugin.getGuiManager().loadConfig();
                    }
                    return result;
                case "version":
                    return handleVersionCommand(player);
                case "stats":
                    if (args.length > 1) {
                        return handleAdminStatsCommand(sender, args);
                    }
                    if (plugin.getGuiManager() != null) {
                        plugin.getGuiManager().openServerStatsGUI(player);
                        player.sendMessage("§aOpened server combat overview.");
                        return true;
                    }
                    return handleStatsCommand(player);
                case "debug":
                    return handleDebugCommand(player, args);
                case "logging":
                    return handleLoggingCommand(player, args);
                case "protection":
                    return handleProtectionCommand(player, args);
                case "clear":
                    return handleClearCommand(player, args);
                default:
                    return false; // Unknown subcommand
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling admin command: " + e.getMessage());
            e.printStackTrace();
            if (sender instanceof Player) {
                sender.sendMessage("§cAn error occurred while processing the command. Check console for details.");
            }
            return true;
        }
    }

    /**
     * Shows real-time combat info for a player with enhanced error handling.
     *
     * @param player The admin player executing the command
     * @param args The command arguments
     * @return true if command executed successfully
     */
    private boolean handleInspectCommand(Player player, String[] args) {
        try {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /combat inspect <player>");
                player.sendMessage("§7Inspects real-time combat status of a player.");
                return true;
            }

            String targetName = args[1];
            if (targetName == null || targetName.trim().isEmpty()) {
                player.sendMessage("§cPlayer name cannot be empty.");
                return true;
            }

            Player target = Bukkit.getPlayer(targetName);

            if (target == null) {
                player.sendMessage("§cPlayer '" + targetName + "' is not online.");
                return true;
            }

            // Check if target is within inspection range (if configured)
            if (plugin.getConfigManager() != null) {
                Object rangeValue = plugin.getConfigManager().getConfigValue("commands.admin.inspection-range");
                int rangeLimit = (rangeValue instanceof Number) ? ((Number) rangeValue).intValue() : 50;
                if (rangeLimit > 0 && !player.getWorld().equals(target.getWorld())) {
                    player.sendMessage("§cTarget player is in a different world.");
                    return true;
                }
                if (rangeLimit > 0 && player.getLocation().distance(target.getLocation()) > rangeLimit) {
                    player.sendMessage("§cTarget player is too far away (max: " + rangeLimit + " blocks).");
                    return true;
                }
            }

            combatInspector.inspectPlayer(player, target);
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error in inspect command: " + e.getMessage());
            player.sendMessage("§cFailed to inspect player. Check console for details.");
            return true;
        }
    }

    /**
     * Handles showing stats for a specific player (admin).
     */
    private boolean handleAdminStatsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /combat stats <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        if (plugin.getGuiManager() != null) {
            plugin.getGuiManager().openMainStatsGUI((Player) sender, target.getUniqueId());
            sender.sendMessage("§aOpening statistics for " + target.getName());
        }

        return true;
    }

    /**
     * Shows last combat stats for a player with enhanced error handling.
     *
     * @param player The admin player executing the command
     * @param args The command arguments
     * @return true if command executed successfully
     */
    private boolean handleAdminSummaryCommand(Player player, String[] args) {
        try {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /combat summary <player>");
                player.sendMessage("§7Shows the last combat summary for a player.");
                return true;
            }

            String targetName = args[1];
            if (targetName == null || targetName.trim().isEmpty()) {
                player.sendMessage("§cPlayer name cannot be empty.");
                return true;
            }

            Player target = Bukkit.getPlayer(targetName);

            if (target == null) {
                player.sendMessage("§cPlayer '" + targetName + "' is not online.");
                return true;
            }

            if (plugin.getGuiManager() != null) {
                plugin.getGuiManager().openMainStatsGUI(player, target.getUniqueId());
                player.sendMessage("§aOpening combat statistics for §e" + target.getName() + "§a.");
                return true;
            }

            combatInspector.showPlayerSummary(player, target);
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error in summary command: " + e.getMessage());
            player.sendMessage("§cFailed to show player summary. Check console for details.");
            return true;
        }
    }

    /**
     * Reloads configuration with proper implementation.
     *
     * @param player The admin player executing the command
     * @return true if command executed successfully
     */
    private boolean handleReloadCommand(Player player) {
        try {
            if (plugin.getConfigManager() == null) {
                player.sendMessage("§cConfiguration manager is not available.");
                return true;
            }

            // Reload configuration using the interface method
            plugin.getConfigManager().reloadConfig();

            player.sendMessage("§aConfiguration reloaded successfully!");
            player.sendMessage("§7All combat systems have been updated with new settings.");

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
            player.sendMessage("§cFailed to reload configuration. Check console for details.");
            return true;
        }
    }

    /**
     * Checks for plugin updates and displays version information.
     *
     * @param player The admin player executing the command
     * @return true if command executed successfully
     */
    private boolean handleVersionCommand(Player player) {
        try {
            var pluginManager = plugin.getPluginManager();
            var versionChecker = pluginManager != null ? pluginManager.getVersionChecker() : null;

            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§e§lTrueCombatManager Version Info");
            player.sendMessage("");
            player.sendMessage("§7Current version: §a" + (versionChecker != null ? versionChecker.getCurrentVersion() : "unknown"));
            if (versionChecker != null && versionChecker.isUpdateAvailable()) {
                player.sendMessage("§7Latest version:  §a" + versionChecker.getLatestVersion() + " §7(UPDATE AVAILABLE!)");
                player.sendMessage("§7Update notes:    §f" + (versionChecker.getUpdateNotes() != null ? versionChecker.getUpdateNotes() : "none"));
                if (versionChecker.getUpdateUrl() != null) {
                    player.sendMessage("§7Download:        §e" + versionChecker.getUpdateUrl());
                }
                player.sendMessage("");
                player.sendMessage("§aRun §e/combat version §aagain to re-check for updates.");
            } else {
                player.sendMessage("§7Latest version:  §a" + (versionChecker != null ? versionChecker.getLatestVersion() : "unknown") + " §7(up to date)");
                if (versionChecker != null && versionChecker.getLastCheckTime() > 0) {
                    long minutesAgo = (System.currentTimeMillis() - versionChecker.getLastCheckTime()) / 60000;
                    player.sendMessage("§7Last checked:    §f" + minutesAgo + " minute(s) ago");
                }
            }
            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error checking version: " + e.getMessage());
            player.sendMessage("§cFailed to check version. Check console for details.");
            return true;
        }
    }

    /**
     * Toggles debug mode with enhanced feedback.
     *
     * @param player The admin player executing the command
     * @param args The command arguments
     * @return true if command executed successfully
     */
    private boolean handleDebugCommand(Player player, String[] args) {
        try {
            boolean enabled = debugManager.toggleDebugMode(player);
            player.sendMessage("§aDebug mode " + (enabled ? "enabled" : "disabled") + "!");

            if (enabled) {
                player.sendMessage("§7Debug information will now be displayed in chat.");
                player.sendMessage("§7Use /combat debug again to disable.");
            } else {
                player.sendMessage("§7Debug mode disabled.");
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error toggling debug mode: " + e.getMessage());
            player.sendMessage("§cFailed to toggle debug mode. Check console for details.");
            return true;
        }
    }

    /**
     * Toggles console logging with enhanced UI/UX.
     *
     * @param player The admin player executing the command
     * @param args The command arguments
     * @return true if command executed successfully
     */
    private boolean handleLoggingCommand(Player player, String[] args) {
        try {
            if (args.length < 2) {
                // Show current status
                boolean currentStatus = plugin.getLoggingManager().isConsoleLoggingEnabled();
                player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("§e§lConsole Logging Status");
                player.sendMessage("");
                player.sendMessage("§7Current Status: " + (currentStatus ? "§a§lENABLED ✓" : "§c§lDISABLED ✗"));
                player.sendMessage("");
                player.sendMessage("§7When enabled, the following will be logged:");
                player.sendMessage("  §8• §7Combat start/end events");
                player.sendMessage("  §8• §7Damage dealt/received");
                player.sendMessage("  §8• §7Newbie protection checks");
                player.sendMessage("  §8• §7Restriction blocks (trident, ender pearl, etc.)");
                player.sendMessage("  §8• §7Command blocks");
                player.sendMessage("");
                player.sendMessage("§7Usage: §e/combat logging <enabled|disabled>");
                player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                return true;
            }

            String action = args[1].toLowerCase();
            boolean newStatus;

            switch (action) {
                case "enabled":
                case "enable":
                case "on":
                case "true":
                    plugin.getLoggingManager().enableConsoleLogging();
                    newStatus = true;
                    break;
                case "disabled":
                case "disable":
                case "off":
                case "false":
                    plugin.getLoggingManager().disableConsoleLogging();
                    newStatus = false;
                    break;
                default:
                    player.sendMessage("§cInvalid option. Use: enabled or disabled");
                    return true;
            }

            // Enhanced feedback
            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§e§lConsole Logging " + (newStatus ? "Enabled" : "Disabled"));
            player.sendMessage("");
            
            if (newStatus) {
                player.sendMessage("§a✓ Console logging has been enabled!");
                player.sendMessage("");
                player.sendMessage("§7The following will now be logged to console:");
                player.sendMessage("  §8• §aCombat events (start/end)");
                player.sendMessage("  §8• §aDamage tracking");
                player.sendMessage("  §8• §aNewbie protection checks");
                player.sendMessage("  §8• §aRestriction blocks");
                player.sendMessage("  §8• §aCommand blocks");
                player.sendMessage("");
                player.sendMessage("§7This is useful for debugging and monitoring.");
            } else {
                player.sendMessage("§c✗ Console logging has been disabled!");
                player.sendMessage("");
                player.sendMessage("§7Combat events will no longer spam the console.");
                player.sendMessage("§7Only errors will be logged.");
                player.sendMessage("");
                player.sendMessage("§7This keeps your console clean and improves performance.");
            }
            
            player.sendMessage("");
            player.sendMessage("§7Use §e/combat logging §7to check status anytime.");
            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error toggling console logging: " + e.getMessage());
            player.sendMessage("§cFailed to toggle console logging. Check console for details.");
            return true;
        }
    }

    /**
     * Handles protection command to give timed protection to players.
     *
     * @param player The admin player executing the command
     * @param args The command arguments
     * @return true if command executed successfully
     */
    private boolean handleProtectionCommand(Player player, String[] args) {
        try {
            if (args.length < 2) {
                player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("§e§lNewbie Protection Command");
                player.sendMessage("");
                player.sendMessage("§7Usage:");
                player.sendMessage("  §e/combat protection <player> <seconds>");
                player.sendMessage("  §e/combat protection all <seconds>");
                player.sendMessage("  §e/combat protection <player> remove");
                player.sendMessage("  §e/combat protection <player> check");
                player.sendMessage("");
                player.sendMessage("§7Examples:");
                player.sendMessage("  §8• §7/combat protection Steve 300 §8- §7Give Steve 5 minutes protection");
                player.sendMessage("  §8• §7/combat protection all 600 §8- §7Give all players 10 minutes protection");
                player.sendMessage("  §8• §7/combat protection Steve remove §8- §7Remove Steve's protection");
                player.sendMessage("  §8• §7/combat protection Steve check §8- §7Check Steve's protection status");
                player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                return true;
            }

            String targetName = args[1];
            
            // Handle "all" keyword
            if (targetName.equalsIgnoreCase("all")) {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /combat protection all <seconds>");
                    return true;
                }
                
                try {
                    int seconds = Integer.parseInt(args[2]);
                    if (seconds <= 0) {
                        player.sendMessage("§cSeconds must be a positive number!");
                        return true;
                    }
                    
                    int count = 0;
                    for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                        plugin.getNewbieProtection().giveTimedProtection(onlinePlayer, seconds);
                        count++;
                    }
                    
                    player.sendMessage("§a✓ Granted " + seconds + "s protection to " + count + " online players!");
                    plugin.getServer().broadcastMessage("§6[Server] §eAll players have been granted §a" + seconds + "s §eof newbie protection!");
                    
                    return true;
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid number: " + args[2]);
                    return true;
                }
            }
            
            // Handle specific player
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                player.sendMessage("§cPlayer '" + targetName + "' is not online.");
                return true;
            }
            
            if (args.length < 3) {
                player.sendMessage("§cUsage: /combat protection <player> <seconds|remove|check>");
                return true;
            }
            
            String action = args[2].toLowerCase();
            
            // Handle "check" action
            if (action.equals("check")) {
                if (plugin.getNewbieProtection().hasTimedProtection(target)) {
                    int remaining = plugin.getNewbieProtection().getRemainingProtectionTime(target);
                    player.sendMessage("§a" + target.getName() + " has §e" + remaining + "s §aof protection remaining.");
                } else {
                    player.sendMessage("§c" + target.getName() + " does not have timed protection.");
                }
                return true;
            }
            
            // Handle "remove" action
            if (action.equals("remove")) {
                plugin.getNewbieProtection().removeTimedProtection(target.getUniqueId());
                player.sendMessage("§a✓ Removed protection from " + target.getName());
                target.sendMessage("§cYour newbie protection has been removed by an administrator.");
                return true;
            }
            
            // Handle giving protection (number of seconds)
            try {
                int seconds = Integer.parseInt(action);
                if (seconds <= 0) {
                    player.sendMessage("§cSeconds must be a positive number!");
                    return true;
                }
                
                plugin.getNewbieProtection().giveTimedProtection(target, seconds);
                player.sendMessage("§a✓ Granted " + seconds + "s protection to " + target.getName());
                
                return true;
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid action or number: " + action);
                player.sendMessage("§7Use: <seconds>, 'remove', or 'check'");
                return true;
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error in protection command: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§cFailed to execute protection command. Check console for details.");
            return true;
        }
    }

    /**
     * Displays server-wide combat statistics.
     *
     * @param player The admin player executing the command
     * @return true if command executed successfully
     */
    private boolean handleStatsCommand(Player player) {
        try {
            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§e§lServer Combat Statistics");
            player.sendMessage("");
            
            // Get combat tracker from CombatManager (cast to concrete class)
            if (!(plugin.getCombatManager() instanceof com.muzlik.pvpcombat.combat.CombatManager)) {
                player.sendMessage("§cCombat manager is not available.");
                player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                return true;
            }
            
            com.muzlik.pvpcombat.combat.CombatManager combatManager = 
                (com.muzlik.pvpcombat.combat.CombatManager) plugin.getCombatManager();
            CombatTracker tracker = combatManager.getCombatTracker();
            
            if (tracker == null) {
                player.sendMessage("§cCombat tracker is not available.");
                player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                return true;
            }
            
            // Calculate server-wide statistics
            Map<UUID, com.muzlik.pvpcombat.data.PlayerCombatData> allData = tracker.getAllPlayerData();
            
            int totalPlayers = allData.size();
            int totalCombats = 0;
            int totalWins = 0;
            int totalLosses = 0;
            double totalDamageDealt = 0;
            double totalDamageReceived = 0;
            long totalCombatTime = 0;
            
            for (com.muzlik.pvpcombat.data.PlayerCombatData data : allData.values()) {
                totalCombats += data.getTotalCombats();
                totalWins += data.getWins();
                totalLosses += data.getLosses();
                totalDamageDealt += data.getTotalDamageDealt();
                totalDamageReceived += data.getTotalDamageReceived();
                totalCombatTime += data.getTotalCombatTime();
            }
            
            // Get active sessions count
            int activeSessions = combatManager.getActiveSessions().size() / 2; // Divide by 2 since each session has 2 players
            
            player.sendMessage("§7Total Tracked Players: §e" + totalPlayers);
            player.sendMessage("§7Active Combat Sessions: §e" + activeSessions);
            player.sendMessage("§7Total Combats: §e" + totalCombats);
            player.sendMessage("§7Total Wins: §a" + totalWins);
            player.sendMessage("§7Total Losses: §c" + totalLosses);
            player.sendMessage("§7Total Damage Dealt: §e" + String.format("%.1f", totalDamageDealt));
            player.sendMessage("§7Total Damage Received: §e" + String.format("%.1f", totalDamageReceived));
            
            // Format combat time
            long hours = totalCombatTime / (1000 * 60 * 60);
            long minutes = (totalCombatTime / (1000 * 60)) % 60;
            long seconds = (totalCombatTime / 1000) % 60;
            player.sendMessage("§7Total Combat Time: §e" + hours + "h " + minutes + "m " + seconds + "s");
            
            // Calculate averages
            if (totalPlayers > 0) {
                player.sendMessage("");
                player.sendMessage("§e§lAverages per Player:");
                player.sendMessage("§7Combats: §e" + String.format("%.1f", (double) totalCombats / totalPlayers));
                player.sendMessage("§7Damage Dealt: §e" + String.format("%.1f", totalDamageDealt / totalPlayers));
                player.sendMessage("§7Win Rate: §e" + String.format("%.1f%%", totalWins > 0 ? (double) totalWins / (totalWins + totalLosses) * 100 : 0));
            }
            
            player.sendMessage("");
            player.sendMessage("§7Use §e/combat inspect <player> §7for individual stats");
            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error in stats command: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§cFailed to display statistics. Check console for details.");
            return true;
        }
    }
    
    /**
     * Force-ends combat for a specific player.
     *
     * @param player The admin player executing the command
     * @param args The command arguments
     * @return true if command executed successfully
     */
    private boolean handleClearCommand(Player player, String[] args) {
        try {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /combat clear <player>");
                player.sendMessage("§7Force-ends combat for the specified player.");
                return true;
            }
            
            String targetName = args[1];
            if (targetName == null || targetName.trim().isEmpty()) {
                player.sendMessage("§cPlayer name cannot be empty.");
                return true;
            }
            
            Player target = Bukkit.getPlayer(targetName);
            
            if (target == null) {
                player.sendMessage("§cPlayer '" + targetName + "' is not online.");
                return true;
            }
            
            // Check if target is in combat
            if (!plugin.getCombatManager().isInCombat(target)) {
                player.sendMessage("§c" + target.getName() + " is not in combat.");
                return true;
            }
            
            // Get opponent before ending combat
            Player opponent = plugin.getCombatManager().getOpponent(target);
            
            // End combat for the target player
            boolean success = plugin.getCombatManager().endCombat(target.getUniqueId());
            
            if (success) {
                player.sendMessage("§a✓ Force-ended combat for " + target.getName());
                target.sendMessage("§cYour combat has been ended by an administrator.");
                
                // Notify opponent if they exist
                if (opponent != null && opponent.isOnline()) {
                    opponent.sendMessage("§cYour opponent's combat was ended by an administrator.");
                }
                
                // Log the action
                plugin.getLogger().info("[ADMIN] " + player.getName() + " force-ended combat for " + target.getName());
            } else {
                player.sendMessage("§cFailed to end combat for " + target.getName());
            }
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error in clear command: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§cFailed to clear combat. Check console for details.");
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                return Collections.emptyList();
            }

            Player player = (Player) sender;
            if (!player.hasPermission("pvpcombat.admin")) {
                return Collections.emptyList();
            }

            List<String> completions = new ArrayList<>();

            if (args.length == 1) {
                String input = args[0].toLowerCase();
                List<String> commands = Arrays.asList("inspect", "summary", "reload", "debug", "logging", "protection", "stats", "clear", "version");
                for (String cmd : commands) {
                    if (cmd.toLowerCase().startsWith(input)) {
                        completions.add(cmd);
                    }
                }
            } else if (args.length == 2 && "logging".equals(args[0].toLowerCase())) {
                String input = args[1].toLowerCase();
                List<String> options = Arrays.asList("enabled", "disabled");
                for (String opt : options) {
                    if (opt.startsWith(input)) {
                        completions.add(opt);
                    }
                }
            } else if (args.length == 2 && "protection".equals(args[0].toLowerCase())) {
                String input = args[1].toLowerCase();
                // Add "all" option
                if ("all".startsWith(input)) {
                    completions.add("all");
                }
                // Add online player names
                plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> p.getName().toLowerCase().startsWith(input))
                    .limit(10)
                    .forEach(p -> completions.add(p.getName()));
            } else if (args.length == 3 && "protection".equals(args[0].toLowerCase())) {
                String input = args[2].toLowerCase();
                if ("all".equalsIgnoreCase(args[1])) {
                    // For "all", only suggest time values
                    List<String> times = Arrays.asList("60", "300", "600", "900", "1800");
                    for (String time : times) {
                        if (time.startsWith(input)) {
                            completions.add(time);
                        }
                    }
                } else {
                    // For specific player, suggest actions and times
                    List<String> actions = Arrays.asList("check", "remove", "60", "300", "600", "900", "1800");
                    for (String action : actions) {
                        if (action.startsWith(input)) {
                            completions.add(action);
                        }
                    }
                }
            } else if (args.length == 2) {
                String subCmd = args[0].toLowerCase();
                if ("inspect".equals(subCmd) || "summary".equals(subCmd) || "clear".equals(subCmd)) {
                    String input = args[1].toLowerCase();
                    plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> p.getName().toLowerCase().startsWith(input))
                        .limit(10)
                        .forEach(p -> completions.add(p.getName()));
                }
            }

            return completions;

        } catch (Exception e) {
            plugin.getLogger().warning("Error in admin tab completion: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}