package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ApplicationIdentity;
import dev.erst.fingrind.contract.EnvironmentDescriptor;
import dev.erst.fingrind.contract.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
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
            ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
            ProtocolCatalog.sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.storageDriver(),
            ProtocolCatalog.storageEngine(),
            ProtocolCatalog.bookProtectionMode(),
            ProtocolCatalog.defaultBookCipher()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.sqliteLibraryMode(),
            ProtocolCatalog.sqliteLibraryEnvironmentVariable(),
            ProtocolCatalog.sqliteBundleHomeSystemProperty(),
            SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS,
            runtimeProbe.status() == SqliteRuntime.Status.READY
                ? SqliteCompileOptionsVerificationStatus.VERIFIED
                : SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
            runtimeProbe.requiredMinimumSqliteVersion(),
            runtimeProbe.requiredSqlite3mcVersion(),
            dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus.fromWireValue(
                runtimeProbe.status().wireValue()),
            runtimeProbe.loadedSqliteVersion(),
            runtimeProbe.loadedSqlite3mcVersion(),
            runtimeProbe.issue()));
  }
}
