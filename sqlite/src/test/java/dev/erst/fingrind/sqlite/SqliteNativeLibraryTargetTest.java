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
import java.util.jar.Attributes;
import java.util.jar.Manifest;
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
    assertTrue(message.contains("FINGRIND_SQLITE_LIBRARY"));
    assertThrows(
        IllegalStateException.class, () -> SqliteNativeRuntimePolicy.configuredLibraryTarget(null));
  }

  @Test
  void configuredLibraryTarget_requiresManagedPathAndNormalizesIt() {
    String originalOperatorTrust = System.getProperty(SqliteRuntime.OPERATOR_TRUST_SYSTEM_PROPERTY);
    try {
      System.setProperty(SqliteRuntime.OPERATOR_TRUST_SYSTEM_PROPERTY, "true");
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
    } finally {
      restoreSystemProperty(SqliteRuntime.OPERATOR_TRUST_SYSTEM_PROPERTY, originalOperatorTrust);
    }
  }

  @Test
  void configuredLibraryTarget_prefersExplicitEnvironmentLibraryOverBundleHome() {
    String originalOperatorTrust = System.getProperty(SqliteRuntime.OPERATOR_TRUST_SYSTEM_PROPERTY);
    try {
      System.setProperty(SqliteRuntime.OPERATOR_TRUST_SYSTEM_PROPERTY, "true");
      SqliteLibraryTarget libraryTarget =
          SqliteNativeRuntimePolicy.configuredLibraryTarget(
              "./build/../sqlite/libsqlite3.so.0", tempDirectory.toString());
      assertEquals("managed-only", libraryTarget.mode());
      assertTrue(
          Path.of(libraryTarget.lookupTarget()).endsWith(Path.of("sqlite", "libsqlite3.so.0")));
    } finally {
      restoreSystemProperty(SqliteRuntime.OPERATOR_TRUST_SYSTEM_PROPERTY, originalOperatorTrust);
    }
  }

  @Test
  void configuredLibraryTarget_requiresExplicitOperatorTrustApproval() {
    String originalOperatorTrust = System.getProperty(SqliteRuntime.OPERATOR_TRUST_SYSTEM_PROPERTY);
    try {
      System.clearProperty(SqliteRuntime.OPERATOR_TRUST_SYSTEM_PROPERTY);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteNativeRuntimePolicy.configuredLibraryTarget(
                      "./build/../sqlite/libsqlite3.so.0", tempDirectory.toString()));
      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains(SqliteRuntime.OPERATOR_TRUST_SYSTEM_PROPERTY));
    } finally {
      restoreSystemProperty(SqliteRuntime.OPERATOR_TRUST_SYSTEM_PROPERTY, originalOperatorTrust);
    }
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
    try {
      System.setProperty(
          "fingrind.source-checkout.root", sourceCheckoutRoot.resolve("gradlew").toString());
      SqliteLibraryTarget libraryTarget =
          SqliteNativeRuntimePolicy.configuredLibraryTarget(null, null);
      assertEquals(SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED, libraryTarget.provenance());
      assertEquals(
          managedLibraryPath.toAbsolutePath().normalize().toString(), libraryTarget.lookupTarget());
    } finally {
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
    assertTrue(message.contains("FINGRIND_SQLITE_LIBRARY"));
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
        Objects.requireNonNull(blankConfiguredPath.getMessage())
            .contains("FINGRIND_SQLITE_LIBRARY"));
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
    assertEquals(List.of(), SqliteNativeRuntimePolicy.sourceCheckoutRoots(null, null, null));
    assertEquals(
        List.of(sourceCheckoutRoot.toAbsolutePath().normalize()),
        SqliteNativeRuntimePolicy.sourceCheckoutRoots(
            incompleteCandidate.toString(),
            sourceCheckoutRoot.resolve("gradlew").toString(),
            sourceCheckoutRoot.resolve("cli").resolve("nested").toString()));
  }

  @Test
  void sourceCheckoutRootFromManifest_handlesNullDirectoriesManifestsAndIoFailures()
      throws IOException {
    Path codeSourceJar = tempDirectory.resolve("fingrind.jar");
    Path sourceCheckoutRoot = tempDirectory.resolve("FinGrind");
    Files.createDirectories(sourceCheckoutRoot.resolve("cli"));
    Files.writeString(sourceCheckoutRoot.resolve("gradlew"), "#!/usr/bin/env bash\n");
    Files.writeString(codeSourceJar, "jar", StandardCharsets.UTF_8);
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest
        .getMainAttributes()
        .putValue("FinGrind-Source-Checkout-Root", sourceCheckoutRoot.toString());
    Path buildRoot = tempDirectory.resolve("project-build").resolve("root");
    manifest
        .getMainAttributes()
        .putValue("FinGrind-Source-Checkout-Build-Root", buildRoot.toString());
    assertEquals(
        sourceCheckoutRoot.toAbsolutePath().normalize().toString(),
        SqliteNativeRuntimePolicy.sourceCheckoutRootFromManifest(
            codeSourceJar, ignored -> manifest));
    assertEquals(
        buildRoot.toAbsolutePath().normalize().toString(),
        SqliteNativeRuntimePolicy.sourceCheckoutBuildRootFromManifest(
            codeSourceJar, ignored -> manifest));
    assertEquals(
        null, SqliteNativeRuntimePolicy.sourceCheckoutRootFromManifest(null, ignored -> manifest));
    assertEquals(
        null,
        SqliteNativeRuntimePolicy.sourceCheckoutBuildRootFromManifest(null, ignored -> manifest));
    assertEquals(
        null,
        SqliteNativeRuntimePolicy.sourceCheckoutRootFromManifest(
            tempDirectory, ignored -> manifest));
    assertEquals(
        null,
        SqliteNativeRuntimePolicy.sourceCheckoutBuildRootFromManifest(
            tempDirectory, ignored -> manifest));
    assertEquals(
        null,
        SqliteNativeRuntimePolicy.sourceCheckoutRootFromManifest(codeSourceJar, ignored -> null));
    assertEquals(
        null,
        SqliteNativeRuntimePolicy.sourceCheckoutBuildRootFromManifest(
            codeSourceJar, ignored -> null));
    assertEquals(
        null,
        SqliteNativeRuntimePolicy.sourceCheckoutRootFromManifest(
            codeSourceJar,
            ignored -> {
              throw new IOException("boom");
            }));
    assertEquals(
        null,
        SqliteNativeRuntimePolicy.sourceCheckoutBuildRootFromManifest(
            codeSourceJar,
            ignored -> {
              throw new IOException("boom");
            }));
  }

  @Test
  void sourceCheckoutBuildRoot_prefersConfiguredBuildRootOverManifestBuildRoot() {
    Path configuredBuildRoot = tempDirectory.resolve("configured-build-root");
    Path manifestBuildRoot = tempDirectory.resolve("manifest-build-root");
    assertEquals(
        configuredBuildRoot.toAbsolutePath().normalize(),
        SqliteNativeRuntimePolicy.sourceCheckoutBuildRoot(
            configuredBuildRoot.toString(), manifestBuildRoot.toString()));
  }

  @Test
  void sourceCheckoutBuildRoot_fallsBackToManifestBuildRoot() {
    Path manifestBuildRoot = tempDirectory.resolve("manifest-build-root");
    assertEquals(
        manifestBuildRoot.toAbsolutePath().normalize(),
        SqliteNativeRuntimePolicy.sourceCheckoutBuildRoot(null, manifestBuildRoot.toString()));
  }

  @Test
  void sourceCheckoutBuildRoot_returnsNullWhenNeitherCueExists() {
    assertEquals(null, SqliteNativeRuntimePolicy.sourceCheckoutBuildRoot(null, null));
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
