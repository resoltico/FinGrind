package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ApplicationIdentity;
import dev.erst.fingrind.contract.EnvironmentDescriptor;
import dev.erst.fingrind.contract.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
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
            runtimeDistribution,
            "self-contained-bundle",
            ProtocolCatalog.supportedPublicCliBundleTargets(),
            ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
            ProtocolCatalog.sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            SqliteRuntime.STORAGE_DRIVER,
            SqliteRuntime.STORAGE_ENGINE,
            SqliteRuntime.BOOK_PROTECTION_MODE,
            SqliteRuntime.DEFAULT_BOOK_CIPHER),
        new EnvironmentSqliteDescriptor(
            runtimeProbe.libraryMode(),
            SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE,
            SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY,
            SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS,
            runtimeProbe.status() == SqliteRuntime.Status.READY
                ? SqliteCompileOptionsVerificationStatus.VERIFIED
                : SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
            runtimeProbe.requiredMinimumSqliteVersion(),
            runtimeProbe.requiredSqlite3mcVersion(),
            runtimeProbe.status().wireValue(),
            runtimeProbe.loadedSqliteVersion(),
            runtimeProbe.loadedSqlite3mcVersion(),
            runtimeProbe.issue()));
  }
}
