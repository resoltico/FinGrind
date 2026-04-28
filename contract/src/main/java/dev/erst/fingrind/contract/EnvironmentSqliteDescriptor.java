package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.SqliteLibraryMode;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Descriptor for the managed SQLite runtime contract exposed by the active environment. */
public record EnvironmentSqliteDescriptor(
    SqliteLibraryMode libraryMode,
    String libraryEnvironmentVariable,
    String bundleHomeSystemProperty,
    List<String> requiredCompileOptions,
    SqliteCompileOptionsVerificationStatus compileOptionsVerification,
    String requiredMinimumSqliteVersion,
    String requiredSqlite3mcVersion,
    String requiredSqliteSourceId,
    SqliteRuntimeStatus runtimeStatus,
    @Nullable SqliteRuntimeProvenance runtimeProvenance,
    @Nullable String loadedLibraryPath,
    @Nullable String loadedSqliteVersion,
    @Nullable String loadedSqlite3mcVersion,
    @Nullable String loadedSqliteSourceId,
    @Nullable String runtimeIssue)
    implements ContractDiscoveryDescriptor {
  /** Validates one environment SQLite descriptor payload. */
  public EnvironmentSqliteDescriptor {
    libraryMode = ContractDescriptorValidation.requireValue(libraryMode, "libraryMode");
    libraryEnvironmentVariable =
        ContractDescriptorValidation.requireText(
            libraryEnvironmentVariable, "libraryEnvironmentVariable");
    bundleHomeSystemProperty =
        ContractDescriptorValidation.requireText(
            bundleHomeSystemProperty, "bundleHomeSystemProperty");
    requiredCompileOptions =
        ContractDescriptorValidation.copyList(requiredCompileOptions, "requiredCompileOptions");
    compileOptionsVerification =
        ContractDescriptorValidation.requireValue(
            compileOptionsVerification, "compileOptionsVerification");
    requiredMinimumSqliteVersion =
        ContractDescriptorValidation.requireText(
            requiredMinimumSqliteVersion, "requiredMinimumSqliteVersion");
    requiredSqlite3mcVersion =
        ContractDescriptorValidation.requireText(
            requiredSqlite3mcVersion, "requiredSqlite3mcVersion");
    requiredSqliteSourceId =
        ContractDescriptorValidation.requireText(requiredSqliteSourceId, "requiredSqliteSourceId");
    runtimeStatus = ContractDescriptorValidation.requireValue(runtimeStatus, "runtimeStatus");
    loadedLibraryPath =
        ContractDescriptorValidation.requireOptionalText(loadedLibraryPath, "loadedLibraryPath");
    loadedSqliteVersion =
        ContractDescriptorValidation.requireOptionalText(
            loadedSqliteVersion, "loadedSqliteVersion");
    loadedSqlite3mcVersion =
        ContractDescriptorValidation.requireOptionalText(
            loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    loadedSqliteSourceId =
        ContractDescriptorValidation.requireOptionalText(
            loadedSqliteSourceId, "loadedSqliteSourceId");
    runtimeIssue = ContractDescriptorValidation.requireOptionalText(runtimeIssue, "runtimeIssue");
    if (runtimeStatus == SqliteRuntimeStatus.READY) {
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
    } else if (runtimeStatus == SqliteRuntimeStatus.UNAVAILABLE) {
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
    } else {
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
    }
  }
}
