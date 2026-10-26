package com.muzlik.pvpcombat.events;

import com.muzlik.pvpcombat.combat.AntiInterferenceManager;
import com.muzlik.pvpcombat.combat.CombatManager;
import com.muzlik.pvpcombat.combat.CombatTracker;
import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import com.muzlik.pvpcombat.data.CombatSession;
import com.muzlik.pvpcombat.data.WeaponStats;
import com.muzlik.pvpcombat.data.RestrictionData;
import com.muzlik.pvpcombat.performance.PerformanceMonitor;
import com.muzlik.pvpcombat.logging.CombatLogger;
import com.muzlik.pvpcombat.restrictions.RestrictionManager;
import com.muzlik.pvpcombat.utils.AsyncUtils;
import com.muzlik.pvpcombat.utils.CacheManager;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.ChatColor;
import java.util.Arrays;
import java.util.List;

/**
 * Main event listener handling all combat-related events.
 */
public class CombatEventListener implements Listener {

    private final PvPCombatPlugin plugin;
    private final CombatManager combatManager;
    private final CombatTracker combatTracker;
    private final AntiInterferenceManager antiInterferenceManager;
    private final RestrictionManager restrictionManager;
    private final CombatLogger combatLogger;
    private final PerformanceMonitor performanceMonitor;
    private final CacheManager cacheManager;
    private final com.muzlik.pvpcombat.protection.NewbieProtection newbieProtection;

    public CombatEventListener(PvPCombatPlugin plugin, CombatManager combatManager,
                                CombatTracker combatTracker, AntiInterferenceManager antiInterferenceManager,
                                RestrictionManager restrictionManager, CombatLogger combatLogger,
                                PerformanceMonitor performanceMonitor, CacheManager cacheManager,
                                com.muzlik.pvpcombat.protection.NewbieProtection newbieProtection) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.combatTracker = combatTracker;
        this.antiInterferenceManager = antiInterferenceManager;
        this.restrictionManager = restrictionManager;
        this.combatLogger = combatLogger;
        this.performanceMonitor = performanceMonitor;
        this.cacheManager = cacheManager;
        this.newbieProtection = newbieProtection;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        performanceMonitor.startOperation("entity-damage-event");

