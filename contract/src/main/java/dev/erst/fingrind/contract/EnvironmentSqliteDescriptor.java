package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Descriptor for the managed SQLite runtime contract exposed by the active environment. */
public record EnvironmentSqliteDescriptor(
    String libraryMode,
    String libraryEnvironmentVariable,
    String bundleHomeSystemProperty,
    List<String> requiredCompileOptions,
    SqliteCompileOptionsVerificationStatus compileOptionsVerification,
    String requiredMinimumSqliteVersion,
    String requiredSqlite3mcVersion,
    String runtimeStatus,
    @Nullable String loadedSqliteVersion,
    @Nullable String loadedSqlite3mcVersion,
    @Nullable String runtimeIssue) {
  /** Validates one environment SQLite descriptor payload. */
  public EnvironmentSqliteDescriptor {
    libraryMode = ContractDescriptorValidation.requireText(libraryMode, "libraryMode");
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
    runtimeStatus = ContractDescriptorValidation.requireText(runtimeStatus, "runtimeStatus");
    loadedSqliteVersion =
        ContractDescriptorValidation.requireOptionalText(
            loadedSqliteVersion, "loadedSqliteVersion");
    loadedSqlite3mcVersion =
        ContractDescriptorValidation.requireOptionalText(
            loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    runtimeIssue = ContractDescriptorValidation.requireOptionalText(runtimeIssue, "runtimeIssue");
  }
}
