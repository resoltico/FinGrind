package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.sqlite.SqliteRuntime;

/**
 * Builds runtime contract descriptors that describe the active CLI distribution and SQLite probe.
 */
final class CliRuntimeContractDescriptors {
  private CliRuntimeContractDescriptors() {}

  static ApplicationIdentity applicationIdentity(CliMetadata metadata) {
    return new ApplicationIdentity(
        metadata.applicationName(), metadata.version(), metadata.description());
  }

  static EnvironmentDescriptor environmentDescriptor(
      SqliteRuntime.Probe runtimeProbe, String runtimeDistribution) {
    return new EnvironmentDescriptor(
        new EnvironmentDistributionDescriptor(
            RuntimeDistribution.fromWireValue(runtimeDistribution),
            ProtocolCatalog.publicCliDistribution(),
            ProtocolCatalog.supportedPublicCliBundleTargets(),
            ProtocolCatalog.unsupportedPublicCliBundleTargets(),
            ProtocolCatalog.sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.storageDriver(),
            ProtocolCatalog.storageEngine(),
            ProtocolCatalog.bookProtectionMode(),
            ProtocolCatalog.protectedBookFormat()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.sqliteLibraryMode(),
            ProtocolCatalog.sqliteLibraryEnvironmentVariable(),
            ProtocolCatalog.sqliteBundleHomeSystemProperty(),
            SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS,
            SqliteRuntime.FORBIDDEN_SQLITE_COMPILE_OPTIONS,
            SqliteRuntime.REQUIRES_SECURE_MEMORY_SUPPORT,
            runtimeProbe.requiredMinimumSqliteVersion(),
            runtimeProbe.requiredSqlite3mcVersion(),
            runtimeProbe.requiredSqliteSourceId(),
            EnvironmentSqliteDescriptor.runtime(
                runtimeProbe.compileOptionsVerification(),
                dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus.fromWireValue(
                    runtimeProbe.status().wireValue()),
                runtimeProbe.runtimeProvenance(),
                runtimeProbe.runtimeTrustBasis(),
                runtimeProbe.loadedLibraryPath(),
                runtimeProbe.loadedSqliteVersion(),
                runtimeProbe.loadedSqlite3mcVersion(),
                runtimeProbe.loadedSqliteSourceId(),
                runtimeProbe.issue())));
  }
}
