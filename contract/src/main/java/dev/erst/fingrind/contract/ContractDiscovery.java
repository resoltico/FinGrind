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
        StorageSurfaceDescriptor.class,
        CommandCatalogDescriptor.class,
        VersionDescriptor.class,
        ArtifactOutputDescriptor.class,
        CommandDescriptor.class,
        ExitCodeDescriptor.class,
        EnvironmentDistributionDescriptor.class,
        EnvironmentStorageDescriptor.class,
        EnvironmentSqliteDescriptor.class,
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
      StorageSurfaceDescriptor storage,
      CommandCatalogDescriptor commands,
      ContractRequestShapes.RequestInputDescriptor requestInput,
      ContractRequestShapes.RequestShapesDescriptor requestShapes,
      ContractResponse.ResponseModelDescriptor responseModel,
      ContractResponse.PlanExecutionDescriptor planExecution,
      ContractResponse.AuditDescriptor audit,
      ContractResponse.AccountRegistryDescriptor accountRegistry,
      ContractResponse.ReversalDescriptor reversals,
      ContractResponse.PreflightDescriptor preflight,
      ContractResponse.CurrencyDescriptor currencyModel,
      EnvironmentDescriptor environment,
      String timestamp) {
    /** Validates one capabilities descriptor payload. */
    public CapabilitiesDescriptor {
      application = ContractDescriptorValidation.requireText(application, "application");
      version = ContractDescriptorValidation.requireText(version, "version");
      storage = ContractDescriptorValidation.requireValue(storage, "storage");
      commands = ContractDescriptorValidation.requireValue(commands, "commands");
      requestInput = ContractDescriptorValidation.requireValue(requestInput, "requestInput");
      requestShapes = ContractDescriptorValidation.requireValue(requestShapes, "requestShapes");
      responseModel = ContractDescriptorValidation.requireValue(responseModel, "responseModel");
      planExecution = ContractDescriptorValidation.requireValue(planExecution, "planExecution");
      audit = ContractDescriptorValidation.requireValue(audit, "audit");
      accountRegistry =
          ContractDescriptorValidation.requireValue(accountRegistry, "accountRegistry");
      reversals = ContractDescriptorValidation.requireValue(reversals, "reversals");
      preflight = ContractDescriptorValidation.requireValue(preflight, "preflight");
      currencyModel = ContractDescriptorValidation.requireValue(currencyModel, "currencyModel");
      environment = ContractDescriptorValidation.requireValue(environment, "environment");
      timestamp = ContractDescriptorValidation.requireText(timestamp, "timestamp");
    }
  }

  /** Descriptor for the storage surface published by the CLI capabilities contract. */
  public record StorageSurfaceDescriptor(List<String> engines, String bookBoundary) {
    /** Validates one storage-surface descriptor payload. */
    public StorageSurfaceDescriptor {
      engines = ContractDescriptorValidation.copyList(engines, "engines");
      bookBoundary = ContractDescriptorValidation.requireText(bookBoundary, "bookBoundary");
    }
  }

  /** Descriptor for the grouped command catalog published by the CLI capabilities contract. */
  public record CommandCatalogDescriptor(
      List<String> discovery, List<String> administration, List<String> query, List<String> write) {
    /** Validates one command-catalog descriptor payload. */
    public CommandCatalogDescriptor {
      discovery = ContractDescriptorValidation.copyList(discovery, "discovery");
      administration = ContractDescriptorValidation.copyList(administration, "administration");
      query = ContractDescriptorValidation.copyList(query, "query");
      write = ContractDescriptorValidation.copyList(write, "write");
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

  /** Stable verification states for the required SQLite compile-option contract. */
  public enum SqliteCompileOptionsVerificationStatus {
    VERIFIED("verified"),
    NOT_VERIFIED("not-verified");

    private final String wireValue;

    SqliteCompileOptionsVerificationStatus(String wireValue) {
      this.wireValue = ContractDescriptorValidation.requireText(wireValue, "wireValue");
    }

    /** Returns the stable public wire value for this verification state. */
    public String wireValue() {
      return wireValue;
    }

    @Override
    public String toString() {
      return wireValue;
    }
  }

  /** Descriptor for the public CLI distribution and runtime packaging contract. */
  public record EnvironmentDistributionDescriptor(
      String runtimeDistribution,
      String publicCliDistribution,
      List<String> supportedPublicCliBundleTargets,
      List<String> unsupportedPublicCliOperatingSystems,
      String sourceCheckoutJava) {
    /** Validates one distribution descriptor payload. */
    public EnvironmentDistributionDescriptor {
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
    }
  }

  /** Descriptor for the storage engine exposed by the active runtime environment. */
  public record EnvironmentStorageDescriptor(
      String storageDriver,
      String storageEngine,
      String bookProtectionMode,
      String defaultBookCipher) {
    /** Validates one environment storage descriptor payload. */
    public EnvironmentStorageDescriptor {
      storageDriver = ContractDescriptorValidation.requireText(storageDriver, "storageDriver");
      storageEngine = ContractDescriptorValidation.requireText(storageEngine, "storageEngine");
      bookProtectionMode =
          ContractDescriptorValidation.requireText(bookProtectionMode, "bookProtectionMode");
      defaultBookCipher =
          ContractDescriptorValidation.requireText(defaultBookCipher, "defaultBookCipher");
    }
  }

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

  /** Descriptor for the active SQLite runtime environment. */
  public record EnvironmentDescriptor(
      EnvironmentDistributionDescriptor distribution,
      EnvironmentStorageDescriptor storage,
      EnvironmentSqliteDescriptor sqlite) {
    /** Validates one runtime environment descriptor payload. */
    public EnvironmentDescriptor {
      distribution = ContractDescriptorValidation.requireValue(distribution, "distribution");
      storage = ContractDescriptorValidation.requireValue(storage, "storage");
      sqlite = ContractDescriptorValidation.requireValue(sqlite, "sqlite");
    }
  }
}
