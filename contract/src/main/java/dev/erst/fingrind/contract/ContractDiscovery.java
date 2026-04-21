package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
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
        ArtifactOutputDescriptor.class,
        CommandDescriptor.class,
        ExitCodeDescriptor.class,
        EnvironmentDescriptor.class);
  }

  /** Stable identity fields that appear on discovery descriptors. */
  public record ApplicationIdentity(String application, String version, String description) {
    /** Validates one stable application identity descriptor. */
    public ApplicationIdentity {
      application = ContractDescriptorValidation.requireText(application, "application");
      version = ContractDescriptorValidation.requireText(version, "version");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

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
      EnvironmentDescriptor environment) {
    /** Validates one help descriptor payload. */
    public HelpDescriptor {
      application = ContractDescriptorValidation.requireText(application, "application");
      version = ContractDescriptorValidation.requireText(version, "version");
      description = ContractDescriptorValidation.requireText(description, "description");
      usage = ContractDescriptorValidation.copyList(usage, "usage");
      bookModel = ContractDescriptorValidation.requireValue(bookModel, "bookModel");
      commands = ContractDescriptorValidation.copyList(commands, "commands");
      quickStart = ContractDescriptorValidation.copyList(quickStart, "quickStart");
      exitCodes = ContractDescriptorValidation.copyList(exitCodes, "exitCodes");
      preflight = ContractDescriptorValidation.requireValue(preflight, "preflight");
      currencyModel = ContractDescriptorValidation.requireValue(currencyModel, "currencyModel");
      environment = ContractDescriptorValidation.requireValue(environment, "environment");
    }
  }

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
      String timestamp) {
    /** Validates one capabilities descriptor payload. */
    public CapabilitiesDescriptor {
      application = ContractDescriptorValidation.requireText(application, "application");
      version = ContractDescriptorValidation.requireText(version, "version");
      storage = ContractDescriptorValidation.copyList(storage, "storage");
      bookBoundary = ContractDescriptorValidation.requireText(bookBoundary, "bookBoundary");
      discoveryCommands =
          ContractDescriptorValidation.copyList(discoveryCommands, "discoveryCommands");
      administrationCommands =
          ContractDescriptorValidation.copyList(administrationCommands, "administrationCommands");
      queryCommands = ContractDescriptorValidation.copyList(queryCommands, "queryCommands");
      writeCommands = ContractDescriptorValidation.copyList(writeCommands, "writeCommands");
      requestInput = ContractDescriptorValidation.requireValue(requestInput, "requestInput");
      requestShapes = ContractDescriptorValidation.requireValue(requestShapes, "requestShapes");
      responseModel = ContractDescriptorValidation.requireValue(responseModel, "responseModel");
      planExecution = ContractDescriptorValidation.requireValue(planExecution, "planExecution");
      audit = ContractDescriptorValidation.requireValue(audit, "audit");
      accountRegistry =
          ContractDescriptorValidation.requireValue(accountRegistry, "accountRegistry");
      reversals = ContractDescriptorValidation.requireValue(reversals, "reversals");
      preflightSemantics =
          ContractDescriptorValidation.requireText(preflightSemantics, "preflightSemantics");
      preflight = ContractDescriptorValidation.requireValue(preflight, "preflight");
      currencyModel = ContractDescriptorValidation.requireValue(currencyModel, "currencyModel");
      environment = ContractDescriptorValidation.requireValue(environment, "environment");
      timestamp = ContractDescriptorValidation.requireText(timestamp, "timestamp");
    }
  }

  /** Descriptor for the version payload. */
  public record VersionDescriptor(String application, String version, String description) {
    /** Validates one version descriptor payload. */
    public VersionDescriptor {
      application = ContractDescriptorValidation.requireText(application, "application");
      version = ContractDescriptorValidation.requireText(version, "version");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

  /** Descriptor for one non-stdout export artifact supported by a command. */
  public record ArtifactOutputDescriptor(String format, String option, String description) {
    /** Validates one artifact-output descriptor payload. */
    public ArtifactOutputDescriptor {
      format = ContractDescriptorValidation.requireText(format, "format");
      option = ContractDescriptorValidation.requireText(option, "option");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

  /** Descriptor for one advertised CLI command. */
  public record CommandDescriptor(
      String name,
      List<String> aliases,
      List<String> options,
      String executionMode,
      List<String> outputModes,
      List<ArtifactOutputDescriptor> artifactOutputs,
      String summary) {
    /** Validates one command descriptor payload. */
    public CommandDescriptor {
      name = ContractDescriptorValidation.requireText(name, "name");
      aliases = ContractDescriptorValidation.copyList(aliases, "aliases");
      options = ContractDescriptorValidation.copyList(options, "options");
      executionMode = ContractDescriptorValidation.requireText(executionMode, "executionMode");
      outputModes = ContractDescriptorValidation.copyList(outputModes, "outputModes");
      artifactOutputs = ContractDescriptorValidation.copyList(artifactOutputs, "artifactOutputs");
      summary = ContractDescriptorValidation.requireText(summary, "summary");
    }
  }

  /** Descriptor for one process exit code. */
  public record ExitCodeDescriptor(int code, String meaning) {
    /** Validates one exit-code descriptor payload. */
    public ExitCodeDescriptor {
      if (code < 0) {
        throw new IllegalArgumentException("code must not be negative.");
      }
      meaning = ContractDescriptorValidation.requireText(meaning, "meaning");
    }
  }

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
      @Nullable String sqliteRuntimeIssue) {
    /** Validates one runtime environment descriptor payload. */
    public EnvironmentDescriptor {
      runtimeDistribution =
          ContractDescriptorValidation.requireText(runtimeDistribution, "runtimeDistribution");
      publicCliDistribution =
          ContractDescriptorValidation.requireText(publicCliDistribution, "publicCliDistribution");
      supportedPublicCliBundleTargets =
          ContractDescriptorValidation.copyList(
              supportedPublicCliBundleTargets, "supportedPublicCliBundleTargets");
      unsupportedPublicCliOperatingSystems =
          ContractDescriptorValidation.copyList(
              unsupportedPublicCliOperatingSystems, "unsupportedPublicCliOperatingSystems");
      sourceCheckoutJava =
          ContractDescriptorValidation.requireText(sourceCheckoutJava, "sourceCheckoutJava");
      storageDriver = ContractDescriptorValidation.requireText(storageDriver, "storageDriver");
      storageEngine = ContractDescriptorValidation.requireText(storageEngine, "storageEngine");
      bookProtectionMode =
          ContractDescriptorValidation.requireText(bookProtectionMode, "bookProtectionMode");
      defaultBookCipher =
          ContractDescriptorValidation.requireText(defaultBookCipher, "defaultBookCipher");
      sqliteLibraryMode =
          ContractDescriptorValidation.requireText(sqliteLibraryMode, "sqliteLibraryMode");
      sqliteLibraryEnvironmentVariable =
          ContractDescriptorValidation.requireText(
              sqliteLibraryEnvironmentVariable, "sqliteLibraryEnvironmentVariable");
      sqliteLibraryBundleHomeSystemProperty =
          ContractDescriptorValidation.requireText(
              sqliteLibraryBundleHomeSystemProperty, "sqliteLibraryBundleHomeSystemProperty");
      requiredSqliteCompileOptions =
          ContractDescriptorValidation.copyList(
              requiredSqliteCompileOptions, "requiredSqliteCompileOptions");
      requiredMinimumSqliteVersion =
          ContractDescriptorValidation.requireText(
              requiredMinimumSqliteVersion, "requiredMinimumSqliteVersion");
      requiredSqlite3mcVersion =
          ContractDescriptorValidation.requireText(
              requiredSqlite3mcVersion, "requiredSqlite3mcVersion");
      sqliteRuntimeStatus =
          ContractDescriptorValidation.requireText(sqliteRuntimeStatus, "sqliteRuntimeStatus");
      loadedSqliteVersion =
          ContractDescriptorValidation.requireOptionalText(
              loadedSqliteVersion, "loadedSqliteVersion");
      loadedSqlite3mcVersion =
          ContractDescriptorValidation.requireOptionalText(
              loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
      sqliteRuntimeIssue =
          ContractDescriptorValidation.requireOptionalText(
              sqliteRuntimeIssue, "sqliteRuntimeIssue");
    }
  }
}
