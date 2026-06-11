package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteLibraryMode;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Covers status-dependent invariant checks on the environment SQLite descriptor. */
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
                    "3.53.2",
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    "3.53.2",
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    "3.53.2",
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    "3.53.2",
                    null,
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    "3.53.2",
                    "2.3.5",
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
                    "3.53.2",
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
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
                    "3.53.2",
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
                    "2.3.5",
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
  void runtimeTrustBasis_followsRuntimeProvenanceContract() {
    IllegalArgumentException trustBasisWithoutProvenance =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new EnvironmentSqliteDescriptor(
                    SqliteLibraryMode.MANAGED_ONLY,
                    "fingrind.sqlite.bundle.home",
                    List.of("THREADSAFE=1", "SECURE_DELETE"),
                    List.of("USE_URI"),
                    true,
                    "3.53.2",
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
                    EnvironmentSqliteDescriptor.runtime(
                        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                        SqliteRuntimeStatus.UNAVAILABLE,
                        null,
                        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY,
                        null,
                        null,
                        null,
                        null,
                        "missing native library"),
                    null));
    assertEquals(
        "runtimeTrustBasis must be absent when runtimeProvenance is absent.",
        trustBasisWithoutProvenance.getMessage());
    IllegalArgumentException mismatchedTrustBasis =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new EnvironmentSqliteDescriptor(
                    SqliteLibraryMode.MANAGED_ONLY,
                    "fingrind.sqlite.bundle.home",
                    List.of("THREADSAFE=1", "SECURE_DELETE"),
                    List.of("USE_URI"),
                    true,
                    "3.53.2",
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
                    EnvironmentSqliteDescriptor.runtime(
                        SqliteCompileOptionsVerificationStatus.VERIFIED,
                        SqliteRuntimeStatus.READY,
                        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY,
                        "/tmp/libsqlite3.dylib",
                        "3.53.2",
                        "2.3.5",
                        ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
                        null),
                    null));
    assertEquals(
        "runtimeTrustBasis must match runtimeProvenance source-checkout-managed.",
        mismatchedTrustBasis.getMessage());
    EnvironmentSqliteDescriptor descriptor =
        new EnvironmentSqliteDescriptor(
            SqliteLibraryMode.MANAGED_ONLY,
            "fingrind.sqlite.bundle.home",
            List.of("THREADSAFE=1", "SECURE_DELETE"),
            List.of("USE_URI"),
            true,
            "3.53.2",
            "2.3.5",
            ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
            EnvironmentSqliteDescriptor.runtime(
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntimeStatus.READY,
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                null,
                "/tmp/libsqlite3.dylib",
                "3.53.2",
                "2.3.5",
                ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
                null),
            null);
    assertEquals(
        SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY, runtimeTrustBasis(descriptor));
  }

  @Test
  void failedStatus_requiresResolvedRuntimeFactsAndRuntimeIssue() {
    IllegalArgumentException failedWithVerifiedCompileOptions =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteCompileOptionsVerificationStatus.VERIFIED,
                    SqliteRuntimeStatus.FAILED,
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    null,
                    null,
                    null,
                    "native bridge failed"));
    assertEquals(
        "compileOptionsVerification must be NOT_VERIFIED when SQLite runtime status is FAILED.",
        failedWithVerifiedCompileOptions.getMessage());
    IllegalArgumentException missingProvenance =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.FAILED,
                    null,
                    "/tmp/libsqlite3.dylib",
                    null,
                    null,
                    null,
                    "native bridge failed"));
    assertEquals(
        "runtimeProvenance is required when SQLite runtime status is FAILED.",
        missingProvenance.getMessage());
    IllegalArgumentException missingLibraryPath =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.FAILED,
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    null,
                    null,
                    null,
                    null,
                    "native bridge failed"));
    assertEquals(
        "loadedLibraryPath is required when SQLite runtime status is FAILED.",
        missingLibraryPath.getMessage());
    IllegalArgumentException failedWithLoadedSqliteVersion =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.FAILED,
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.2",
                    null,
                    null,
                    "native bridge failed"));
    assertEquals(
        "Loaded SQLite version, SQLite3MC version, and source id must be absent when SQLite runtime status is FAILED.",
        failedWithLoadedSqliteVersion.getMessage());
    IllegalArgumentException failedWithLoadedSqlite3mcVersion =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.FAILED,
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    null,
                    "2.3.5",
                    null,
                    "native bridge failed"));
    assertEquals(
        "Loaded SQLite version, SQLite3MC version, and source id must be absent when SQLite runtime status is FAILED.",
        failedWithLoadedSqlite3mcVersion.getMessage());
    IllegalArgumentException failedWithLoadedSqliteSourceId =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.FAILED,
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    null,
                    null,
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
                    "native bridge failed"));
    assertEquals(
        "Loaded SQLite version, SQLite3MC version, and source id must be absent when SQLite runtime status is FAILED.",
        failedWithLoadedSqliteSourceId.getMessage());
    IllegalArgumentException missingRuntimeIssue =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    SqliteRuntimeStatus.FAILED,
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    null,
                    null,
                    null,
                    null));
    assertEquals(
        "runtimeIssue is required when SQLite runtime status is FAILED.",
        missingRuntimeIssue.getMessage());
    EnvironmentSqliteDescriptor descriptor =
        descriptor(
            SqliteRuntimeStatus.FAILED,
            SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
            "/tmp/libsqlite3.dylib",
            null,
            null,
            null,
            "native bridge failed");
    assertEquals(SqliteRuntimeStatus.FAILED, runtimeStatus(descriptor));
    assertEquals(
        SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY, runtimeTrustBasis(descriptor));
    assertEquals("native bridge failed", runtimeIssue(descriptor));
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
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.2",
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    "3.53.2",
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    null,
                    "3.53.2",
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    null,
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.2",
                    null,
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
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
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.2",
                    "2.3.5",
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
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib",
                    "3.53.2",
                    "2.3.5",
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
                    null));
    assertEquals(
        "Loaded SQLite provenance, version, source id, and runtimeIssue are required when SQLite runtime status is INCOMPATIBLE.",
        missingRuntimeIssue.getMessage());
    EnvironmentSqliteDescriptor descriptor =
        descriptor(
            SqliteRuntimeStatus.INCOMPATIBLE,
            SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
            "/tmp/libsqlite3.dylib",
            "3.53.2",
            "2.3.5",
            "different-source-id",
            "source id mismatch");
    assertEquals(SqliteRuntimeStatus.INCOMPATIBLE, runtimeStatus(descriptor));
    assertEquals(SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED, runtimeProvenance(descriptor));
    assertEquals(
        SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY, runtimeTrustBasis(descriptor));
    assertEquals("different-source-id", loadedSqliteSourceId(descriptor));
    assertEquals("source id mismatch", runtimeIssue(descriptor));
  }

  @Test
  void explicitRuntimeStates_exposeStatusAndCompileVerification() {
    EnvironmentSqliteDescriptor.ReadyRuntime ready =
        new EnvironmentSqliteDescriptor.ReadyRuntime(
            SqliteRuntimeProvenance.BUNDLE_MANAGED,
            SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY,
            "/tmp/libsqlite3.dylib",
            "3.53.2",
            "2.3.5",
            ProtocolCatalog.managedSqlite().requiredSqliteSourceId());
    assertEquals(SqliteRuntimeStatus.READY, ready.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.VERIFIED, ready.compileOptionsVerification());

    EnvironmentSqliteDescriptor.UnavailableRuntime unavailable =
        new EnvironmentSqliteDescriptor.UnavailableRuntime("missing native library");
    assertEquals(SqliteRuntimeStatus.UNAVAILABLE, unavailable.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        unavailable.compileOptionsVerification());

    EnvironmentSqliteDescriptor.FailedRuntime failed =
        new EnvironmentSqliteDescriptor.FailedRuntime(
            SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
            SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
            "/tmp/libsqlite3.dylib",
            "native bridge failed");
    assertEquals(SqliteRuntimeStatus.FAILED, failed.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED, failed.compileOptionsVerification());

    EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible =
        new EnvironmentSqliteDescriptor.IncompatibleRuntime(
            SqliteCompileOptionsVerificationStatus.FAILED,
            SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
            SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
            "/tmp/libsqlite3.dylib",
            "3.53.2",
            "2.3.5",
            "different-source-id",
            "source id mismatch");
    assertEquals(SqliteRuntimeStatus.INCOMPATIBLE, incompatible.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.FAILED, incompatible.compileOptionsVerification());
  }

  @Test
  void incompatibleRuntime_rejectsVerifiedCompileOptions() {
    IllegalArgumentException verifiedCompileOptions =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new EnvironmentSqliteDescriptor.IncompatibleRuntime(
                    SqliteCompileOptionsVerificationStatus.VERIFIED,
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
                    "/tmp/libsqlite3.dylib",
                    "3.53.2",
                    "2.3.5",
                    "different-source-id",
                    "source id mismatch"));
    assertEquals(
        "compileOptionsVerification must not be VERIFIED when SQLite runtime status is INCOMPATIBLE.",
        verifiedCompileOptions.getMessage());
  }

  private static SqliteRuntimeStatus runtimeStatus(EnvironmentSqliteDescriptor descriptor) {
    return descriptor.runtime().status();
  }

  private static @Nullable SqliteRuntimeProvenance runtimeProvenance(
      EnvironmentSqliteDescriptor descriptor) {
    return switch (descriptor.runtime()) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.runtimeProvenance();
      case EnvironmentSqliteDescriptor.FailedRuntime failed -> failed.runtimeProvenance();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.runtimeProvenance();
      case EnvironmentSqliteDescriptor.UnavailableRuntime _ -> null;
    };
  }

  private static @Nullable SqliteRuntimeTrustBasis runtimeTrustBasis(
      EnvironmentSqliteDescriptor descriptor) {
    return switch (descriptor.runtime()) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.runtimeTrustBasis();
      case EnvironmentSqliteDescriptor.FailedRuntime failed -> failed.runtimeTrustBasis();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.runtimeTrustBasis();
      case EnvironmentSqliteDescriptor.UnavailableRuntime _ -> null;
    };
  }

  private static @Nullable String loadedSqliteSourceId(EnvironmentSqliteDescriptor descriptor) {
    return switch (descriptor.runtime()) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.loadedSqliteSourceId();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.loadedSqliteSourceId();
      case EnvironmentSqliteDescriptor.FailedRuntime _,
          EnvironmentSqliteDescriptor.UnavailableRuntime _ ->
          null;
    };
  }

  private static @Nullable String runtimeIssue(EnvironmentSqliteDescriptor descriptor) {
    return switch (descriptor.runtime()) {
      case EnvironmentSqliteDescriptor.UnavailableRuntime unavailable -> unavailable.runtimeIssue();
      case EnvironmentSqliteDescriptor.FailedRuntime failed -> failed.runtimeIssue();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.runtimeIssue();
      case EnvironmentSqliteDescriptor.ReadyRuntime _ -> null;
    };
  }

  private static EnvironmentSqliteDescriptor descriptor(
      SqliteRuntimeStatus runtimeStatus,
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String runtimeIssue) {
    SqliteCompileOptionsVerificationStatus compileOptionsVerification =
        switch (runtimeStatus) {
          case READY -> SqliteCompileOptionsVerificationStatus.VERIFIED;
          case UNAVAILABLE, FAILED, INCOMPATIBLE ->
              SqliteCompileOptionsVerificationStatus.NOT_VERIFIED;
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
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String runtimeIssue) {
    return new EnvironmentSqliteDescriptor(
        SqliteLibraryMode.MANAGED_ONLY,
        "fingrind.sqlite.bundle.home",
        List.of("THREADSAFE=1", "SECURE_DELETE"),
        List.of("USE_URI"),
        true,
        "3.53.2",
        "2.3.5",
        ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
        EnvironmentSqliteDescriptor.runtime(
            compileOptionsVerification,
            runtimeStatus,
            runtimeProvenance,
            runtimeProvenance == null
                ? null
                : SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
            loadedLibraryPath,
            loadedSqliteVersion,
            loadedSqlite3mcVersion,
            loadedSqliteSourceId,
            runtimeIssue),
        null);
  }
}
