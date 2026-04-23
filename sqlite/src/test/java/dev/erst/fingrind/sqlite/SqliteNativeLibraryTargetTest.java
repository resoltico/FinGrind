package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
@NullUnmarked
class SqliteNativeLibraryTargetTest extends SqliteNativeBridgeTestSupport {

  @Test
  void configuredLibraryTarget_requiresManagedLibraryPath() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimePolicy.configuredLibraryTarget(null));

    assertTrue(exception.getMessage().contains("bundle launcher"));
    assertTrue(exception.getMessage().contains("FINGRIND_SQLITE_LIBRARY"));
  }

  @Test
  void configuredLibraryTarget_requiresManagedPathAndNormalizesIt() {
    SqliteLibraryTarget libraryTarget =
        SqliteNativeRuntimePolicy.configuredLibraryTarget("./build/../sqlite/libsqlite3.so.0");

    assertEquals("managed-only", libraryTarget.mode());
    assertTrue(
        Path.of(libraryTarget.lookupTarget()).endsWith(Path.of("sqlite", "libsqlite3.so.0")));
    assertEquals("managed-only", SqliteRuntime.LIBRARY_MODE);
    assertTrue(libraryTarget.toString().contains("managed-only"));
    assertEquals(
        libraryTarget,
        SqliteNativeRuntimePolicy.configuredLibraryTarget("./build/../sqlite/libsqlite3.so.0"));
    assertEquals(
        libraryTarget.hashCode(),
        SqliteNativeRuntimePolicy.configuredLibraryTarget("./build/../sqlite/libsqlite3.so.0")
            .hashCode());
    assertThrows(
        IllegalStateException.class,
        () -> SqliteNativeRuntimePolicy.configuredLibraryTarget("   "));
    assertThrows(IllegalArgumentException.class, () -> new SqliteLibraryTarget(" ", "x"));
  }

  @Test
  void configuredLibraryTarget_prefersExplicitEnvironmentLibraryOverBundleHome() {
    SqliteLibraryTarget libraryTarget =
        SqliteNativeRuntimePolicy.configuredLibraryTarget(
            "./build/../sqlite/libsqlite3.so.0", tempDirectory.toString());

    assertEquals("managed-only", libraryTarget.mode());
    assertTrue(
        Path.of(libraryTarget.lookupTarget()).endsWith(Path.of("sqlite", "libsqlite3.so.0")));
  }

  @Test
  void configuredLibraryTarget_resolvesBundledLibraryWhenBundleHomeIsPresent() throws IOException {
    Path bundleHomePath = tempDirectory.resolve("fingrind-0.14.0-test");
    Path bundledLibraryPath =
        bundleHomePath.resolve("lib").resolve("native").resolve(expectedNativeLibraryFileName());
    Files.createDirectories(bundledLibraryPath.getParent());
    Files.writeString(bundledLibraryPath, "sqlite3mc", StandardCharsets.UTF_8);

    SqliteLibraryTarget libraryTarget =
        SqliteNativeRuntimePolicy.configuredLibraryTarget(null, bundleHomePath.toString());

    assertEquals("managed-only", libraryTarget.mode());
    assertEquals(
        bundledLibraryPath.toAbsolutePath().normalize().toString(), libraryTarget.lookupTarget());
  }

  @Test
  void configuredLibraryTarget_rejectsIncompleteBundleHome() throws IOException {
    Path bundleHomePath = tempDirectory.resolve("fingrind-0.14.0-test");
    Files.createDirectories(bundleHomePath);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimePolicy.configuredLibraryTarget(null, bundleHomePath.toString()));

    assertTrue(exception.getMessage().contains("bundle home"));
    assertTrue(exception.getMessage().contains("FINGRIND_SQLITE_LIBRARY"));
  }

  @Test
  void configuredLibraryTarget_rejectsMissingOrBlankInputsAcrossBundleResolutionModes() {
    IllegalStateException missingEverywhere =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimePolicy.configuredLibraryTarget(null, null));
    IllegalStateException blankConfiguredPath =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimePolicy.configuredLibraryTarget("   ", null));
    IllegalStateException blankBundleHome =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimePolicy.configuredLibraryTarget(null, "   "));

    assertTrue(missingEverywhere.getMessage().contains("bundle launcher"));
    assertTrue(blankConfiguredPath.getMessage().contains("FINGRIND_SQLITE_LIBRARY"));
    assertTrue(blankBundleHome.getMessage().contains("bundle launcher"));
  }

  @Test
  void supportedNativeLibraryFileName_supportsMacOsLinuxWindowsAndRejectsUnsupportedHosts() {
    String originalOsName = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Mac OS X");
      assertEquals("libsqlite3.dylib", SqliteNativeRuntimePolicy.supportedNativeLibraryFileName());

      System.setProperty("os.name", "Linux");
      assertEquals("libsqlite3.so.0", SqliteNativeRuntimePolicy.supportedNativeLibraryFileName());

      System.setProperty("os.name", "Windows 11");
      assertEquals("sqlite3.dll", SqliteNativeRuntimePolicy.supportedNativeLibraryFileName());

      System.setProperty("os.name", "FreeBSD");
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              SqliteNativeRuntimePolicy::supportedNativeLibraryFileName);

      assertTrue(exception.getMessage().contains("macOS, Linux, and Windows only"));
      assertTrue(exception.getMessage().contains("FreeBSD"));
    } finally {
      restoreSystemProperty("os.name", originalOsName);
    }
  }
}
