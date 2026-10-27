package com.muzlik.pvpcombat.tracking;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VersionCheckerTest {

    @Test
    @Order(1)
    @DisplayName("isNewerVersion should return true when candidate is newer")
    void testIsNewerVersionNewer() {
        VersionChecker checker = new VersionChecker(null);
        assertTrue(checker.isNewerVersion("1.3.1", "1.3.0-alpha"));
        assertTrue(checker.isNewerVersion("1.4.0", "1.3.0-alpha"));
    }

    @Test
    @Order(2)
    @DisplayName("isNewerVersion should return false when candidate is same or older")
    void testIsNewerVersionSameOrOlder() {
        VersionChecker checker = new VersionChecker(null);
        assertFalse(checker.isNewerVersion("1.3.0-alpha", "1.3.0-alpha"));
        assertFalse(checker.isNewerVersion("1.3.0", "1.3.0"));
        assertFalse(checker.isNewerVersion("1.2.0", "1.3.0-alpha"));
        assertFalse(checker.isNewerVersion("1.3.0-alpha", "1.3.1"));
        assertFalse(checker.isNewerVersion("1.3.0-beta", "1.3.0-alpha"));
    }

    @Test
    @Order(3)
    @DisplayName("isNewerVersion should handle 1.20, 1.21, 1.22 versions")
    void testMinecraftVersions() {
        VersionChecker checker = new VersionChecker(null);
        assertTrue(checker.isNewerVersion("1.21.0", "1.20.4"));
        assertTrue(checker.isNewerVersion("1.22.0", "1.21.1"));
        assertFalse(checker.isNewerVersion("1.20.4", "1.21.0"));
        assertFalse(checker.isNewerVersion("1.21.0", "1.22.0"));
    }

    @Test
    @Order(4)
    @DisplayName("isNewerVersion should handle future 26.xx versions")
    void testFutureVersions() {
        VersionChecker checker = new VersionChecker(null);
        assertTrue(checker.isNewerVersion("26.0.0", "1.3.0-alpha"));
        assertTrue(checker.isNewerVersion("26.0.0", "1.22.0"));
        assertFalse(checker.isNewerVersion("1.22.0", "26.0.0"));
    }

    @Test
    @Order(5)
    @DisplayName("parseVersionPart should strip non-numeric suffixes")
    void testParseVersionPart() {
        VersionChecker checker = new VersionChecker(null);
        assertEquals(1, checker.parseVersionPart("1"));
        assertEquals(3, checker.parseVersionPart("3"));
        assertEquals(0, checker.parseVersionPart("alpha"));
        assertEquals(0, checker.parseVersionPart("beta"));
        assertEquals(1, checker.parseVersionPart("1-beta"));
        assertEquals(21, checker.parseVersionPart("21"));
    }

    @Test
    @Order(6)
    @DisplayName("CURRENT_VERSION should be 1.3.0-alpha")
    void testCurrentVersion() {
        VersionChecker checker = new VersionChecker(null);
        assertEquals("1.3.0-alpha", checker.getCurrentVersion());
    }
}
