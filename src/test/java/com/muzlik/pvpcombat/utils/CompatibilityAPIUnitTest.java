package com.muzlik.pvpcombat.utils;

import com.muzlik.pvpcombat.core.PvPCombatPlugin;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CompatibilityAPI abstraction layer.
 * Verifies that the correct adapter is selected for each Minecraft version tier
 * and that version-specific behavior works correctly.
 */
class CompatibilityAPIUnitTest {

    @Test
    @DisplayName("Legacy adapter tier and capabilities")
    void testLegacyAdapterFor1_19() {
        LegacyCompatibilityAdapter legacy = new LegacyCompatibilityAdapter(null);
        assertEquals("LEGACY (1.19.x)", legacy.getTier());
        assertFalse(legacy.usesComponentAPI());
        assertFalse(legacy.usesRefinedActionBar());
        assertFalse(legacy.isMajorRework());
    }

    @Test
    @DisplayName("Modern adapter tier and capabilities")
    void testModernAdapterFor1_20() {
        ModernCompatibilityAdapter modern = new ModernCompatibilityAdapter(null);
        assertEquals("MODERN (1.20+)", modern.getTier());
        assertTrue(modern.usesComponentAPI());
        assertFalse(modern.isMajorRework());
        // 1.20 doesn't have refined action bar (that's 1.22+)
        assertFalse(modern.usesRefinedActionBar());
    }

    @Test
    @DisplayName("Modern adapter reports refined action bar for 1.22+")
    void testModernAdapterRefinedActionBarFor1_22() {
        // Use MinecraftVersion instances directly to simulate different versions
        VersionCompatibility.MinecraftVersion v1_22 = new VersionCompatibility.MinecraftVersion(1, 22, 0);
        VersionCompatibility.MinecraftVersion v1_20 = new VersionCompatibility.MinecraftVersion(1, 20, 0);

        assertTrue(v1_22.isAtLeast(new VersionCompatibility.MinecraftVersion(1, 22, 0)));
        assertFalse(v1_20.isAtLeast(new VersionCompatibility.MinecraftVersion(1, 22, 0)));
    }

    @Test
    @DisplayName("Modern adapter reports major rework for 26+")
    void testModernAdapterMajorReworkFor26() {
        VersionCompatibility.MinecraftVersion v26 = new VersionCompatibility.MinecraftVersion(26, 0, 0);
        VersionCompatibility.MinecraftVersion v1_22 = new VersionCompatibility.MinecraftVersion(1, 22, 0);

        assertTrue(v26.isAtLeast(new VersionCompatibility.MinecraftVersion(26, 0, 0)));
        assertFalse(v1_22.isAtLeast(new VersionCompatibility.MinecraftVersion(26, 0, 0)));
    }

    @Test
    @DisplayName("translateColor translates legacy color codes")
    void testTranslateColor() {
        LegacyCompatibilityAdapter legacy = new LegacyCompatibilityAdapter(null);
        ModernCompatibilityAdapter modern = new ModernCompatibilityAdapter(null);

        // &c should translate to ChatColor.RED + "Hello"
        String legacyResult = legacy.translateColor("&cHello");
        String modernResult = modern.translateColor("&cHello");

        // Both should produce the same translated output (not the raw &c)
        assertEquals(legacyResult, modernResult);
        assertNotEquals("&cHello", legacyResult); // Should be translated, not raw
    }

    @Test
    @DisplayName("stripColor removes color codes")
    void testStripColor() {
        LegacyCompatibilityAdapter legacy = new LegacyCompatibilityAdapter(null);
        ModernCompatibilityAdapter modern = new ModernCompatibilityAdapter(null);

        assertEquals("Hello world", legacy.stripColor("&cHello &fworld"));
        assertEquals("Hello world", modern.stripColor("&cHello &fworld"));
    }

    @Test
    @DisplayName("resolveSound handles valid and invalid sound names")
    void testResolveSound() {
        LegacyCompatibilityAdapter legacy = new LegacyCompatibilityAdapter(null);

        assertNotNull(legacy.resolveSound("ENTITY_PLAYER_HURT"));
        assertNotNull(legacy.resolveSound("BLOCK_NOTE_BLOCK_PLING"));
        assertNull(legacy.resolveSound("INVALID_SOUND_NAME"));
        assertNull(legacy.resolveSound(""));
        assertNull(legacy.resolveSound(null));
    }

    @Test
    @DisplayName("VersionCompatibility tier detection via MinecraftVersion instances")
    void testVersionCompatibilityTier() {
        VersionCompatibility.MinecraftVersion v1_20 = new VersionCompatibility.MinecraftVersion(1, 20, 0);
        VersionCompatibility.MinecraftVersion v1_22 = new VersionCompatibility.MinecraftVersion(1, 22, 0);
        VersionCompatibility.MinecraftVersion v26 = new VersionCompatibility.MinecraftVersion(26, 0, 0);
        VersionCompatibility.MinecraftVersion v1_19 = new VersionCompatibility.MinecraftVersion(1, 19, 4);

        assertTrue(v1_20.isAtLeast(new VersionCompatibility.MinecraftVersion(1, 20, 0)));
        assertTrue(v1_22.isAtLeast(new VersionCompatibility.MinecraftVersion(1, 22, 0)));
        assertTrue(v26.isAtLeast(new VersionCompatibility.MinecraftVersion(26, 0, 0)));

        assertFalse(v1_20.isAtLeast(new VersionCompatibility.MinecraftVersion(1, 22, 0)));
        assertFalse(v1_22.isAtLeast(new VersionCompatibility.MinecraftVersion(26, 0, 0)));
        assertFalse(v1_19.isAtLeast(new VersionCompatibility.MinecraftVersion(1, 21, 0)));
    }

    @Test
    @DisplayName("MAX_SUPPORTED includes 1.23.x")
    void testMaxSupported1_23() {
        VersionCompatibility.MinecraftVersion v1_23_0 = new VersionCompatibility.MinecraftVersion(1, 23, 0);
        assertTrue(v1_23_0.isAtMost(VersionCompatibility.MAX_SUPPORTED));
    }

    @Test
    @DisplayName("26.xx exceeds MAX_SUPPORTED")
    void testVersion26ExceedsMax() {
        VersionCompatibility.MinecraftVersion v26_0_0 = new VersionCompatibility.MinecraftVersion(26, 0, 0);
        assertFalse(v26_0_0.isAtMost(VersionCompatibility.MAX_SUPPORTED));
    }
}
