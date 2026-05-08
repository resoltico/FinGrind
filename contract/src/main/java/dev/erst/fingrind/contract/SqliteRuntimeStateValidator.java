package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared validation for the public SQLite runtime-state contract. */
public final class SqliteRuntimeStateValidator {
  private SqliteRuntimeStateValidator() {}

  /** Validates one SQLite runtime-state tuple and returns the explicit runtime variant. */
  public static EnvironmentSqliteDescriptor.RuntimeState validate(
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      SqliteRuntimeStatus runtimeStatus,
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable SqliteRuntimeTrustBasis runtimeTrustBasis,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String runtimeIssue) {
    Objects.requireNonNull(compileOptionsVerification, "compileOptionsVerification");
    Objects.requireNonNull(runtimeStatus, "runtimeStatus");

    SqliteRuntimeTrustBasis resolvedTrustBasis =
        normalizeTrustBasis(runtimeProvenance, runtimeTrustBasis);
    return switch (runtimeStatus) {
      case READY ->
          readyRuntime(
              compileOptionsVerification,
              runtimeProvenance,
              resolvedTrustBasis,
              loadedLibraryPath,
              loadedSqliteVersion,
              loadedSqlite3mcVersion,
              loadedSqliteSourceId,
              runtimeIssue);
      case UNAVAILABLE ->
          unavailableRuntime(
              compileOptionsVerification,
              runtimeProvenance,
              loadedLibraryPath,
              loadedSqliteVersion,
              loadedSqlite3mcVersion,
              loadedSqliteSourceId,
              runtimeIssue);
      case FAILED ->
          failedRuntime(
              compileOptionsVerification,
              runtimeProvenance,
              resolvedTrustBasis,
              loadedLibraryPath,
              loadedSqliteVersion,
              loadedSqlite3mcVersion,
              loadedSqliteSourceId,
              runtimeIssue);
      case INCOMPATIBLE ->
          incompatibleRuntime(
              compileOptionsVerification,
              runtimeProvenance,
              resolvedTrustBasis,
              loadedLibraryPath,
              loadedSqliteVersion,
              loadedSqlite3mcVersion,
              loadedSqliteSourceId,
              runtimeIssue);
    };
  }