        try {
            // Only handle player vs player damage (direct hit; projectiles logged separately)
            if (!(event.getEntity() instanceof Player)) {
                return;
            }
            if (!(event.getDamager() instanceof Player)) {
                // #region agent log
                if (event.getDamager() != null) {
                    com.muzlik.pvpcombat.debug.AgentDebugLog.log("pre", "H3", "CombatEventListener.java:onEntityDamage",
                            "skipped_non_player_damager", java.util.Map.of(
                                    "damagerType", event.getDamager().getClass().getSimpleName(),
                                    "victim", ((Player) event.getEntity()).getName()));
                }
                // #endregion
                return;
            }

            Player attacker = (Player) event.getDamager();
            Player defender = (Player) event.getEntity();
            
            // FIX: Prevent self-combat (player hitting themselves)
            if (attacker.equals(defender)) {
                event.setCancelled(true);
                return;
            }
            
            // Check newbie protection FIRST (before any other checks, even before cancelled check)
            if (newbieProtection.isEnabled()) {
                // Check if attacker is a newbie trying to deal damage
                if (newbieProtection.isNewbie(attacker)) {
                    if (!newbieProtection.canNewbieDealDamage(attacker)) {
                        event.setCancelled(true);
                        attacker.sendMessage(newbieProtection.getNewbieAttackMessage());
                        
                        // Only log if console logging is enabled
                        if (plugin.getConfig().getBoolean("logging.console-enabled", false)) {
                            plugin.getLoggingManager().log("[NEWBIE PROTECTION] Blocked " + attacker.getName() + " (newbie) from attacking " + defender.getName());
                        }
                        return;
                    }
                }
                
                // Check if defender is a newbie who can't receive damage
                if (newbieProtection.isNewbie(defender)) {
                    if (!newbieProtection.canNewbieReceiveDamage(defender)) {
                        event.setCancelled(true);
                        attacker.sendMessage(newbieProtection.getAttackingNewbieMessage());
                        
                        // Only log if console logging is enabled
                        if (plugin.getConfig().getBoolean("logging.console-enabled", false)) {
                            plugin.getLoggingManager().log("[NEWBIE PROTECTION] Blocked " + attacker.getName() + " from attacking " + defender.getName() + " (newbie)");
                        }
                        return;
                    }
                }
            }
            
            // Check if damage is actually being dealt (not cancelled or 0 damage)
            if (event.isCancelled() || event.getFinalDamage() <= 0) {
                return;
            }
            
            // Check if attacker is in a safe zone - prevent hitting from safezone
            if (isInSafeZone(attacker)) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "You cannot attack players from a safe zone!");
                return;
            }
            
            // Check if defender is in a safe zone - prevent hitting players in safezone
            if (isInSafeZone(defender)) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "You cannot attack players in a safe zone!");
                return;
            }

            // Check cache for interference data first
            String interferenceKey = attacker.getUniqueId() + ":" + defender.getUniqueId();
            Boolean cachedInterference = (Boolean) cacheManager.get("restriction-data", interferenceKey);

            boolean hasInterference = cachedInterference != null ? cachedInterference :
                antiInterferenceManager.checkInterference(attacker, defender);

            // Cache the result
            if (cachedInterference == null) {
                cacheManager.put("restriction-data", interferenceKey, hasInterference);
            }

            // Check for interference first
            if (hasInterference) {
                // Handle interference synchronously to avoid async event errors
                antiInterferenceManager.handleInterference(attacker, defender);

                // Cancel the event if blocking is enabled
                if (antiInterferenceManager.shouldBlockInterference()) {
                    event.setCancelled(true);
                }
                return;
            }

            // 1. Start or reset combat state FIRST
            // #region agent log
            boolean neitherInCombat = !combatManager.isInCombat(attacker) && !combatManager.isInCombat(defender);
            com.muzlik.pvpcombat.debug.AgentDebugLog.log("pre", "H4", "CombatEventListener.java:onEntityDamage",
                    "combat_damage_branch", java.util.Map.of(
                            "neitherInCombat", neitherInCombat,
                            "attacker", attacker.getName(),
                            "defender", defender.getName()));
            // #endregion
            if (!combatManager.isInCombat(attacker) && !combatManager.isInCombat(defender)) {
                // Block creative and spectator mode players from entering combat
                if (attacker.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || attacker.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    event.setCancelled(true);
                    attacker.sendMessage(ChatColor.RED + "You cannot enter combat while in Creative or Spectator mode!");
                    return;
                }
                if (defender.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || defender.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    event.setCancelled(true);
                    attacker.sendMessage(ChatColor.RED + "That player is in Creative or Spectator mode and cannot be attacked!");
                    return;
                }

                // Start new combat synchronously
                combatManager.startCombat(attacker, defender);
            } else {
                // If either player is already in combat but is now in creative/spectator, end their combat
                if ((combatManager.isInCombat(attacker) && (attacker.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || attacker.getGameMode() == org.bukkit.GameMode.SPECTATOR))
                        || (combatManager.isInCombat(defender) && (defender.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || defender.getGameMode() == org.bukkit.GameMode.SPECTATOR))) {
                    event.setCancelled(true);
                    attacker.sendMessage(ChatColor.RED + "Combat is not allowed in Creative or Spectator mode!");
                    return;
                }

                // Reset timer for existing combat synchronously
                CombatSession timerSession = combatManager.getActiveSessions().values().stream()
                    .filter(s -> s.involvesPlayer(attacker))
                    .findFirst().orElse(null);
                if (timerSession != null) {
                    // Reset timer to default duration
                    int defaultDuration = plugin.getConfig().getInt("combat.duration", 30);
                    timerSession.getTimerData().setRemainingSeconds(defaultDuration);
                }
            }

            // 2. Record damage synchronously to ensure it's tracked
            // Use the CombatManager's tracker, not the local one!
            double damage = event.getFinalDamage();
            combatManager.getCombatTracker().recordDamageDealt(attacker, damage);
            combatManager.getCombatTracker().recordDamageReceived(defender, damage);
            
            // 3. Also record damage in the session for per-combat tracking
            // Now the session is guaranteed to exist
            CombatSession damageSession = combatManager.getActiveSessions().values().stream()
                .filter(s -> s.involvesPlayer(attacker))
                .findFirst().orElse(null);
            if (damageSession != null) {
                // Check for critical hit (simple version: falling and not on ladder/vines/water)
                boolean isCritical = attacker.getFallDistance() > 0.0F &&
                                     !attacker.isOnGround() &&
                                     !attacker.isInsideVehicle() &&
                                     !attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS) &&
                                     attacker.getLocation().getBlock().getType() != Material.LADDER &&
                                     attacker.getLocation().getBlock().getType() != Material.VINE;

                damageSession.recordDamage(attacker, damage, attacker.getInventory().getItemInMainHand().getType(), isCritical);
            }
            
            // Debug logging - only if console logging is enabled
            if (plugin.getConfig().getBoolean("logging.console-enabled", false)) {
                plugin.getLoggingManager().log(String.format("[DAMAGE] %s dealt %.1f to %s (Total: %.1f)", 
                    attacker.getName(), damage, defender.getName(),
                    combatManager.getCombatTracker().getPlayerData(attacker.getUniqueId()).getTotalDamageDealt()));
            }

            // 4. Log damage event asynchronously
            if (combatManager.isInCombat(attacker)) {
                Player opponent = combatManager.getOpponent(attacker);
                if (opponent != null && opponent.equals(defender)) {
                    AsyncUtils.runAsync(plugin, () -> {
                        CombatSession logSession = combatManager.getActiveSessions().values().stream()
                            .filter(s -> s.involvesPlayer(attacker))
                            .findFirst().orElse(null);
                        if (logSession != null) {
                            String weaponType = attacker.getInventory().getItemInMainHand().getType().toString();
                            double distance = attacker.getLocation().distance(defender.getLocation());
                            combatLogger.logDamageDealt(logSession.getSessionId(), attacker, defender, damage,
                                                       true, distance, weaponType);
                        }
                    }, "combat-processing");
                }
            }
        } finally {
            performanceMonitor.endOperation("entity-damage-event");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        performanceMonitor.startOperation("player-death-event");

        try {
            Player deceased = event.getEntity();

            if (combatManager.isInCombat(deceased)) {
                // Record stats synchronously to ensure they're saved
                Player opponent = combatManager.getOpponent(deceased);
                if (opponent != null) {
                    // Use the CombatManager's tracker, not the local one!
                    combatManager.getCombatTracker().recordWin(opponent);
                    combatManager.getCombatTracker().recordLoss(deceased);

                    // Record weapon kill
                    CombatSession session = combatManager.getActiveSessions().get(deceased.getUniqueId());
                    if (session != null) {
                        String weapon = opponent.getInventory().getItemInMainHand().getType().name();
                        session.getWeaponStats(opponent).computeIfAbsent(weapon, WeaponStats::new).recordKill();
                    }
                    
                    plugin.getLoggingManager().log("Combat ended - Winner: " + opponent.getName() + ", Loser: " + deceased.getName());
                }

                // End combat - keep on main thread for thread safety
                AsyncUtils.runSync(plugin, () -> combatManager.endCombat(deceased.getUniqueId()));
            }
        } finally {
            performanceMonitor.endOperation("player-death-event");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        performanceMonitor.startOperation("player-quit-event");

        try {
            Player player = event.getPlayer();

            if (!combatManager.isInCombat(player)) return;

            Player opponent = combatManager.getOpponent(player);

            // ── Determine disconnect type ─────────────────────────────────
            // getQuitMessage() returns the chat leave message, e.g. "Steve left the game".
            // We inspect it for network-drop keywords; anything else is intentional.
            com.muzlik.pvpcombat.combat.DisconnectTracker.DisconnectType disconnectType;
            String quitMsg = event.getQuitMessage() != null ? event.getQuitMessage().toLowerCase() : "";
            if (quitMsg.contains("timed out") || quitMsg.contains("lost connection")
                    || quitMsg.contains("disconnect.timeout") || quitMsg.contains("disconnect.genericReason")) {
                disconnectType = com.muzlik.pvpcombat.combat.DisconnectTracker.DisconnectType.BAD_INTERNET;
            } else {
                // "left the game" = intentional disconnect
                disconnectType = com.muzlik.pvpcombat.combat.DisconnectTracker.DisconnectType.INTENTIONAL;
            }

            // ── Check master switch ───────────────────────────────────────
            boolean disconnectProtection = plugin.getConfig().getBoolean(
                "combat.disconnect-protection.enabled", true);

            if (disconnectProtection) {
                CombatSession session = combatManager.getActiveSessions().get(player.getUniqueId());
                int remainingTime = session != null ? session.getRemainingTime()
                    : plugin.getConfig().getInt("combat.duration", 10);

                plugin.getLoggingManager().log(String.format(
                    "%s disconnected (%s) during combat with %s. Remaining: %ds.",
                    player.getName(), disconnectType.name(),
                    opponent != null ? opponent.getName() : "none", remainingTime));

                // Hand off to DisconnectTracker BEFORE ending the session so the
                // opponent reference and session data are still valid.
                combatManager.getDisconnectTracker().onPlayerDisconnect(
                    player, opponent, remainingTime, disconnectType);

                // Silently remove only the quitting player from the session map.
                // Called directly (we're already on the main thread in a Bukkit event).
                // The opponent's session entry, boss bar, and action bar stay intact.
                combatManager.silentlyRemovePlayer(player.getUniqueId());

            } else {
                // Protection disabled – instant punishment
                combatManager.getCombatTracker().recordLoss(player);

                if (opponent != null) {
                    combatManager.getCombatTracker().recordWin(opponent);
                    plugin.getLoggingManager().log(player.getName() + " forfeited. " + opponent.getName() + " wins!");
                    opponent.sendMessage(ChatColor.GREEN + "You won! " + ChatColor.YELLOW
                        + player.getName() + " forfeited by logging out.");
                }

                plugin.getServer().broadcastMessage(ChatColor.RED + player.getName()
                    + ChatColor.YELLOW + " forfeited by logging out during combat!");
                plugin.getLoggingManager().log(player.getName() + " was killed for combat logging.");

                AsyncUtils.runSync(plugin, () -> combatManager.endCombat(player.getUniqueId()));
            }
        } finally {
            performanceMonitor.endOperation("player-quit-event");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();

        // Check if projectile is an ender pearl
        if (!(projectile instanceof org.bukkit.entity.EnderPearl)) {
            return;
        }

        // Get the shooter (player)
        if (!(projectile.getShooter() instanceof Player)) {
            return;
        }

        Player player = (Player) projectile.getShooter();

        // Check if player is in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }

        // Check if player can use ender pearl
        if (!restrictionManager.canUseEnderPearl(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot use Ender Pearls during combat!");
            plugin.getLoggingManager().log("[ENDERPEARL] Blocked " + player.getName() + " from using ender pearl in combat");
            return;
        }

        // Apply ender pearl restrictions
        RestrictionData restrictionData = restrictionManager.getOrCreateRestrictionData(player);
        
        // Schedule cooldown application for next tick to override Minecraft's default cooldown
        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                restrictionManager.getEnderPearlRestriction().onEnderPearlUsed(player, restrictionData);
            }
        };
        task.runTaskLater(plugin, 1L);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnderPearlTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        // Only check ender pearl teleports
        if (event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check if player is in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }
        
        // Check if teleporting into safezone
        if (isInSafeZone(event.getTo())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot use Ender Pearls to enter safe zones during combat!");
            plugin.getLoggingManager().log("[ENDERPEARL] Blocked " + player.getName() + " from teleporting into safezone");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        // Check if it's a player
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // Only check when player is starting to glide (not stopping)
        if (!event.isGliding()) {
            return;
        }
        
        // Check if player is in combat and elytra is restricted
        if (combatManager.isInCombat(player)) {
            boolean elytraEnabled = plugin.getConfig().getBoolean("restrictions.elytra.enabled", true);
            boolean blockGlide = plugin.getConfig().getBoolean("restrictions.elytra.block-glide", true);
            
            if (elytraEnabled && blockGlide) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot use Elytra during combat!");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Additional check - if somehow they're gliding during combat, stop them
        if (player.isGliding() && combatManager.isInCombat(player)) {
            boolean elytraEnabled = plugin.getConfig().getBoolean("restrictions.elytra.enabled", true);
            boolean blockGlide = plugin.getConfig().getBoolean("restrictions.elytra.block-glide", true);
            
            if (elytraEnabled && blockGlide) {
                player.setGliding(false);
                player.sendMessage(ChatColor.RED + "Elytra gliding is restricted during combat!");
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Handle timed protection for new players
        newbieProtection.onPlayerJoin(player);
        
        // Check for pending punishment first
        if (combatManager.getDisconnectTracker().hasPendingPunishment(player.getUniqueId())) {
            combatManager.getDisconnectTracker().applyPendingPunishment(player);
            return;
        }
        
        // Check if player was tracked as disconnected during combat
        boolean wasTracked = combatManager.getDisconnectTracker().onPlayerReconnect(player);
        
        if (!wasTracked) {
            // Resume combat state from cache if exists
            Object combatState = cacheManager.get("combat-state", player.getUniqueId().toString());
            // #region agent log
            com.muzlik.pvpcombat.debug.AgentDebugLog.log("pre", "H6", "CombatEventListener.java:onPlayerJoin",
                    "join_resume_cache", java.util.Map.of(
                            "wasTracked", wasTracked,
                            "combatStatePresent", combatState != null));
            // #endregion
            if (combatState != null) {
                player.sendMessage(ChatColor.GREEN + "Combat session resumed from previous session!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material item = event.getItem().getType();

        // Get restriction data once
        RestrictionData restrictionData = restrictionManager.getOrCreateRestrictionData(player);

        // Check for golden apple consumption
        if (item == Material.GOLDEN_APPLE) {
            // Check if player is on cooldown
            if (restrictionData.isOnCooldown("golden_apple")) {
                event.setCancelled(true);
                
                // Get remaining cooldown
                int remainingSeconds = 0;
                java.time.LocalDateTime expiry = restrictionData.getActiveCooldowns().get("golden_apple");
                if (expiry != null) {
                    remainingSeconds = (int) java.time.Duration.between(java.time.LocalDateTime.now(), expiry).getSeconds();
                }
                
                player.sendMessage(ChatColor.RED + "You cannot use Golden Apples yet! Cooldown: " + remainingSeconds + "s");
                return;
            }

            // Check if usage is blocked during combat
            if (!restrictionManager.canUseGoldenApple(player)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot use Golden Apples during combat!");
                return;
            }

            // Apply golden apple cooldown immediately (not on next tick)
            restrictionManager.getGoldenAppleRestriction().onGoldenAppleUsed(player, restrictionData);
        }
        // Check for enchanted golden apple consumption
        else if (item == Material.ENCHANTED_GOLDEN_APPLE) {
            // Check if player is on cooldown
            if (restrictionData.isOnCooldown("enchanted_golden_apple")) {
                event.setCancelled(true);
                
                // Get remaining cooldown
                int remainingSeconds = 0;
                java.time.LocalDateTime expiry = restrictionData.getActiveCooldowns().get("enchanted_golden_apple");
                if (expiry != null) {
                    remainingSeconds = (int) java.time.Duration.between(java.time.LocalDateTime.now(), expiry).getSeconds();
                }
                
                player.sendMessage(ChatColor.RED + "You cannot use Enchanted Golden Apples yet! Cooldown: " + remainingSeconds + "s");
                return;
            }

            // Check if usage is blocked during combat
            if (!restrictionManager.canUseEnchantedGoldenApple(player)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot use Enchanted Golden Apples during combat!");
                return;
            }

            // Apply enchanted golden apple cooldown immediately (not on next tick)
            restrictionManager.getGoldenAppleRestriction().onEnchantedGoldenAppleUsed(player, restrictionData);
        }
    }

    /**
     * Checks if a player has elytra equipped.
     */
    private boolean hasElytraEquipped(Player player) {
        return player.getInventory().getChestplate() != null &&
               player.getInventory().getChestplate().getType() == Material.ELYTRA;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        // Only process if restrictions are explicitly enabled
        if (!plugin.getConfig().getBoolean("restrictions.blocks.enabled", false)) {
            return;
        }

        Player player = event.getPlayer();

        // Check if combat manager is initialized
        if (combatManager == null || restrictionManager == null) {
            return;
        }

        // Only check restrictions if player is in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }

        // Check if block breaking is allowed
        if (!restrictionManager.canBreakBlocks(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot break blocks while in combat!");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        // Only process if restrictions are explicitly enabled
        if (!plugin.getConfig().getBoolean("restrictions.blocks.enabled", false)) {
            return;
        }

        Player player = event.getPlayer();

        // Check if combat manager is initialized
        if (combatManager == null || restrictionManager == null) {
            return;
        }

        // Only check restrictions if player is in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }

        // Check if block placing is allowed
        if (!restrictionManager.canPlaceBlocks(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot place blocks while in combat!");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Check if player is in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }

        // Check if teleport blocking is enabled
        boolean teleportBlocking = plugin.getConfig().getBoolean("restrictions.teleport.enabled", true);
        if (!teleportBlocking) {
            return;
        }

        // Get the command (without the leading slash)
        String command = event.getMessage().toLowerCase().substring(1);
        String[] args = command.split(" ");
        String baseCommand = args[0];

        // Get blocked commands from config
        List<String> blockedCommands = plugin.getConfig().getStringList("restrictions.teleport.blocked-commands");
        
        // Default blocked commands if not in config
        if (blockedCommands.isEmpty()) {
            blockedCommands = Arrays.asList("tp", "teleport", "home", "spawn", "warp", "warps", "tpa", "tpaccept", "back", "wild", "rtp");
        }

        // Check if command is blocked
        for (String blockedCmd : blockedCommands) {
            if (baseCommand.equalsIgnoreCase(blockedCmd) || baseCommand.startsWith(blockedCmd + ":")) {
                event.setCancelled(true);
                String message = plugin.getConfig().getString("restrictions.teleport.blocked-message", 
                    "&cYou cannot use teleport commands during combat!");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                
                // Only log if console logging is enabled
                if (plugin.getConfig().getBoolean("logging.console-enabled", false)) {
                    plugin.getLoggingManager().log("[COMMAND BLOCK] Blocked " + player.getName() + " from using /" + baseCommand + " in combat");
                }
                return;
            }
        }
    }
    
    /**
     * Checks if a player is currently in a safe zone.
     */
    private boolean isInSafeZone(Player player) {
        return isInSafeZone(player.getLocation());
    }
    
    /**
     * Checks if a location is in a safe zone.
     */
    private boolean isInSafeZone(org.bukkit.Location location) {
        // Check if safezone protection is enabled
        if (!plugin.getConfig().getBoolean("restrictions.safezone.enabled", true)) {
            return false;
        }
        
        // Check if WorldGuard is available
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return false;
        }
        
        try {
            // Get protected regions list
            List<String> protectedRegions = plugin.getConfig().getStringList("restrictions.safezone.protected-regions");
            if (protectedRegions.isEmpty()) {
                return false;
            }
            
            // Use reflection to check WorldGuard regions
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            
            // Get BukkitAdapter
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adaptedWorld = adapterClass.getMethod("adapt", org.bukkit.World.class).invoke(null, location.getWorld());
            
            // Get RegionManager
            Object regionManager = regionContainer.getClass().getMethod("get", 
                Class.forName("com.sk89q.worldedit.world.World")).invoke(regionContainer, adaptedWorld);
            
            if (regionManager != null) {
                // Check each protected region
                for (String regionName : protectedRegions) {
                    Object region = regionManager.getClass().getMethod("getRegion", String.class)
                        .invoke(regionManager, regionName);
                    
                    if (region != null) {
                        // Check if location is in region
                        Boolean contains = (Boolean) region.getClass().getMethod("contains", int.class, int.class, int.class)
                            .invoke(region, location.getBlockX(), location.getBlockY(), location.getBlockZ());
                        
                        if (contains != null && contains) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // WorldGuard not available or error checking - assume not in safezone
            plugin.getLogger().fine("Could not check safezone status: " + e.getMessage());
        }
        
        return false;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTridentThrow(ProjectileLaunchEvent event) {
        // Check if projectile is a trident
        if (!(event.getEntity() instanceof org.bukkit.entity.Trident)) {
            return;
        }

        // Get the shooter (player)
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity().getShooter();

        // Only check if player is in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }

        // Check if trident restrictions are enabled
        boolean tridentEnabled = plugin.getConfig().getBoolean("restrictions.trident.enabled", true);
        if (!tridentEnabled) {
            return;
        }

        // ALWAYS block tridents in combat (both throwing and riptide)
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You cannot use tridents during combat!");
        plugin.getLoggingManager().log("[TRIDENT] Blocked " + player.getName() + " from using trident in combat");
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrystalPlace(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is placing an end crystal
        if (event.getItem() == null || event.getItem().getType() != Material.END_CRYSTAL) {
            return;
        }
        
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Check if player can place crystal
        if (!restrictionManager.getCrystalRestriction().canPlaceCrystal(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot place End Crystals during combat!");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrystalBreak(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        // Check if entity is an end crystal
        if (!(event.getEntity() instanceof org.bukkit.entity.EnderCrystal)) {
            return;
        }
        
        // Check if damager is a player
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();

        // Check if player can break crystal
        if (!restrictionManager.getCrystalRestriction().canBreakCrystal(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot break End Crystals during combat!");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawnAnchorUse(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Check if player clicked on a respawn anchor
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.RESPAWN_ANCHOR) {
            return;
        }
        
        // Check if player is in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }
        
        // Check if respawn anchor restrictions are enabled
        boolean anchorEnabled = plugin.getConfig().getBoolean("restrictions.respawn-anchor.enabled", true);
        if (!anchorEnabled) {
            return;
        }
        
        // Block respawn anchor usage in combat
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You cannot use Respawn Anchors during combat!");
        plugin.getLoggingManager().log("[RESPAWN ANCHOR] Blocked " + player.getName() + " from using respawn anchor in combat");
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractTrident(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is using a trident
        if (event.getItem() == null || event.getItem().getType() != Material.TRIDENT) {
            return;
        }
        
        // Check if player is in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }
        
        // Check if trident restrictions are enabled
        boolean tridentEnabled = plugin.getConfig().getBoolean("restrictions.trident.enabled", true);
        if (!tridentEnabled) {
            return;
        }
        
        // Check if trident has riptide enchantment
        if (event.getItem().getEnchantments().containsKey(org.bukkit.enchantments.Enchantment.RIPTIDE)) {
            // Block riptide usage in combat
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot use Riptide during combat!");
            plugin.getLoggingManager().log("[TRIDENT RIPTIDE] Blocked " + player.getName() + " from using riptide in combat");
        }
    }
}
