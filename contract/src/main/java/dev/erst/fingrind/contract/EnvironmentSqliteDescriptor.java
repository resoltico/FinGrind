package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.SqliteLibraryMode;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
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
    @Nullable SqliteRuntimeTrustBasis runtimeTrustBasis,
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
    runtimeTrustBasis =
        SqliteRuntimeStateValidator.validate(
            compileOptionsVerification,
            runtimeStatus,
            runtimeProvenance,
            runtimeTrustBasis,
            loadedLibraryPath,
            loadedSqliteVersion,
            loadedSqlite3mcVersion,
            loadedSqliteSourceId,
            runtimeIssue);
  }
}
