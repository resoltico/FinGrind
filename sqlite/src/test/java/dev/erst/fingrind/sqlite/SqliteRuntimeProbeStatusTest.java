package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.runtime.SqliteRuntimeArtifactEvidence;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
class SqliteRuntimeProbeStatusTest extends SqliteNativeBridgeTestSupport {
  private static final MethodHandle ARTIFACT_EVIDENCE =
      runtimeHelper(
          "artifactEvidence",
          MethodType.methodType(SqliteRuntimeArtifactEvidence.class, String.class));

  @Test
  void sqliteRuntimeProbe_reportsManagedSupportedVersion() {
    SqliteRuntime.Probe runtimeProbe = SqliteRuntime.probe();
    assertEquals("sqlite-ffm-sqlite3mc", SqliteRuntime.STORAGE_DRIVER);
    assertEquals("sqlite", SqliteRuntime.STORAGE_ENGINE);
    assertEquals("required", SqliteRuntime.BOOK_PROTECTION_MODE);
    assertEquals("chacha20", SqliteRuntime.DEFAULT_BOOK_CIPHER);
    assertEquals("fingrind.bundle.home", SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY);
    assertEquals(
        List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
        SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS);
    assertEquals("3.53.4", SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION);
    assertEquals("2.4.0", SqliteRuntime.REQUIRED_SQLITE3MC_VERSION);
    assertEquals(
        ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.4", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.4.0", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.requiredSqliteSourceId());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.VERIFIED, runtimeProbe.compileOptionsVerification());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals(SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED, runtimeProbe.runtimeProvenance());
    assertEquals(
        SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
        runtimeProbe.runtimeTrustBasis());
    assertFalse(requireLoadedLibraryPath(runtimeProbe).isBlank());
    assertEquals("3.53.4", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.4.0", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.loadedSqliteSourceId());
    assertFalse(SqliteRuntime.sqliteVersion().isBlank());
    assertEquals("2.4.0", SqliteRuntime.sqlite3MultipleCiphersVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, SqliteRuntime.sqliteSourceId());
    assertEquals(
        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED, SqliteRuntime.runtimeProvenance());
    assertEquals(
        SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
        SqliteRuntime.runtimeTrustBasis());
    assertFalse(SqliteRuntime.loadedLibraryPath().isBlank());
    assertEquals(
        new SqliteProtectedBookFormatIntrospection.CipherSettings(
            ProtocolCatalog.runtime().protectedBookFormat().cipher(),
            ProtocolCatalog.runtime().protectedBookFormat().legacyMode(),
            ProtocolCatalog.runtime().protectedBookFormat().legacyPageSize(),
            ProtocolCatalog.runtime().protectedBookFormat().kdfIter(),
            ProtocolCatalog.runtime().protectedBookFormat().plaintextHeaderSize()),
        SqliteProtectedBookFormatIntrospection.runtimeDefaultCipherSettings(
            SqliteNativeBootstrap.api()));
  }

  @Test
  void failureDetail_prefersMessageAndFallsBackToType() {
    assertEquals("boom", SqliteRuntime.failureDetail(new IllegalStateException("boom")));
    assertEquals("RuntimeException", SqliteRuntime.failureDetail(new RuntimeException()));
    assertEquals(
        "Library load failed at /tmp/libsqlite3.dylib.",
        SqliteRuntime.failureDetail(
            new IllegalStateException("Library load failed at /tmp/libsqlite3.dylib.")));
  }

  @Test
  void failureDetail_preservesMachineDiagnosticPaths() {
    assertEquals(
        "Library load failed at /tmp/libsqlite3.dylib).",
        SqliteRuntime.failureDetail(
            new IllegalStateException("Library load failed at /tmp/libsqlite3.dylib).")));
    assertEquals(
        "Diagnostics referenced /opt/fingrind/bundle and C:\\sqlite\\sqlite3.dll].",
        SqliteRuntime.failureDetail(
            new IllegalStateException(
                "Diagnostics referenced /opt/fingrind/bundle and C:\\sqlite\\sqlite3.dll].")));
    assertEquals(
        "Artifacts landed at /tmp/sqlite3.dll, /tmp/sqlite3.dll; and /tmp/sqlite3.dll:",
        SqliteRuntime.failureDetail(
            new IllegalStateException(
                "Artifacts landed at /tmp/sqlite3.dll, /tmp/sqlite3.dll; and /tmp/sqlite3.dll:")));
  }

