package dev.erst.fingrind.contract;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Discovery descriptor namespace for the public machine-readable CLI contract. */
public final class ContractDiscovery {
  private ContractDiscovery() {}

  /** Returns the descriptor record types owned by this namespace. */
  public static List<Class<?>> descriptorTypes() {
    return List.of(
        ApplicationIdentity.class,
        HelpDescriptor.class,
        CapabilitiesDescriptor.class,
        VersionDescriptor.class,
        CommandDescriptor.class,
        ExitCodeDescriptor.class,
        EnvironmentDescriptor.class);
  }

  /** Stable identity fields that appear on discovery descriptors. */
  public record ApplicationIdentity(String application, String version, String description) {}

  /** Descriptor for the help payload. */
  public record HelpDescriptor(
      String application,
      String version,
      String description,
      List<String> usage,
      ContractResponse.BookModelDescriptor bookModel,
      List<CommandDescriptor> commands,
      List<String> quickStart,
      List<ExitCodeDescriptor> exitCodes,
      ContractResponse.PreflightDescriptor preflight,
      ContractResponse.CurrencyDescriptor currencyModel,
      EnvironmentDescriptor environment) {}

  /** Descriptor for the capabilities payload. */
  public record CapabilitiesDescriptor(
      String application,
      String version,
      List<String> storage,
      String bookBoundary,
      List<String> discoveryCommands,
      List<String> administrationCommands,
      List<String> queryCommands,
      List<String> writeCommands,
      ContractRequestShapes.RequestInputDescriptor requestInput,
      ContractRequestShapes.RequestShapesDescriptor requestShapes,
      ContractResponse.ResponseModelDescriptor responseModel,
      ContractResponse.PlanExecutionDescriptor planExecution,
      ContractResponse.AuditDescriptor audit,
      ContractResponse.AccountRegistryDescriptor accountRegistry,
      ContractResponse.ReversalDescriptor reversals,
      String preflightSemantics,
      ContractResponse.PreflightDescriptor preflight,
      ContractResponse.CurrencyDescriptor currencyModel,
      EnvironmentDescriptor environment,
      String timestamp) {}

  /** Descriptor for the version payload. */
  public record VersionDescriptor(String application, String version, String description) {}

  /** Descriptor for one advertised CLI command. */
  public record CommandDescriptor(
      String name, List<String> aliases, List<String> options, String output, String summary) {}

  /** Descriptor for one process exit code. */
  public record ExitCodeDescriptor(int code, String meaning) {}

  /** Descriptor for the active SQLite runtime environment. */
  public record EnvironmentDescriptor(
      String runtimeDistribution,
      String publicCliDistribution,
      List<String> supportedPublicCliBundleTargets,
      List<String> unsupportedPublicCliOperatingSystems,
      String sourceCheckoutJava,
      String storageDriver,
      String storageEngine,
      String bookProtectionMode,
      String defaultBookCipher,
      String sqliteLibraryMode,
      String sqliteLibraryEnvironmentVariable,
      String sqliteLibraryBundleHomeSystemProperty,
      List<String> requiredSqliteCompileOptions,
      boolean sqliteCompileOptionsVerified,
      String requiredMinimumSqliteVersion,
      String requiredSqlite3mcVersion,
      String sqliteRuntimeStatus,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String sqliteRuntimeIssue) {}
}
