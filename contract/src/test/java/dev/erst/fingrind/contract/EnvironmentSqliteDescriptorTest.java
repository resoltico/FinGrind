package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteLibraryMode;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Covers status-dependent invariant checks on the environment SQLite descriptor. */
@NullUnmarked
class EnvironmentSqliteDescriptorTest {
  @Test
  void readyStatus_requiresRuntimeProvenanceLoadedFieldsAndNoRuntimeIssue() {
    IllegalArgumentException readyWithoutVerifiedCompileOptions =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                    SqliteRuntimeStatus.READY,
                    SqliteRuntimeProvenance.BUNDLE_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.0",
                    "2.3.3",
                    ProtocolCatalog.requiredSqliteSourceId(),
                    null));
    assertEquals(
        "compileOptionsVerification must be VERIFIED when SQLite runtime status is READY.",
        readyWithoutVerifiedCompileOptions.getMessage());

    IllegalArgumentException missingProvenance =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.READY,
                    null,
                    "/tmp/libsqlite3.dylib",
                    "3.53.0",
                    "2.3.3",
                    ProtocolCatalog.requiredSqliteSourceId(),
                    null));
    assertEquals(
        "runtimeProvenance is required when SQLite runtime status is READY.",
        missingProvenance.getMessage());

    IllegalArgumentException missingLibraryPath =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.READY,
                    SqliteRuntimeProvenance.BUNDLE_MANAGED,
                    null,
                    "3.53.0",
                    "2.3.3",
                    ProtocolCatalog.requiredSqliteSourceId(),
                    null));
    assertEquals(
        "loadedLibraryPath is required when SQLite runtime status is READY.",
        missingLibraryPath.getMessage());

    IllegalArgumentException missingLoadedVersionFields =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.READY,
                    SqliteRuntimeProvenance.BUNDLE_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    null,
                    "2.3.3",
                    ProtocolCatalog.requiredSqliteSourceId(),
                    null));
    assertEquals(
        "Loaded SQLite version, SQLite3MC version, and source id are required when SQLite runtime status is READY.",
        missingLoadedVersionFields.getMessage());

    IllegalArgumentException missingLoadedSqlite3mcVersion =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.READY,
                    SqliteRuntimeProvenance.BUNDLE_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.0",
                    null,
                    ProtocolCatalog.requiredSqliteSourceId(),
                    null));
    assertEquals(
        "Loaded SQLite version, SQLite3MC version, and source id are required when SQLite runtime status is READY.",
        missingLoadedSqlite3mcVersion.getMessage());

    IllegalArgumentException missingLoadedSqliteSourceId =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.READY,
                    SqliteRuntimeProvenance.BUNDLE_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.0",
                    "2.3.3",
                    null,
                    null));
    assertEquals(
        "Loaded SQLite version, SQLite3MC version, and source id are required when SQLite runtime status is READY.",
        missingLoadedSqliteSourceId.getMessage());

    IllegalArgumentException readyWithRuntimeIssue =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.READY,
                    SqliteRuntimeProvenance.BUNDLE_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.0",
                    "2.3.3",
                    ProtocolCatalog.requiredSqliteSourceId(),
                    "unexpected"));
    assertEquals(
        "runtimeIssue must be absent when SQLite runtime status is READY.",
        readyWithRuntimeIssue.getMessage());
  }

  @Test
  void unavailableStatus_requiresRuntimeIssueAndForbidsLoadedFields() {
    IllegalArgumentException unavailableWithFailedCompileOptions =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteCompileOptionsVerificationStatus.FAILED,
                    SqliteRuntimeStatus.UNAVAILABLE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "missing native library"));
    assertEquals(
        "compileOptionsVerification must be NOT_VERIFIED when SQLite runtime status is UNAVAILABLE.",
        unavailableWithFailedCompileOptions.getMessage());

    IllegalArgumentException unavailableWithLoadedFields =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.UNAVAILABLE,
                    SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                    "/tmp/libsqlite3.dylib",
                    null,
                    null,
                    null,
                    "missing native library"));
    assertEquals(
        "Loaded SQLite provenance and version fields must be absent when SQLite runtime status is UNAVAILABLE.",
        unavailableWithLoadedFields.getMessage());

    IllegalArgumentException unavailableWithLoadedLibraryPath =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.UNAVAILABLE,
                    null,
                    "/tmp/libsqlite3.dylib",
                    null,
                    null,
                    null,
                    "missing native library"));
    assertEquals(
        "Loaded SQLite provenance and version fields must be absent when SQLite runtime status is UNAVAILABLE.",
        unavailableWithLoadedLibraryPath.getMessage());

    IllegalArgumentException unavailableWithLoadedSqliteVersion =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.UNAVAILABLE,
                    null,
                    null,
                    "3.53.0",
                    null,
                    null,
                    "missing native library"));
    assertEquals(
        "Loaded SQLite provenance and version fields must be absent when SQLite runtime status is UNAVAILABLE.",
        unavailableWithLoadedSqliteVersion.getMessage());

    IllegalArgumentException unavailableWithLoadedSqlite3mcVersion =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.UNAVAILABLE,
                    null,
                    null,
                    null,
                    "2.3.3",
                    null,
                    "missing native library"));
    assertEquals(
        "Loaded SQLite provenance and version fields must be absent when SQLite runtime status is UNAVAILABLE.",
        unavailableWithLoadedSqlite3mcVersion.getMessage());

    IllegalArgumentException unavailableWithLoadedSqliteSourceId =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.UNAVAILABLE,
                    null,
                    null,
                    null,
                    null,
                    "source-id",
                    "missing native library"));
    assertEquals(
        "Loaded SQLite provenance and version fields must be absent when SQLite runtime status is UNAVAILABLE.",
        unavailableWithLoadedSqliteSourceId.getMessage());

    IllegalArgumentException unavailableWithoutRuntimeIssue =
        assertThrows(
            IllegalArgumentException.class,
            () -> descriptor(SqliteRuntimeStatus.UNAVAILABLE, null, null, null, null, null, null));
    assertEquals(
        "runtimeIssue is required when SQLite runtime status is UNAVAILABLE.",
        unavailableWithoutRuntimeIssue.getMessage());
  }

  @Test
  void incompatibleStatus_requiresProvenanceLoadedFieldsAndRuntimeIssue() {
    IllegalArgumentException incompatibleWithVerifiedCompileOptions =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteCompileOptionsVerificationStatus.VERIFIED,
                    SqliteRuntimeStatus.INCOMPATIBLE,
                    SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.0",
                    "2.3.3",
                    ProtocolCatalog.requiredSqliteSourceId(),
                    "source id mismatch"));
    assertEquals(
        "compileOptionsVerification must not be VERIFIED when SQLite runtime status is INCOMPATIBLE.",
        incompatibleWithVerifiedCompileOptions.getMessage());

    IllegalArgumentException missingProvenance =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.INCOMPATIBLE,
                    null,
                    "/tmp/libsqlite3.dylib",
                    "3.53.0",
                    "2.3.3",
                    ProtocolCatalog.requiredSqliteSourceId(),
                    "source id mismatch"));
    assertEquals(
        "runtimeProvenance is required when SQLite runtime status is INCOMPATIBLE.",
        missingProvenance.getMessage());

    IllegalArgumentException missingLoadedLibraryPath =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.INCOMPATIBLE,
                    SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                    null,
                    "3.53.0",
                    "2.3.3",
                    ProtocolCatalog.requiredSqliteSourceId(),
                    "source id mismatch"));
    assertEquals(
        "Loaded SQLite provenance, version, source id, and runtimeIssue are required when SQLite runtime status is INCOMPATIBLE.",
        missingLoadedLibraryPath.getMessage());

    IllegalArgumentException missingLoadedSqliteVersion =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.INCOMPATIBLE,
                    SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                    "/tmp/libsqlite3.dylib",
                    null,
                    "2.3.3",
                    ProtocolCatalog.requiredSqliteSourceId(),
                    "source id mismatch"));
    assertEquals(
        "Loaded SQLite provenance, version, source id, and runtimeIssue are required when SQLite runtime status is INCOMPATIBLE.",
        missingLoadedSqliteVersion.getMessage());

    IllegalArgumentException missingLoadedSqlite3mcVersion =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.INCOMPATIBLE,
                    SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.0",
                    null,
                    ProtocolCatalog.requiredSqliteSourceId(),
                    "source id mismatch"));
    assertEquals(
        "Loaded SQLite provenance, version, source id, and runtimeIssue are required when SQLite runtime status is INCOMPATIBLE.",
        missingLoadedSqlite3mcVersion.getMessage());

    IllegalArgumentException missingLoadedSqliteSourceId =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.INCOMPATIBLE,
                    SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.0",
                    "2.3.3",
                    null,
                    "source id mismatch"));
    assertEquals(
        "Loaded SQLite provenance, version, source id, and runtimeIssue are required when SQLite runtime status is INCOMPATIBLE.",
        missingLoadedSqliteSourceId.getMessage());

    IllegalArgumentException missingRuntimeIssue =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.INCOMPATIBLE,
                    SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.0",
                    "2.3.3",
                    ProtocolCatalog.requiredSqliteSourceId(),
                    null));
    assertEquals(
        "Loaded SQLite provenance, version, source id, and runtimeIssue are required when SQLite runtime status is INCOMPATIBLE.",
        missingRuntimeIssue.getMessage());

    EnvironmentSqliteDescriptor descriptor =
        descriptor(
            SqliteRuntimeStatus.INCOMPATIBLE,
            SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
            "/tmp/libsqlite3.dylib",
            "3.53.0",
            "2.3.3",
            "different-source-id",
            "source id mismatch");
    assertEquals(SqliteRuntimeStatus.INCOMPATIBLE, descriptor.runtimeStatus());
    assertEquals(SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED, descriptor.runtimeProvenance());
    assertEquals("different-source-id", descriptor.loadedSqliteSourceId());
    assertEquals("source id mismatch", descriptor.runtimeIssue());
  }

  private static EnvironmentSqliteDescriptor descriptor(
      SqliteRuntimeStatus runtimeStatus,
      SqliteRuntimeProvenance runtimeProvenance,
      String loadedLibraryPath,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String loadedSqliteSourceId,
      String runtimeIssue) {
    SqliteCompileOptionsVerificationStatus compileOptionsVerification =
        switch (runtimeStatus) {
          case READY -> SqliteCompileOptionsVerificationStatus.VERIFIED;
          case UNAVAILABLE, INCOMPATIBLE -> SqliteCompileOptionsVerificationStatus.NOT_VERIFIED;
        };
    return descriptor(
        compileOptionsVerification,
        runtimeStatus,
        runtimeProvenance,
        loadedLibraryPath,
        loadedSqliteVersion,
        loadedSqlite3mcVersion,
        loadedSqliteSourceId,
        runtimeIssue);
  }

  private static EnvironmentSqliteDescriptor descriptor(
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      SqliteRuntimeStatus runtimeStatus,
      SqliteRuntimeProvenance runtimeProvenance,
      String loadedLibraryPath,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String loadedSqliteSourceId,
      String runtimeIssue) {
    return new EnvironmentSqliteDescriptor(
        SqliteLibraryMode.MANAGED_ONLY,
        "FINGRIND_SQLITE_LIBRARY",
        "fingrind.sqlite.bundle.home",
        List.of("THREADSAFE=1", "SECURE_DELETE"),
        compileOptionsVerification,
        "3.53.0",
        "2.3.3",
        ProtocolCatalog.requiredSqliteSourceId(),
        runtimeStatus,
        runtimeProvenance,
        loadedLibraryPath,
        loadedSqliteVersion,
        loadedSqlite3mcVersion,
        loadedSqliteSourceId,
        runtimeIssue);
  }
}