  @Test
  void absoluteLoadedLibraryPath_preservesAbsolutePathsAndResolvesRelativeValues() {
    assertEquals(
        Path.of("libsqlite3.dylib").toAbsolutePath().normalize().toString(),
        SqliteRuntime.absoluteLoadedLibraryPath("libsqlite3.dylib"));
    assertEquals(
        Path.of("x").toAbsolutePath().normalize().toString(),
        SqliteRuntime.absoluteLoadedLibraryPath("x"));
    assertEquals("/tmp", SqliteRuntime.absoluteLoadedLibraryPath("/tmp/"));
    assertEquals(
        "C:\\sqlite\\sqlite3.dll",
        SqliteRuntime.absoluteLoadedLibraryPath("C:\\sqlite\\sqlite3.dll"));
    assertEquals(
        "C:/sqlite/sqlite3.dll", SqliteRuntime.absoluteLoadedLibraryPath("C:/sqlite/sqlite3.dll"));
    assertEquals(
        Path.of("C:sqlite3.dll").toAbsolutePath().normalize().toString(),
        SqliteRuntime.absoluteLoadedLibraryPath("C:sqlite3.dll"));
    assertEquals(
        "\\\\server\\share\\sqlite3.dll",
        SqliteRuntime.absoluteLoadedLibraryPath("\\\\server\\share\\sqlite3.dll"));
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> SqliteRuntime.absoluteLoadedLibraryPath("   "));
    assertEquals("loadedLibraryPath must not be blank.", exception.getMessage());
  }

  @Test
  void artifactEvidence_requiresBothRuntimeProvenanceSidecars() throws Exception {
    Path libraryParent = tempDirectory.resolve("runtime-evidence");
    Files.createDirectories(libraryParent);
    Path libraryPath = libraryParent.resolve("libsqlite3.so.0");
    Files.writeString(libraryPath, "library");

    assertNull(artifactEvidence(libraryPath.toString()));

    Path toolchainFingerprintPath = libraryParent.resolve("toolchain-fingerprint.json");
    Path buildContractPath = libraryParent.resolve("build-contract.json");
    Files.writeString(toolchainFingerprintPath, "{\"toolchain\":\"test\"}");
    assertNull(artifactEvidence(libraryPath.toString()));
    Files.writeString(buildContractPath, "{\"contract\":\"test\"}");

    SqliteRuntimeArtifactEvidence evidence = artifactEvidence(libraryPath.toString());
    assertNotNull(evidence);
    assertEquals(
        toolchainFingerprintPath.toAbsolutePath().normalize().toString(),
        evidence.toolchainFingerprintPath());
    assertEquals(
        buildContractPath.toAbsolutePath().normalize().toString(), evidence.buildContractPath());
    assertNull(artifactEvidence("libsqlite3.so.0"));
  }

  @Test
  void sqliteNativeAccessGate_allowsTheCurrentRuntimeModuleWhenNativeAccessIsEnabled() {
    Module runtimeModule = SqliteNativeAccessGate.runtimeModule();

    assertTrue(SqliteNativeAccessGate.isEnabled(runtimeModule));
    assertDoesNotThrow(() -> SqliteNativeAccessGate.requireEnabled());
  }

  @Test
  void sqliteNativeAccessGate_reportsNamedModuleLaunchRequirements() {
    Module namedModule =
        ModuleLayer.boot().modules().stream()
            .filter(Module::isNamed)
            .filter(module -> !SqliteNativeAccessGate.isEnabled(module))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("Expected one named module without native access"));

    assertTrue(namedModule.isNamed());
    assertFalse(SqliteNativeAccessGate.isEnabled(namedModule));
    assertEquals(
        "--enable-native-access=" + namedModule.getName(),
        SqliteNativeAccessGate.requiredFlag(namedModule));
    assertTrue(
        SqliteNativeAccessGate.failureMessage(namedModule)
            .contains("SQLite native access is disabled for module " + namedModule.getName()));

    ManagedSqliteRuntimeUnavailableException exception =
        assertThrows(
            ManagedSqliteRuntimeUnavailableException.class,
            () -> SqliteNativeAccessGate.requireEnabled(namedModule));

    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains("--enable-native-access=" + namedModule.getName()));
  }

  @Test
  void probe_reportsIncompatibleRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        probe(
            () -> {
              throw new UnsupportedSqliteVersionException(
                  "3.51.0",
                  "3.53.4",
                  "managed-only",
                  "2.4.0",
                  SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
            },
            () -> "2.4.0",
            () -> SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.4", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.4.0", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals(SqliteRuntimeProvenance.BUNDLE_MANAGED, runtimeProbe.runtimeProvenance());
    assertEquals(
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY, runtimeProbe.runtimeTrustBasis());
    assertEquals("/tmp/libsqlite3.dylib", runtimeProbe.loadedLibraryPath());
    assertEquals("3.51.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.4.0", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.loadedSqliteSourceId());
    assertTrue(requireIssue(runtimeProbe).contains("requires SQLite 3.53.4 or newer"));
  }

  @Test
  void probe_reportsFailedRuntimeWithoutDiscardingResolvedRuntimeFacts() {
    SqliteRuntime.Probe runtimeProbe =
        probe(
            () -> {
              throw new IllegalStateException("sqlite runtime unavailable");
            },
            () -> "2.4.0",
            () -> SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertEquals(SqliteRuntime.Status.FAILED, runtimeProbe.status());
    assertEquals("sqlite runtime unavailable", runtimeProbe.issue());
    assertEquals(SqliteRuntimeProvenance.BUNDLE_MANAGED, runtimeProbe.runtimeProvenance());
    assertEquals(
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY, runtimeProbe.runtimeTrustBasis());
    assertEquals("/tmp/libsqlite3.dylib", runtimeProbe.loadedLibraryPath());
    assertNull(runtimeProbe.loadedSqliteVersion());
    assertNull(runtimeProbe.loadedSqlite3mcVersion());
    assertNull(runtimeProbe.loadedSqliteSourceId());
  }

  @Test
  void probe_reportsIncompatibleSqlite3mcRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        probe(
            () -> "3.53.4",
            () -> {
              throw new UnsupportedSqliteMultipleCiphersVersionException(
                  "2.3.2",
                  "2.4.0",
                  "managed-only",
                  "3.53.4",
                  SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
            },
            () -> SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals(
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY, runtimeProbe.runtimeTrustBasis());
    assertEquals("3.53.4", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.2", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.loadedSqliteSourceId());
    assertTrue(requireIssue(runtimeProbe).contains("requires SQLite3 Multiple Ciphers 2.4.0"));
  }

  @Test
  void probe_reportsIncompatibleSourceIdRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        probe(
            () -> "3.53.4",
            () -> "2.4.0",
            () -> {
              throw new UnsupportedSqliteSourceIdException(
                  "2026-04-09 wrong-source-id",
                  SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                  "managed-only",
                  "3.53.4",
                  "2.4.0");
            });
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertEquals(
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY, runtimeProbe.runtimeTrustBasis());
    assertEquals("3.53.4", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.4.0", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals("2026-04-09 wrong-source-id", runtimeProbe.loadedSqliteSourceId());
    assertTrue(requireIssue(runtimeProbe).contains("requires SQLite source id"));
  }

  @Test
  void probe_reportsIncompatibleCompileOptionsRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        probe(
            () -> {
              throw new UnsupportedSqliteCompileOptionsException(
                  "3.53.4",
                  "2.4.0",
                  SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                  "managed-only",
                  List.of("SECURE_DELETE"),
                  List.of());
            },
            () -> {
              throw new AssertionError("sqlite3mc version lookup should not run");
            },
            () -> SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.FAILED, runtimeProbe.compileOptionsVerification());
    assertEquals(
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY, runtimeProbe.runtimeTrustBasis());
    assertEquals("3.53.4", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.4.0", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.loadedSqliteSourceId());
    assertTrue(requireIssue(runtimeProbe).contains("missing required compile options"));
  }

  @Test
  void runtimeProbeAndStatusExposeStableValueSemantics() {
    SqliteRuntime.Probe runtimeProbe = readyProbe();
    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.4", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.4.0", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.requiredSqliteSourceId());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.VERIFIED, runtimeProbe.compileOptionsVerification());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals(SqliteRuntimeProvenance.BUNDLE_MANAGED, runtimeProbe.runtimeProvenance());
    assertEquals(
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY, runtimeProbe.runtimeTrustBasis());
    assertEquals("/tmp/libsqlite3.dylib", runtimeProbe.loadedLibraryPath());
    assertEquals("3.53.4", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.4.0", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.loadedSqliteSourceId());
    assertNull(runtimeProbe.issue());
    assertEquals(runtimeProbe, readyProbe());
    assertEquals(runtimeProbe.hashCode(), readyProbe().hashCode());
    assertTrue(runtimeProbe.toString().contains("managed-only"));
    assertEquals("ready", SqliteRuntime.Status.READY.wireValue());
    assertEquals("unavailable", SqliteRuntime.Status.UNAVAILABLE.wireValue());
    assertEquals("incompatible", SqliteRuntime.Status.INCOMPATIBLE.wireValue());
  }

  @Test
  void runtimeProbe_normalizesWhitespaceAroundRequiredAndOptionalFields() {
    SqliteRuntime.Probe runtimeProbe =
        new SqliteRuntime.Probe(
            "  managed-only  ",
            "  3.53.4  ",
            "  2.4.0  ",
            "  " + SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID + "  ",
            SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
            SqliteRuntime.Status.UNAVAILABLE,
            null,
            null,
            "   ",
            "\t",
            "\n",
            "  ",
            "  runtime unavailable  ",
            null);
    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.4", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.4.0", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.requiredSqliteSourceId());
    assertNull(runtimeProbe.runtimeProvenance());
    assertNull(runtimeProbe.runtimeTrustBasis());
    assertNull(runtimeProbe.loadedLibraryPath());
    assertNull(runtimeProbe.loadedSqliteVersion());
    assertNull(runtimeProbe.loadedSqlite3mcVersion());
    assertNull(runtimeProbe.loadedSqliteSourceId());
    assertEquals("runtime unavailable", runtimeProbe.issue());
  }

  @Test
  void runtimeProbe_trustBasisFollowsRuntimeProvenanceContract() {
    IllegalArgumentException missingProvenanceException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteRuntime.Probe(
                    "managed-only",
                    "3.53.4",
                    "2.4.0",
                    SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                    SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                    SqliteRuntime.Status.UNAVAILABLE,
                    null,
                    SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY,
                    null,
                    null,
                    null,
                    null,
                    "runtime unavailable",
                    null));
    assertEquals(
        "runtimeTrustBasis must be absent when runtimeProvenance is absent.",
        missingProvenanceException.getMessage());
    SqliteRuntime.Probe derivedTrustBasisProbe =
        new SqliteRuntime.Probe(
            "managed-only",
            "3.53.4",
            "2.4.0",
            SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
            SqliteCompileOptionsVerificationStatus.VERIFIED,
            SqliteRuntime.Status.READY,
            SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
            null,
            "/tmp/libsqlite3.dylib",
            "3.53.4",
            "2.4.0",
            SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
            null,
            null);
    assertEquals(
        SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
        derivedTrustBasisProbe.runtimeTrustBasis());
    IllegalArgumentException mismatchedTrustBasisException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteRuntime.Probe(
                    "managed-only",
                    "3.53.4",
                    "2.4.0",
                    SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                    SqliteCompileOptionsVerificationStatus.VERIFIED,
                    SqliteRuntime.Status.READY,
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY,
                    "/tmp/libsqlite3.dylib",
                    "3.53.4",
                    "2.4.0",
                    SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                    null,
                    null));
    assertEquals(
        "runtimeTrustBasis must match runtimeProvenance source-checkout-managed.",
        mismatchedTrustBasisException.getMessage());
  }

  @Test
  void runtimeProbe_rejectsMissingStatusShape() {
    assertThrows(
        NullPointerException.class,
        () -> runtimeProbe("managed-only", null, null, null, null, null, null, null, "boom"));
  }

  @Test
  void runtimeProbe_rejectsInvalidReadyShape() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.4",
                "2.4.0",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                null,
                "/tmp/libsqlite3.dylib",
                "3.53.4",
                "2.4.0",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                null,
                "3.53.4",
                "2.4.0",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                null,
                "2.4.0",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.4",
                null,
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.4",
                "2.4.0",
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.4",
                "2.4.0",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
  }

  @Test
  void runtimeProbe_rejectsInvalidUnavailableShape() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.FAILED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.4",
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                "/tmp/libsqlite3.dylib",
                null,
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                "3.53.4",
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                "2.4.0",
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                "boom"));
  }

  @Test
  void runtimeProbe_rejectsInvalidFailedShape() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.FAILED,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                null,
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.FAILED,
                null,
                "/tmp/libsqlite3.dylib",
                null,
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.FAILED,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                null,
                null,
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.FAILED,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                null,
                null,
                null,
                null));
  }

  @Test
  void runtimeProbe_rejectsInvalidIncompatibleShape() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.51.0",
                "2.4.0",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                null,
                "/tmp/libsqlite3.dylib",
                "3.51.0",
                "2.4.0",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                null,
                "3.51.0",
                "2.4.0",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                null,
                "2.4.0",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.4",
                null,
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.51.0",
                "2.4.0",
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.51.0",
                "2.4.0",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                " ",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                "boom"));
  }

  private static SqliteRuntime.Probe probe(
      Supplier<String> sqliteVersionSupplier,
      Supplier<String> sqlite3mcVersionSupplier,
      Supplier<String> sqliteSourceIdSupplier) {
    return SqliteRuntime.probe(
        () -> "managed-only",
        () -> SqliteRuntimeProvenance.BUNDLE_MANAGED,
        () -> "/tmp/libsqlite3.dylib",
        sqliteVersionSupplier,
        sqlite3mcVersionSupplier,
        sqliteSourceIdSupplier,
        SqliteRuntime::failureDetail);
  }

  private static SqliteRuntime.Probe readyProbe() {
    return runtimeProbe(
        "managed-only",
        SqliteCompileOptionsVerificationStatus.VERIFIED,
        SqliteRuntime.Status.READY,
        SqliteRuntimeProvenance.BUNDLE_MANAGED,
        "/tmp/libsqlite3.dylib",
        "3.53.4",
        "2.4.0",
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        null);
  }

  @NullUnmarked
  private static SqliteRuntime.Probe runtimeProbe(
      String libraryMode,
      @Nullable SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      SqliteRuntime.@Nullable Status status,
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String issue) {
    return new SqliteRuntime.Probe(
        libraryMode,
        "3.53.4",
        "2.4.0",
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        compileOptionsVerification,
        status,
        runtimeProvenance,
        runtimeProvenance == null
            ? null
            : SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
        loadedLibraryPath,
        loadedSqliteVersion,
        loadedSqlite3mcVersion,
        loadedSqliteSourceId,
        issue,
        null);
  }

  private static String requireLoadedLibraryPath(SqliteRuntime.Probe runtimeProbe) {
    return Objects.requireNonNull(runtimeProbe.loadedLibraryPath());
  }

  private static String requireIssue(SqliteRuntime.Probe runtimeProbe) {
    return Objects.requireNonNull(runtimeProbe.issue());
  }

  private static MethodHandle runtimeHelper(String methodName, MethodType methodType) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(SqliteRuntime.class, MethodHandles.lookup());
      return lookup.findStatic(SqliteRuntime.class, methodName, methodType);
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError("Failed to bind SQLite runtime helper: " + methodName, exception);
    }
  }

  private static @Nullable SqliteRuntimeArtifactEvidence artifactEvidence(
      String loadedLibraryPath) {
    try {
      return (SqliteRuntimeArtifactEvidence) ARTIFACT_EVIDENCE.invokeExact(loadedLibraryPath);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke SQLite runtime artifact evidence helper.", throwable);
    }
  }
}
