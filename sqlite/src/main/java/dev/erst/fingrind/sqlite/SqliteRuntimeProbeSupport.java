package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.runtime.SqliteRuntimeArtifactEvidence;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Probes the packaged SQLite runtime through one configured library target. */
final class SqliteRuntimeProbeSupport {
  private SqliteRuntimeProbeSupport() {}

  static SqliteRuntime.Probe probeConfiguredTarget(
      Supplier<SqliteLibraryTarget> configuredLibraryTargetSupplier) {
    return probeConfiguredTarget(
        configuredLibraryTargetSupplier, SqliteNativeAccessGate.runtimeModule());
  }

  static SqliteRuntime.Probe probeConfiguredTarget(
      Supplier<SqliteLibraryTarget> configuredLibraryTargetSupplier, Module nativeAccessModule) {
    return probeConfiguredTarget(
        configuredLibraryTargetSupplier,
        nativeAccessModule,
        SqliteNativeAccessGate.isEnabled(nativeAccessModule));
  }

  static SqliteRuntime.Probe probeConfiguredTarget(
      Supplier<SqliteLibraryTarget> configuredLibraryTargetSupplier,
      Module nativeAccessModule,
      boolean nativeAccessEnabled) {
    Objects.requireNonNull(nativeAccessModule, "nativeAccessModule");
    SqliteLibraryTarget configuredLibraryTarget;
    try {
      configuredLibraryTarget = configuredLibraryTargetSupplier.get();
    } catch (RuntimeException | Error throwable) {
      return new SqliteRuntime.Probe(
          SqliteRuntime.LIBRARY_MODE,
          SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
          SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
          SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          SqliteRuntime.Status.UNAVAILABLE,
          null,
          null,
          null,
          null,
          null,
          null,
          SqliteRuntime.failureDetail(throwable),
          null);
    }
    if (!nativeAccessEnabled) {
      return new SqliteRuntime.Probe(
          configuredLibraryTarget.mode(),
          SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
          SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
          SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          SqliteRuntime.Status.UNAVAILABLE,
          null,
          null,
          null,
          null,
          null,
          null,
          SqliteNativeAccessGate.failureMessage(nativeAccessModule),
          null);
    }
    return probe(
        () -> configuredLibraryTarget.mode(),
        () -> configuredLibraryTarget.provenance(),
        () -> configuredLibraryTarget.lookupTarget(),
        SqliteNativeRuntimeMetadata::sqliteVersion,
        SqliteNativeRuntimeMetadata::sqlite3MultipleCiphersVersion,
        SqliteNativeRuntimeMetadata::sqliteSourceId,
        SqliteRuntime::failureDetail);
  }

