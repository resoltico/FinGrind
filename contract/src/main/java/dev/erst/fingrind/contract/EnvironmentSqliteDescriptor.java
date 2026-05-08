package dev.erst.fingrind.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.SqliteLibraryMode;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Descriptor for the managed SQLite runtime contract exposed by the active environment. */
public record EnvironmentSqliteDescriptor(
    SqliteLibraryMode libraryMode,
    String libraryEnvironmentVariable,
    String bundleHomeSystemProperty,
    List<String> requiredCompileOptions,
    List<String> forbiddenCompileOptions,
    boolean requiresSecureMemorySupport,
    String requiredMinimumSqliteVersion,
    String requiredSqlite3mcVersion,
    String requiredSqliteSourceId,
    RuntimeState runtime)
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
    forbiddenCompileOptions =
        ContractDescriptorValidation.copyList(forbiddenCompileOptions, "forbiddenCompileOptions");
    requiredMinimumSqliteVersion =
        ContractDescriptorValidation.requireText(
            requiredMinimumSqliteVersion, "requiredMinimumSqliteVersion");
    requiredSqlite3mcVersion =
        ContractDescriptorValidation.requireText(
            requiredSqlite3mcVersion, "requiredSqlite3mcVersion");
    requiredSqliteSourceId =
        ContractDescriptorValidation.requireText(requiredSqliteSourceId, "requiredSqliteSourceId");
    runtime = ContractDescriptorValidation.requireValue(runtime, "runtime");
  }

  /** Builds one validated runtime observation from the raw SQLite probe tuple. */
  public static RuntimeState runtime(
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      SqliteRuntimeStatus runtimeStatus,
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable SqliteRuntimeTrustBasis runtimeTrustBasis,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String runtimeIssue) {
    return SqliteRuntimeStateValidator.validate(
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

  /** Explicit runtime-state family for the active SQLite environment. */
  public sealed interface RuntimeState
      permits ReadyRuntime, UnavailableRuntime, FailedRuntime, IncompatibleRuntime {
    /** Returns the canonical runtime status for this explicit runtime state. */
    @JsonProperty("status")
    SqliteRuntimeStatus status();

    /** Returns the compile-options verification result that belongs to this runtime state. */
    @JsonProperty("compileOptionsVerification")
    SqliteCompileOptionsVerificationStatus compileOptionsVerification();
  }

  /** Runtime state for one verified ready SQLite environment. */
  public record ReadyRuntime(
      SqliteRuntimeProvenance runtimeProvenance,
      SqliteRuntimeTrustBasis runtimeTrustBasis,
      String loadedLibraryPath,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String loadedSqliteSourceId)
      implements RuntimeState {
    public ReadyRuntime {
      Objects.requireNonNull(runtimeProvenance, "runtimeProvenance");
      Objects.requireNonNull(runtimeTrustBasis, "runtimeTrustBasis");
      loadedLibraryPath =
          ContractDescriptorValidation.requireText(loadedLibraryPath, "loadedLibraryPath");
      loadedSqliteVersion =
          ContractDescriptorValidation.requireText(loadedSqliteVersion, "loadedSqliteVersion");
      loadedSqlite3mcVersion =
          ContractDescriptorValidation.requireText(
              loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
      loadedSqliteSourceId =
          ContractDescriptorValidation.requireText(loadedSqliteSourceId, "loadedSqliteSourceId");
    }

    @Override
    public SqliteRuntimeStatus status() {
      return SqliteRuntimeStatus.READY;
    }

    @Override
    public SqliteCompileOptionsVerificationStatus compileOptionsVerification() {
      return SqliteCompileOptionsVerificationStatus.VERIFIED;
    }
  }

  /** Runtime state for one unavailable SQLite environment with no loaded library. */
  public record UnavailableRuntime(String runtimeIssue) implements RuntimeState {
    public UnavailableRuntime {
      runtimeIssue = ContractDescriptorValidation.requireText(runtimeIssue, "runtimeIssue");
    }

    @Override
    public SqliteRuntimeStatus status() {
      return SqliteRuntimeStatus.UNAVAILABLE;
    }

    @Override
    public SqliteCompileOptionsVerificationStatus compileOptionsVerification() {
      return SqliteCompileOptionsVerificationStatus.NOT_VERIFIED;
    }
  }

  /** Runtime state for one loaded SQLite environment that failed before compatibility proof. */
  public record FailedRuntime(
      SqliteRuntimeProvenance runtimeProvenance,
      SqliteRuntimeTrustBasis runtimeTrustBasis,
      String loadedLibraryPath,
      String runtimeIssue)
      implements RuntimeState {
    public FailedRuntime {
      Objects.requireNonNull(runtimeProvenance, "runtimeProvenance");
      Objects.requireNonNull(runtimeTrustBasis, "runtimeTrustBasis");
      loadedLibraryPath =
          ContractDescriptorValidation.requireText(loadedLibraryPath, "loadedLibraryPath");
      runtimeIssue = ContractDescriptorValidation.requireText(runtimeIssue, "runtimeIssue");
    }

    @Override
    public SqliteRuntimeStatus status() {
      return SqliteRuntimeStatus.FAILED;
    }

    @Override
    public SqliteCompileOptionsVerificationStatus compileOptionsVerification() {
      return SqliteCompileOptionsVerificationStatus.NOT_VERIFIED;
    }
  }

  /** Runtime state for one loaded SQLite environment that failed compatibility checks. */
  public record IncompatibleRuntime(
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      SqliteRuntimeProvenance runtimeProvenance,
      SqliteRuntimeTrustBasis runtimeTrustBasis,
      String loadedLibraryPath,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String loadedSqliteSourceId,
      String runtimeIssue)
      implements RuntimeState {
    public IncompatibleRuntime {
      compileOptionsVerification =
          ContractDescriptorValidation.requireValue(
              compileOptionsVerification, "compileOptionsVerification");
      if (compileOptionsVerification == SqliteCompileOptionsVerificationStatus.VERIFIED) {
        throw new IllegalArgumentException(
            "compileOptionsVerification must not be VERIFIED when SQLite runtime status is INCOMPATIBLE.");
      }
      Objects.requireNonNull(runtimeProvenance, "runtimeProvenance");
      Objects.requireNonNull(runtimeTrustBasis, "runtimeTrustBasis");
      loadedLibraryPath =
          ContractDescriptorValidation.requireText(loadedLibraryPath, "loadedLibraryPath");
      loadedSqliteVersion =
          ContractDescriptorValidation.requireText(loadedSqliteVersion, "loadedSqliteVersion");
      loadedSqlite3mcVersion =
          ContractDescriptorValidation.requireText(
              loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
      loadedSqliteSourceId =
          ContractDescriptorValidation.requireText(loadedSqliteSourceId, "loadedSqliteSourceId");
      runtimeIssue = ContractDescriptorValidation.requireText(runtimeIssue, "runtimeIssue");
    }

    @Override
    public SqliteRuntimeStatus status() {
      return SqliteRuntimeStatus.INCOMPATIBLE;
    }
  }
}
