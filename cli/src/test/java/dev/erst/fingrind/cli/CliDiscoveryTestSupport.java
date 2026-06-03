package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import java.util.List;
import java.util.Objects;

/** Shared discovery test fixtures for help, capabilities, and runtime renderer tests. */
final class CliDiscoveryTestSupport {
  private CliDiscoveryTestSupport() {}

  static HelpDescriptor helpDescriptor(
      ApplicationIdentity applicationIdentity,
      List<String> usage,
      ContractResponse.BookModelDescriptor bookModel,
      List<CommandDescriptor> commands,
      List<dev.erst.fingrind.contract.discovery.WorkflowDescriptor> quickStart,
      List<ExitCodeDescriptor> exitCodes,
      ContractResponse.PreflightDescriptor preflight,
      ContractResponse.CurrencyDescriptor currencyModel) {
    return helpDescriptor(
        applicationIdentity,
        usage,
        bookModel,
        commands,
        quickStart,
        exitCodes,
        preflight,
        currencyModel,
        MachineContract.help(applicationIdentity, environment()).requestShapes());
  }

  static HelpDescriptor helpDescriptor(
      ApplicationIdentity applicationIdentity,
      List<String> usage,
      ContractResponse.BookModelDescriptor bookModel,
      List<CommandDescriptor> commands,
      List<dev.erst.fingrind.contract.discovery.WorkflowDescriptor> quickStart,
      List<ExitCodeDescriptor> exitCodes,
      ContractResponse.PreflightDescriptor preflight,
      ContractResponse.CurrencyDescriptor currencyModel,
      ContractRequestShapes.@org.jspecify.annotations.Nullable RequestShapesDescriptor
          requestShapes) {
    OperationId commandTopic = commands.size() == 1 ? commands.getFirst().name() : null;
    HelpDescriptor canonical =
        MachineContract.help(applicationIdentity, environment(), commandTopic);
    return new HelpDescriptor(
        applicationIdentity.application(),
        applicationIdentity.version(),
        applicationIdentity.description(),
        usage,
        bookModel,
        canonical.bookkeepingKernel(),
        requestShapes,
        canonical.requestTemplate(),
        canonical.declareAccountTemplate(),
        canonical.planTemplate(),
        commands,
        quickStart,
        exitCodes,
        preflight,
        currencyModel);
  }

  static ContractRequestShapes.RequestShapesDescriptor withoutDeclareAccountEnumVocabulary(
      ContractRequestShapes.RequestShapesDescriptor requestShapes) {
    ContractRequestShapes.DeclareAccountRequestShapeDescriptor declareAccount =
        Objects.requireNonNull(requestShapes.declareAccount());
    return new ContractRequestShapes.RequestShapesDescriptor(
        requestShapes.schemaDialect(),
        requestShapes.postEntry(),
        new ContractRequestShapes.DeclareAccountRequestShapeDescriptor(
            declareAccount.topLevelFields(), List.of(), declareAccount.schema()),
        requestShapes.ledgerPlan());
  }

  static ApplicationIdentity identity() {
    return new ApplicationIdentity(
        "FinGrind",
        "0.51.0",
        "Command-line double-entry bookkeeping with one protected book per accounting entity");
  }

  static EnvironmentDescriptor environment() {
    return environmentWithRuntime(
        EnvironmentSqliteDescriptor.runtime(
            SqliteCompileOptionsVerificationStatus.VERIFIED,
            SqliteRuntimeStatus.READY,
            SqliteRuntimeProvenance.BUNDLE_MANAGED,
            SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY,
            "<redacted>/libsqlite3.dylib",
            ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion(),
            ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion(),
            ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
            null));
  }

  static EnvironmentDescriptor environmentWithRuntime(
      EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return new EnvironmentDescriptor(
        new EnvironmentDistributionDescriptor(
            ProtocolCatalog.distribution().bundleRuntimeDistribution(),
            ProtocolCatalog.distribution().publicCliDistribution(),
            List.of(PublicCliBundleTarget.MACOS_AARCH64, PublicCliBundleTarget.WINDOWS_X86_64),
            List.of(),
            ProtocolCatalog.distribution().sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.runtime().storageDriver(),
            ProtocolCatalog.runtime().storageEngine(),
            ProtocolCatalog.runtime().bookProtectionMode(),
            ProtocolCatalog.runtime().protectedBookFormat()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.runtime().sqliteLibraryMode(),
            ProtocolCatalog.runtime().sqliteBundleHomeSystemProperty(),
            ProtocolCatalog.managedSqlite().requiredCompileOptions(),
            ProtocolCatalog.managedSqlite().forbiddenCompileOptions(),
            ProtocolCatalog.managedSqlite().requiresSecureMemorySupport(),
            ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion(),
            ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion(),
            ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
            runtime,
            null));
  }
}
