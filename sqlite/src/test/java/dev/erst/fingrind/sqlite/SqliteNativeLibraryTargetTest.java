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
                SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
                    null, List::of, () -> null));
    String message = Objects.requireNonNull(exception.getMessage());
    assertTrue(message.contains("bundle launcher"));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteManagedLibraryTargetLocator.configuredLibraryTarget(null, List::of, () -> null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SqliteLibraryTarget(" ", SqliteRuntimeProvenance.BUNDLE_MANAGED, "x"));
  }

  @Test
  void configuredLibraryTarget_blankBundleHomeFallsBackToSupportedResolutionPaths() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
                    "   ", List::of, () -> null));
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
        SqliteManagedLibraryTargetLocator.configuredLibraryTarget(bundleHomePath.toString());
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
        SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
            null, () -> List.of(sourceCheckoutRoot), () -> null);
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
          SqliteManagedLibraryTargetLocator.configuredLibraryTarget();
      assertEquals(SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED, libraryTarget.provenance());
      assertEquals(
          managedLibraryPath.toAbsolutePath().normalize().toString(), libraryTarget.lookupTarget());
    } finally {
      restoreSystemProperty("fingrind.source-checkout.build-root", originalSourceCheckoutBuildRoot);
      restoreSystemProperty("fingrind.source-checkout.root", originalSourceCheckoutRoot);
    }
  }

  @Test
  void configuredLibraryTarget_bundleHomeOverload_resolvesManagedLibraryFromDefaultDetection()
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
      SqliteLibraryTarget libraryTarget =
          SqliteManagedLibraryTargetLocator.configuredLibraryTarget();
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
        SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
            null, () -> List.of(sourceCheckoutRoot), () -> externalizedBuildRoot);
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
                SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
                    null, () -> List.of(sourceCheckoutRoot), () -> null));
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
                SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
                    bundleHomePath.toString()));
    String message = Objects.requireNonNull(exception.getMessage());
    assertTrue(message.contains("bundle home"));
  }

  @Test
  void configuredLibraryTarget_rejectsMissingOrBlankInputsAcrossBundleResolutionModes() {
    IllegalStateException missingEverywhere =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
                    null, List::of, () -> null));
    IllegalStateException blankBundleHome =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
                    "   ", List::of, () -> null));
    assertTrue(Objects.requireNonNull(missingEverywhere.getMessage()).contains("bundle launcher"));
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
    assertEquals(List.of(), SqliteSourceCheckoutRuntimeLocator.sourceCheckoutRoots(null, null));
    assertEquals(
        List.of(sourceCheckoutRoot.toAbsolutePath().normalize()),
        SqliteSourceCheckoutRuntimeLocator.sourceCheckoutRoots(
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
        SqliteSourceCheckoutRuntimeLocator.sourceCheckoutRootFromCodeSource(nestedCodeSource));
  }

  @Test
  void sourceCheckoutBuildRoot_prefersConfiguredBuildRootWhenPresent() {
    Path configuredBuildRoot = tempDirectory.resolve("configured-build-root");
    assertEquals(
        configuredBuildRoot.toAbsolutePath().normalize(),
        SqliteSourceCheckoutRuntimeLocator.sourceCheckoutBuildRoot(configuredBuildRoot.toString()));
  }

  @Test
  void sourceCheckoutBuildRoot_returnsNullWhenNoConfiguredCueExists() {
    assertEquals(null, SqliteSourceCheckoutRuntimeLocator.sourceCheckoutBuildRoot(null));
  }

  @Test
  void sourceCheckoutBuildRoot_treatsBlankConfiguredPathsAsMissing() {
    assertEquals(null, SqliteSourceCheckoutRuntimeLocator.sourceCheckoutBuildRoot("   "));
  }

  @Test
  void sourceCheckoutRootFromCodeSource_handlesNullFilesAndDirectories() throws IOException {
    Path codeSourceDirectory = tempDirectory.resolve("classes");
    Path codeSourceJar = tempDirectory.resolve("fingrind.jar");
    Path codeSourcePathWithoutExistingFile = tempDirectory.resolve("missing-classes");
    Files.createDirectories(codeSourceDirectory);
    Files.writeString(codeSourceJar, "jar", StandardCharsets.UTF_8);
    assertEquals(null, SqliteSourceCheckoutRuntimeLocator.sourceCheckoutRootFromCodeSource(null));
    assertEquals(
        codeSourcePathWithoutExistingFile.toAbsolutePath().normalize().toString(),
        SqliteSourceCheckoutRuntimeLocator.sourceCheckoutRootFromCodeSource(
            codeSourcePathWithoutExistingFile));
    assertEquals(
        Objects.requireNonNull(codeSourceJar.getParent()).toString(),
        SqliteSourceCheckoutRuntimeLocator.sourceCheckoutRootFromCodeSource(codeSourceJar));
    assertEquals(
        codeSourceDirectory.toString(),
        SqliteSourceCheckoutRuntimeLocator.sourceCheckoutRootFromCodeSource(codeSourceDirectory));
  }

  @Test
  void codeSourcePath_handlesNullValidAndInvalidLocations() throws IOException {
    Path classesDirectory = tempDirectory.resolve("classes");
    Files.createDirectories(classesDirectory);
    assertEquals(null, SqliteSourceCheckoutRuntimeLocator.codeSourcePath((String) null));
    assertEquals(null, SqliteSourceCheckoutRuntimeLocator.codeSourcePath((CodeSource) null));
    assertEquals(
        null,
        SqliteSourceCheckoutRuntimeLocator.codeSourcePath(
            new CodeSource((URL) null, (Certificate[]) null)));
    assertEquals(
        classesDirectory.toAbsolutePath().normalize(),
        SqliteSourceCheckoutRuntimeLocator.codeSourcePath(classesDirectory.toUri().toString()));
    assertEquals(null, SqliteSourceCheckoutRuntimeLocator.codeSourcePath("://bad"));
  }

  @Test
  void
      sourceCheckoutManagedLibraryPath_returnsNullWhenDirectoryIsMissingAndFailsWhenInspectionBreaks()
          throws IOException {
    Path sourceCheckoutRoot = tempDirectory.resolve("FinGrind");
    Path managedSqliteRoot =
        sourceCheckoutRoot
            .resolve("build")
            .resolve("managed-sqlite")
            .resolve(expectedManagedSqliteClassifier());
    assertEquals(
        null,
        SqliteSourceCheckoutRuntimeLocator.sourceCheckoutManagedLibraryPath(
            sourceCheckoutRoot,
            (root, expectedFileName) -> {
              throw new IOException("boom");
            }));
    Files.createDirectories(managedSqliteRoot);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteSourceCheckoutRuntimeLocator.sourceCheckoutManagedLibraryPath(
                    sourceCheckoutRoot,
                    (root, expectedFileName) -> {
                      throw new IOException("boom");
                    }));
    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("could not inspect it"));
    assertEquals("boom", Objects.requireNonNull(exception.getCause()).getMessage());
  }

  @Test
  void supportedNativeLibraryFileName_supportsMacOsLinuxWindowsAndRejectsUnsupportedHosts() {
    assertEquals(
        "libsqlite3.dylib",
        SqliteHostPlatformDescriptor.supportedNativeLibraryFileName(
            SqliteHostPlatformDescriptor.supportedOperatingSystemId("Mac OS X"), "Mac OS X"));
    assertEquals(
        "libsqlite3.so.0",
        SqliteHostPlatformDescriptor.supportedNativeLibraryFileName(
            SqliteHostPlatformDescriptor.supportedOperatingSystemId("Linux"), "Linux"));
    assertEquals(
        "sqlite3.dll",
        SqliteHostPlatformDescriptor.supportedNativeLibraryFileName(
            SqliteHostPlatformDescriptor.supportedOperatingSystemId("Windows 11"), "Windows 11"));
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteHostPlatformDescriptor.supportedOperatingSystemId("FreeBSD"));
    String message = Objects.requireNonNull(exception.getMessage());
    assertTrue(message.contains("macOS, Linux, and Windows only"));
    assertTrue(message.contains("FreeBSD"));
  }

  @Test
  void supportedNativeLibraryFileName_provesWindowsAndUnsupportedBranchesIndependently() {
    assertEquals(
        "sqlite3.dll",
        SqliteHostPlatformDescriptor.supportedNativeLibraryFileName(
            "windows", "Windows Server 2025"));
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteHostPlatformDescriptor.supportedNativeLibraryFileName("solaris", "Solaris"));
    assertTrue(NullTestSupport.messageOf(exception).contains("Solaris"));
  }

  @Test
  void supportedHostClassifier_normalizesKnownAndCustomArchitectures() {
    assertEquals(
        "windows-aarch64",
        SqliteHostPlatformDescriptor.supportedHostClassifier("Windows 11", "arm64"));
    assertEquals(
        "windows-x86_64",
        SqliteHostPlatformDescriptor.supportedHostClassifier("Windows 11", "x64"));
    assertEquals(
        "windows-power-pc-64",
        SqliteHostPlatformDescriptor.supportedHostClassifier("Windows 11", "POWER PC 64"));
  }

  private static String expectedManagedSqliteClassifier() {
    return SqliteHostPlatformDescriptor.supportedHostClassifier();
  }
}
