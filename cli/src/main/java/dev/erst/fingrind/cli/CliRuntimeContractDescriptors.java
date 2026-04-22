package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractDiscovery;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.sqlite.SqliteRuntime;

/**
 * Builds runtime contract descriptors that describe the active CLI distribution and SQLite probe.
 */
final class CliRuntimeContractDescriptors {
  private CliRuntimeContractDescriptors() {}

  static ContractDiscovery.ApplicationIdentity applicationIdentity(CliMetadata metadata) {
    return new ContractDiscovery.ApplicationIdentity(
        metadata.applicationName(), metadata.version(), metadata.description());
  }

  static ContractDiscovery.EnvironmentDescriptor environmentDescriptor(
      SqliteRuntime.Probe runtimeProbe, String runtimeDistribution) {
    return new ContractDiscovery.EnvironmentDescriptor(
        new ContractDiscovery.EnvironmentDistributionDescriptor(
            runtimeDistribution,
            "self-contained-bundle",
            ProtocolCatalog.supportedPublicCliBundleTargets(),
            ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
            ProtocolCatalog.sourceCheckoutJava()),
        new ContractDiscovery.EnvironmentStorageDescriptor(
            SqliteRuntime.STORAGE_DRIVER,
            SqliteRuntime.STORAGE_ENGINE,
            SqliteRuntime.BOOK_PROTECTION_MODE,
            SqliteRuntime.DEFAULT_BOOK_CIPHER),
        new ContractDiscovery.EnvironmentSqliteDescriptor(
            runtimeProbe.libraryMode(),
            SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE,
            SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY,
            SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS,
            runtimeProbe.status() == SqliteRuntime.Status.READY
                ? ContractDiscovery.SqliteCompileOptionsVerificationStatus.VERIFIED
                : ContractDiscovery.SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
            runtimeProbe.requiredMinimumSqliteVersion(),
            runtimeProbe.requiredSqlite3mcVersion(),
            runtimeProbe.status().wireValue(),
            runtimeProbe.loadedSqliteVersion(),
            runtimeProbe.loadedSqlite3mcVersion(),
            runtimeProbe.issue()));
  }
}