  static SqliteRuntime.Probe probe(
      Supplier<String> libraryModeSupplier,
      Supplier<SqliteRuntimeProvenance> runtimeProvenanceSupplier,
      Supplier<String> loadedLibraryPathSupplier,
      Supplier<String> sqliteVersionSupplier,
      Supplier<String> sqlite3MultipleCiphersVersionSupplier,
      Supplier<String> sqliteSourceIdSupplier,
      Function<Throwable, String> failureDetail) {
    Objects.requireNonNull(libraryModeSupplier, "libraryModeSupplier");
    Objects.requireNonNull(runtimeProvenanceSupplier, "runtimeProvenanceSupplier");
    Objects.requireNonNull(loadedLibraryPathSupplier, "loadedLibraryPathSupplier");
    Objects.requireNonNull(sqliteVersionSupplier, "sqliteVersionSupplier");
    Objects.requireNonNull(
        sqlite3MultipleCiphersVersionSupplier, "sqlite3MultipleCiphersVersionSupplier");
    Objects.requireNonNull(sqliteSourceIdSupplier, "sqliteSourceIdSupplier");
    Objects.requireNonNull(failureDetail, "failureDetail");

    String libraryMode = libraryModeSupplier.get();
    SqliteRuntimeProvenance runtimeProvenance = runtimeProvenanceSupplier.get();
    String loadedLibraryPath =
        SqliteRuntime.publicLoadedLibraryPath(loadedLibraryPathSupplier.get());
    SqliteRuntimeArtifactEvidence runtimeArtifactEvidence =
        SqliteRuntime.artifactEvidence(loadedLibraryPathSupplier.get());
    String loadedSqliteVersion = null;
    String loadedSqlite3mcVersion = null;
    String loadedSqliteSourceId = null;
    try {
      loadedSqliteVersion = sqliteVersionSupplier.get();
      loadedSqlite3mcVersion = sqlite3MultipleCiphersVersionSupplier.get();
      loadedSqliteSourceId = sqliteSourceIdSupplier.get();
      return new SqliteRuntime.Probe(
          libraryMode,
          SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
          SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
          SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.VERIFIED,
          SqliteRuntime.Status.READY,
          runtimeProvenance,
          SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
          loadedLibraryPath,
          loadedSqliteVersion,
          loadedSqlite3mcVersion,
          loadedSqliteSourceId,
          null,
          runtimeArtifactEvidence);
    } catch (UnsupportedSqliteVersionException exception) {
      return incompatibleProbe(
          libraryMode,
          runtimeProvenance,
          loadedLibraryPath,
          exception.loadedVersion(),
          exception.loadedSqlite3mcVersion(),
          exception.loadedSourceId(),
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          failureDetail.apply(exception),
          runtimeArtifactEvidence);
    } catch (UnsupportedSqliteMultipleCiphersVersionException exception) {
      return incompatibleProbe(
          libraryMode,
          runtimeProvenance,
          loadedLibraryPath,
          exception.loadedSqliteVersion(),
          exception.loadedVersion(),
          exception.loadedSourceId(),
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          failureDetail.apply(exception),
          runtimeArtifactEvidence);
    } catch (UnsupportedSqliteSourceIdException exception) {
      return incompatibleProbe(
          libraryMode,
          runtimeProvenance,
          loadedLibraryPath,
          exception.loadedSqliteVersion(),
          exception.loadedSqlite3mcVersion(),
          exception.loadedSourceId(),
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          failureDetail.apply(exception),
          runtimeArtifactEvidence);
    } catch (UnsupportedSqliteCompileOptionsException exception) {
      return incompatibleProbe(
          libraryMode,
          runtimeProvenance,
          loadedLibraryPath,
          exception.loadedSqliteVersion(),
          exception.loadedSqlite3mcVersion(),
          exception.loadedSourceId(),
          SqliteCompileOptionsVerificationStatus.FAILED,
          failureDetail.apply(exception),
          runtimeArtifactEvidence);
    } catch (RuntimeException | Error throwable) {
      return new SqliteRuntime.Probe(
          libraryMode,
          SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
          SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
          SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          SqliteRuntime.Status.FAILED,
          runtimeProvenance,
          SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
          loadedLibraryPath,
          loadedSqliteVersion,
          loadedSqlite3mcVersion,
          loadedSqliteSourceId,
          failureDetail.apply(throwable),
          runtimeArtifactEvidence);
    }
  }

  private static SqliteRuntime.Probe incompatibleProbe(
      String libraryMode,
      SqliteRuntimeProvenance runtimeProvenance,
      String loadedLibraryPath,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String loadedSqliteSourceId,
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      String issue,
      @Nullable SqliteRuntimeArtifactEvidence runtimeArtifactEvidence) {
    return new SqliteRuntime.Probe(
        libraryMode,
        SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
        SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        compileOptionsVerification,
        SqliteRuntime.Status.INCOMPATIBLE,
        runtimeProvenance,
        SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
        loadedLibraryPath,
        loadedSqliteVersion,
        loadedSqlite3mcVersion,
        loadedSqliteSourceId,
        issue,
        runtimeArtifactEvidence);
  }
}