  private static @Nullable SqliteRuntimeTrustBasis normalizeTrustBasis(
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable SqliteRuntimeTrustBasis runtimeTrustBasis) {
    if (runtimeProvenance == null) {
      if (runtimeTrustBasis != null) {
        throw new IllegalArgumentException(
            "runtimeTrustBasis must be absent when runtimeProvenance is absent.");
      }
      return null;
    }
    SqliteRuntimeTrustBasis expectedTrustBasis =
        SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance);
    if (runtimeTrustBasis == null) {
      return expectedTrustBasis;
    }
    if (runtimeTrustBasis != expectedTrustBasis) {
      throw new IllegalArgumentException(
          "runtimeTrustBasis must match runtimeProvenance " + runtimeProvenance.wireValue() + ".");
    }
    return runtimeTrustBasis;
  }

  private static EnvironmentSqliteDescriptor.ReadyRuntime readyRuntime(
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable SqliteRuntimeTrustBasis runtimeTrustBasis,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String runtimeIssue) {
    if (compileOptionsVerification != SqliteCompileOptionsVerificationStatus.VERIFIED) {
      throw new IllegalArgumentException(
          "compileOptionsVerification must be VERIFIED when SQLite runtime status is READY.");
    }
    if (runtimeProvenance == null) {
      throw new IllegalArgumentException(
          "runtimeProvenance is required when SQLite runtime status is READY.");
    }
    if (loadedLibraryPath == null) {
      throw new IllegalArgumentException(
          "loadedLibraryPath is required when SQLite runtime status is READY.");
    }
    if (loadedSqliteVersion == null
        || loadedSqlite3mcVersion == null
        || loadedSqliteSourceId == null) {
      throw new IllegalArgumentException(
          "Loaded SQLite version, SQLite3MC version, and source id are required when SQLite runtime status is READY.");
    }
    if (runtimeIssue != null) {
      throw new IllegalArgumentException(
          "runtimeIssue must be absent when SQLite runtime status is READY.");
    }
    return new EnvironmentSqliteDescriptor.ReadyRuntime(
        runtimeProvenance,
        Objects.requireNonNull(runtimeTrustBasis, "runtimeTrustBasis"),
        loadedLibraryPath,
        loadedSqliteVersion,
        loadedSqlite3mcVersion,
        loadedSqliteSourceId);
  }

  private static EnvironmentSqliteDescriptor.UnavailableRuntime unavailableRuntime(
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String runtimeIssue) {
    if (compileOptionsVerification != SqliteCompileOptionsVerificationStatus.NOT_VERIFIED) {
      throw new IllegalArgumentException(
          "compileOptionsVerification must be NOT_VERIFIED when SQLite runtime status is UNAVAILABLE.");
    }
    if (runtimeProvenance != null
        || loadedLibraryPath != null
        || loadedSqliteVersion != null
        || loadedSqlite3mcVersion != null
        || loadedSqliteSourceId != null) {
      throw new IllegalArgumentException(
          "Loaded SQLite provenance and version fields must be absent when SQLite runtime status is UNAVAILABLE.");
    }
    if (runtimeIssue == null) {
      throw new IllegalArgumentException(
          "runtimeIssue is required when SQLite runtime status is UNAVAILABLE.");
    }
    return new EnvironmentSqliteDescriptor.UnavailableRuntime(runtimeIssue);
  }

  private static EnvironmentSqliteDescriptor.FailedRuntime failedRuntime(
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable SqliteRuntimeTrustBasis runtimeTrustBasis,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String runtimeIssue) {
    if (compileOptionsVerification != SqliteCompileOptionsVerificationStatus.NOT_VERIFIED) {
      throw new IllegalArgumentException(
          "compileOptionsVerification must be NOT_VERIFIED when SQLite runtime status is FAILED.");
    }
    if (runtimeProvenance == null) {
      throw new IllegalArgumentException(
          "runtimeProvenance is required when SQLite runtime status is FAILED.");
    }
    if (loadedLibraryPath == null) {
      throw new IllegalArgumentException(
          "loadedLibraryPath is required when SQLite runtime status is FAILED.");
    }
    if (loadedSqliteVersion != null
        || loadedSqlite3mcVersion != null
        || loadedSqliteSourceId != null) {
      throw new IllegalArgumentException(
          "Loaded SQLite version, SQLite3MC version, and source id must be absent when SQLite runtime status is FAILED.");
    }
    if (runtimeIssue == null) {
      throw new IllegalArgumentException(
          "runtimeIssue is required when SQLite runtime status is FAILED.");
    }
    return new EnvironmentSqliteDescriptor.FailedRuntime(
        runtimeProvenance,
        Objects.requireNonNull(runtimeTrustBasis, "runtimeTrustBasis"),
        loadedLibraryPath,
        runtimeIssue);
  }

  private static EnvironmentSqliteDescriptor.IncompatibleRuntime incompatibleRuntime(
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable SqliteRuntimeTrustBasis runtimeTrustBasis,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String runtimeIssue) {
    if (compileOptionsVerification == SqliteCompileOptionsVerificationStatus.VERIFIED) {
      throw new IllegalArgumentException(
          "compileOptionsVerification must not be VERIFIED when SQLite runtime status is INCOMPATIBLE.");
    }
    if (runtimeProvenance == null) {
      throw new IllegalArgumentException(
          "runtimeProvenance is required when SQLite runtime status is INCOMPATIBLE.");
    }
    if (loadedLibraryPath == null
        || loadedSqliteVersion == null
        || loadedSqlite3mcVersion == null
        || loadedSqliteSourceId == null
        || runtimeIssue == null) {
      throw new IllegalArgumentException(
          "Loaded SQLite provenance, version, source id, and runtimeIssue are required when SQLite runtime status is INCOMPATIBLE.");
    }
    return new EnvironmentSqliteDescriptor.IncompatibleRuntime(
        compileOptionsVerification,
        runtimeProvenance,
        Objects.requireNonNull(runtimeTrustBasis, "runtimeTrustBasis"),
        loadedLibraryPath,
        loadedSqliteVersion,
        loadedSqlite3mcVersion,
        loadedSqliteSourceId,
        runtimeIssue);
  }
}
