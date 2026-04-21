package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractDiscovery;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.sqlite.SqliteRuntime;

/**
 * Builds runtime contract descriptors that describe the active CLI distribution and SQLite probe.
 */
final class CliRuntimeContractSupport {
  private CliRuntimeContractSupport() {}

  static ContractDiscovery.ApplicationIdentity applicationIdentity(CliMetadata metadata) {
    return new ContractDiscovery.ApplicationIdentity(
        metadata.applicationName(), metadata.version(), metadata.description());
  }

  static ContractDiscovery.EnvironmentDescriptor environmentDescriptor(
      SqliteRuntime.Probe runtimeProbe, String runtimeDistribution) {
    return new ContractDiscovery.EnvironmentDescriptor(
        runtimeDistribution,
        "self-contained-bundle",
        ProtocolCatalog.supportedPublicCliBundleTargets(),
        ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
        ProtocolCatalog.sourceCheckoutJava(),
        SqliteRuntime.STORAGE_DRIVER,
        SqliteRuntime.STORAGE_ENGINE,
        SqliteRuntime.BOOK_PROTECTION_MODE,
        SqliteRuntime.DEFAULT_BOOK_CIPHER,
        runtimeProbe.libraryMode(),
        SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE,
        SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY,
        SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS,
        runtimeProbe.status() == SqliteRuntime.Status.READY,
        runtimeProbe.requiredMinimumSqliteVersion(),
        runtimeProbe.requiredSqlite3mcVersion(),
        runtimeProbe.status().wireValue(),
        runtimeProbe.loadedSqliteVersion(),
        runtimeProbe.loadedSqlite3mcVersion(),
        runtimeProbe.issue());
  }
}
