package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
class SqliteNativeLibraryTargetTest extends SqliteNativeBridgeTestSupport {
  @Test
  void configuredLibraryTarget_requiresManagedLibraryPath() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimePolicy.configuredLibraryTarget(
                    null, null, List::of, () -> null));
    String message = Objects.requireNonNull(exception.getMessage());
    assertTrue(message.contains("bundle launcher"));
    assertThrows(
        IllegalStateException.class,
        () -> SqliteNativeRuntimePolicy.configuredLibraryTarget(null, null, List::of, () -> null));
  }

  @Test
  void configuredLibraryTarget_rejectsRetiredConfiguredLibraryOverride() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimePolicy.configuredLibraryTarget(
                    "./build/../sqlite/libsqlite3.so.0"));
    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("has been removed"));
    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("FINGRIND_SQLITE_LIBRARY"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SqliteLibraryTarget(" ", SqliteRuntimeProvenance.BUNDLE_MANAGED, "x"));
  }

  @Test
  void configuredLibraryTarget_rejectsRetiredConfiguredLibraryOverrideEvenWhenBundleHomeExists() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimePolicy.configuredLibraryTarget(
                    "./build/../sqlite/libsqlite3.so.0", tempDirectory.toString()));
    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("has been removed"));
  }

  @Test
  void configuredLibraryTarget_blankConfiguredPathFallsBackToSupportedResolutionPaths() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimePolicy.configuredLibraryTarget(
                    "   ", null, List::of, () -> null));
    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("bundle launcher"));
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
  void configuredLibraryTarget_resolvesManagedLibraryFromTheSourceCheckout() throws IOException {
    Path sourceCheckoutRoot = tempDirectory.resolve("FinGrind");
    Path managedLibraryPath =
        sourceCheckoutRoot
            .resolve("build")
            .resolve("managed-sqlite")
            .resolve(expectedManagedSqliteClassifier())
            .resolve(expectedNativeLibraryFileName());
    Files.createDirectories(sourceCheckoutRoot.resolve("cli"));
    Files.writeString(sourceCheckoutRoot.resolve("gradlew"), "#!/usr/bin/env bash\n");
    Files.createDirectories(managedLibraryPath.getParent());
    Files.writeString(managedLibraryPath, "sqlite3mc", StandardCharsets.UTF_8);
    SqliteLibraryTarget libraryTarget =
        SqliteNativeRuntimePolicy.configuredLibraryTarget(
            null, null, () -> List.of(sourceCheckoutRoot), () -> null);
    assertEquals("managed-only", libraryTarget.mode());
    assertEquals(SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED, libraryTarget.provenance());
    assertEquals(
        managedLibraryPath.toAbsolutePath().normalize().toString(), libraryTarget.lookupTarget());
  }

  @Test
  void configuredLibraryTarget_resolvesManagedLibraryFromDefaultSourceCheckoutDetection()
      throws IOException {
    Path sourceCheckoutRoot = tempDirectory.resolve("FinGrind");
    Path managedLibraryPath =
        sourceCheckoutRoot
            .resolve("build")
            .resolve("managed-sqlite")
            .resolve(expectedManagedSqliteClassifier())
            .resolve(expectedNativeLibraryFileName());
    Files.createDirectories(sourceCheckoutRoot.resolve("cli"));
    Files.writeString(sourceCheckoutRoot.resolve("gradlew"), "#!/usr/bin/env bash\n");
    Files.createDirectories(managedLibraryPath.getParent());
    Files.writeString(managedLibraryPath, "sqlite3mc", StandardCharsets.UTF_8);
    String originalSourceCheckoutRoot = System.getProperty("fingrind.source-checkout.root");
    String originalSourceCheckoutBuildRoot =
        System.getProperty("fingrind.source-checkout.build-root");
    try {
      System.setProperty(
          "fingrind.source-checkout.root", sourceCheckoutRoot.resolve("gradlew").toString());
      System.clearProperty("fingrind.source-checkout.build-root");
      SqliteLibraryTarget libraryTarget =
          SqliteNativeRuntimePolicy.configuredLibraryTarget(null, null);
      assertEquals(SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED, libraryTarget.provenance());
      assertEquals(
          managedLibraryPath.toAbsolutePath().normalize().toString(), libraryTarget.lookupTarget());
    } finally {
      restoreSystemProperty("fingrind.source-checkout.build-root", originalSourceCheckoutBuildRoot);
      restoreSystemProperty("fingrind.source-checkout.root", originalSourceCheckoutRoot);
    }
  }

  @Test
  void configuredLibraryTarget_singleArgumentOverload_resolvesManagedLibraryFromDefaultDetection()
      throws IOException {
    Path sourceCheckoutRoot = tempDirectory.resolve("FinGrind-single-arg");
    Path managedLibraryPath =
        sourceCheckoutRoot
            .resolve("build")
            .resolve("managed-sqlite")
            .resolve(expectedManagedSqliteClassifier())
            .resolve(expectedNativeLibraryFileName());
    Files.createDirectories(sourceCheckoutRoot.resolve("cli"));
    Files.writeString(sourceCheckoutRoot.resolve("gradlew"), "#!/usr/bin/env bash\n");
    Files.createDirectories(managedLibraryPath.getParent());
    Files.writeString(managedLibraryPath, "sqlite3mc", StandardCharsets.UTF_8);
    String originalSourceCheckoutRoot = System.getProperty("fingrind.source-checkout.root");
    String originalSourceCheckoutBuildRoot =
        System.getProperty("fingrind.source-checkout.build-root");
    try {
      System.setProperty(
          "fingrind.source-checkout.root", sourceCheckoutRoot.resolve("gradlew").toString());
      System.clearProperty("fingrind.source-checkout.build-root");
      SqliteLibraryTarget libraryTarget = SqliteNativeRuntimePolicy.configuredLibraryTarget(null);
      assertEquals(SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED, libraryTarget.provenance());
      assertEquals(
          managedLibraryPath.toAbsolutePath().normalize().toString(), libraryTarget.lookupTarget());
    } finally {
      restoreSystemProperty("fingrind.source-checkout.build-root", originalSourceCheckoutBuildRoot);
      restoreSystemProperty("fingrind.source-checkout.root", originalSourceCheckoutRoot);
    }
  }

  @Test
  void configuredLibraryTarget_resolvesManagedLibraryFromExternalizedSourceCheckoutBuildRoot()
      throws IOException {
    Path sourceCheckoutRoot = tempDirectory.resolve("FinGrind");
    Path externalizedBuildRoot =
        tempDirectory.resolve("cache").resolve("project-build").resolve("root");
    Path managedLibraryPath =
        externalizedBuildRoot
            .resolve("managed-sqlite")
            .resolve(expectedManagedSqliteClassifier())
            .resolve(expectedNativeLibraryFileName());
    Files.createDirectories(sourceCheckoutRoot.resolve("cli"));
    Files.writeString(sourceCheckoutRoot.resolve("gradlew"), "#!/usr/bin/env bash\n");
    Files.createDirectories(managedLibraryPath.getParent());
    Files.writeString(managedLibraryPath, "sqlite3mc", StandardCharsets.UTF_8);
    SqliteLibraryTarget libraryTarget =
        SqliteNativeRuntimePolicy.configuredLibraryTarget(
            null, null, () -> List.of(sourceCheckoutRoot), () -> externalizedBuildRoot);
    assertEquals(SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED, libraryTarget.provenance());
    assertEquals(
        managedLibraryPath.toAbsolutePath().normalize().toString(), libraryTarget.lookupTarget());
  }

  @Test
  void configuredLibraryTarget_skipsSourceCheckoutCandidatesWithoutManagedLibrary()
      throws IOException {
    Path sourceCheckoutRoot = tempDirectory.resolve("FinGrind");
    Path managedSqliteRoot =
        sourceCheckoutRoot
            .resolve("build")
            .resolve("managed-sqlite")
            .resolve(expectedManagedSqliteClassifier());
    Files.createDirectories(sourceCheckoutRoot.resolve("cli"));
    Files.writeString(sourceCheckoutRoot.resolve("gradlew"), "#!/usr/bin/env bash\n");
    Files.createDirectories(managedSqliteRoot);
    Files.writeString(
        managedSqliteRoot.resolve("not-the-managed-library.txt"),
        "sqlite3mc",
        StandardCharsets.UTF_8);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimePolicy.configuredLibraryTarget(
                    null, null, () -> List.of(sourceCheckoutRoot), () -> null));
    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("prepareManagedSqlite"));
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
    String message = Objects.requireNonNull(exception.getMessage());
    assertTrue(message.contains("bundle home"));
  }

  @Test
  void configuredLibraryTarget_rejectsMissingOrBlankInputsAcrossBundleResolutionModes() {
    IllegalStateException missingEverywhere =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimePolicy.configuredLibraryTarget(
                    null, null, List::of, () -> null));
    IllegalStateException blankConfiguredPath =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimePolicy.configuredLibraryTarget(
                    "   ", null, List::of, () -> null));
    IllegalStateException blankBundleHome =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimePolicy.configuredLibraryTarget(
                    null, "   ", List::of, () -> null));
    assertTrue(Objects.requireNonNull(missingEverywhere.getMessage()).contains("bundle launcher"));
    assertTrue(
        Objects.requireNonNull(blankConfiguredPath.getMessage()).contains("bundle launcher"));
    assertTrue(Objects.requireNonNull(blankBundleHome.getMessage()).contains("bundle launcher"));
  }

  @Test
  void sourceCheckoutRoots_deduplicateNestedCandidatesAndAcceptNullInputs() throws IOException {
    Path sourceCheckoutRoot = tempDirectory.resolve("FinGrind");
    Path incompleteCandidate = tempDirectory.resolve("incomplete");
    Files.createDirectories(sourceCheckoutRoot.resolve("cli").resolve("nested"));
    Files.writeString(sourceCheckoutRoot.resolve("gradlew"), "#!/usr/bin/env bash\n");
    Files.createDirectories(incompleteCandidate);
    Files.writeString(incompleteCandidate.resolve("gradlew"), "#!/usr/bin/env bash\n");
    assertEquals(List.of(), SqliteNativeRuntimePolicy.sourceCheckoutRoots(null, null));
    assertEquals(
        List.of(sourceCheckoutRoot.toAbsolutePath().normalize()),
        SqliteNativeRuntimePolicy.sourceCheckoutRoots(
            incompleteCandidate.toString(),
            sourceCheckoutRoot.resolve("cli").resolve("nested").toString()));
  }

  @Test
  void sourceCheckoutRootFromCodeSource_preservesTheNormalizedCodeSourcePath() throws IOException {
    Path sourceCheckoutRoot = tempDirectory.resolve("FinGrind");
    Path nestedCodeSource = sourceCheckoutRoot.resolve("cli").resolve("build").resolve("libs");
    Files.createDirectories(nestedCodeSource);
    Files.writeString(sourceCheckoutRoot.resolve("gradlew"), "#!/usr/bin/env bash\n");

    assertEquals(
        nestedCodeSource.toAbsolutePath().normalize().toString(),
        SqliteNativeRuntimePolicy.sourceCheckoutRootFromCodeSource(nestedCodeSource));
  }

  @Test
  void sourceCheckoutBuildRoot_prefersConfiguredBuildRootWhenPresent() {
    Path configuredBuildRoot = tempDirectory.resolve("configured-build-root");
    assertEquals(
        configuredBuildRoot.toAbsolutePath().normalize(),
        SqliteNativeRuntimePolicy.sourceCheckoutBuildRoot(configuredBuildRoot.toString()));
  }

  @Test
  void sourceCheckoutBuildRoot_returnsNullWhenNoConfiguredCueExists() {
    assertEquals(null, SqliteNativeRuntimePolicy.sourceCheckoutBuildRoot(null));
  }

  @Test
  void sourceCheckoutRootFromCodeSource_handlesNullFilesAndDirectories() throws IOException {
    Path codeSourceDirectory = tempDirectory.resolve("classes");
    Path codeSourceJar = tempDirectory.resolve("fingrind.jar");
    Path codeSourcePathWithoutExistingFile = tempDirectory.resolve("missing-classes");
    Files.createDirectories(codeSourceDirectory);
    Files.writeString(codeSourceJar, "jar", StandardCharsets.UTF_8);
    assertEquals(null, SqliteNativeRuntimePolicy.sourceCheckoutRootFromCodeSource(null));
    assertEquals(
        codeSourcePathWithoutExistingFile.toAbsolutePath().normalize().toString(),
        SqliteNativeRuntimePolicy.sourceCheckoutRootFromCodeSource(
            codeSourcePathWithoutExistingFile));
    assertEquals(
        Objects.requireNonNull(codeSourceJar.getParent()).toString(),
        SqliteNativeRuntimePolicy.sourceCheckoutRootFromCodeSource(codeSourceJar));
    assertEquals(
        codeSourceDirectory.toString(),
        SqliteNativeRuntimePolicy.sourceCheckoutRootFromCodeSource(codeSourceDirectory));
  }

  @Test
  void codeSourcePath_handlesNullValidAndInvalidLocations() throws IOException {
    Path classesDirectory = tempDirectory.resolve("classes");
    Files.createDirectories(classesDirectory);
    assertEquals(null, SqliteNativeRuntimePolicy.codeSourcePath((String) null));
    assertEquals(null, SqliteNativeRuntimePolicy.codeSourcePath((CodeSource) null));
    assertEquals(
        null,
        SqliteNativeRuntimePolicy.codeSourcePath(new CodeSource((URL) null, (Certificate[]) null)));
    assertEquals(
        classesDirectory.toAbsolutePath().normalize(),
        SqliteNativeRuntimePolicy.codeSourcePath(classesDirectory.toUri().toString()));
    assertEquals(null, SqliteNativeRuntimePolicy.codeSourcePath("://bad"));
  }

  @Test
  void sourceCheckoutManagedLibraryPath_returnsNullWhenDirectoryIsMissingOrFinderFails()
      throws IOException {
    Path sourceCheckoutRoot = tempDirectory.resolve("FinGrind");
    Path managedSqliteRoot =
        sourceCheckoutRoot
            .resolve("build")
            .resolve("managed-sqlite")
            .resolve(expectedManagedSqliteClassifier());
    assertEquals(
        null,
        SqliteNativeRuntimePolicy.sourceCheckoutManagedLibraryPath(
            sourceCheckoutRoot,
            (root, expectedFileName) -> {
              throw new IOException("boom");
            }));
    Files.createDirectories(managedSqliteRoot);
    assertEquals(
        null,
        SqliteNativeRuntimePolicy.sourceCheckoutManagedLibraryPath(
            sourceCheckoutRoot,
            (root, expectedFileName) -> {
              throw new IOException("boom");
            }));
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
      String message = Objects.requireNonNull(exception.getMessage());
      assertTrue(message.contains("macOS, Linux, and Windows only"));
      assertTrue(message.contains("FreeBSD"));
    } finally {
      restoreSystemProperty("os.name", originalOsName);
    }
  }

  @Test
  void supportedNativeLibraryFileName_provesWindowsAndUnsupportedBranchesIndependently() {
    assertEquals(
        "sqlite3.dll",
        SqliteNativeRuntimePolicy.supportedNativeLibraryFileName("windows", "Windows Server 2025"));
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimePolicy.supportedNativeLibraryFileName("solaris", "Solaris"));
    assertTrue(NullTestSupport.messageOf(exception).contains("Solaris"));
  }

  @Test
  void supportedHostClassifier_normalizesKnownAndCustomArchitectures() {
    String originalOsName = System.getProperty("os.name");
    String originalOsArch = System.getProperty("os.arch");
    try {
      System.setProperty("os.name", "Windows 11");
      System.setProperty("os.arch", "arm64");
      assertEquals("windows-aarch64", SqliteNativeRuntimePolicy.supportedHostClassifier());
      System.setProperty("os.arch", "x64");
      assertEquals("windows-x86_64", SqliteNativeRuntimePolicy.supportedHostClassifier());
      System.setProperty("os.arch", "POWER PC 64");
      assertEquals("windows-power-pc-64", SqliteNativeRuntimePolicy.supportedHostClassifier());
    } finally {
      restoreSystemProperty("os.name", originalOsName);
      restoreSystemProperty("os.arch", originalOsArch);
    }
  }

  private static String expectedManagedSqliteClassifier() {
    return SqliteNativeRuntimePolicy.supportedHostClassifier();
  }
}
