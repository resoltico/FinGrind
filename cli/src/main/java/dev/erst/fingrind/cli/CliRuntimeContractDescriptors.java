package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentPublicationDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentRuntimeDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import org.jspecify.annotations.Nullable;

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
      SqliteRuntime.Probe runtimeProbe,
      String runtimeDistribution,
      @Nullable PublicCliBundleTarget bundleTarget) {
    CliOutputModeDefaults.OutputDefault outputDefault =
        CliOutputModeDefaults.outputDefault(CliOutputModeDefaults.OutputSurface.SELECTABLE);
    return new EnvironmentDescriptor(
        new EnvironmentRuntimeDescriptor(
            RuntimeDistribution.fromWireValue(runtimeDistribution), outputDefault.mode()),
        new EnvironmentPublicationDescriptor(
            ProtocolCatalog.distribution().publicCliDistribution(),
            ProtocolCatalog.distribution().supportedPublicCliBundleTargets(),
            ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets(),
            bundleTarget,
            ProtocolCatalog.distribution().sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.runtime().storageDriver(),
            ProtocolCatalog.runtime().storageEngine(),
            ProtocolCatalog.runtime().bookProtectionMode(),
            ProtocolCatalog.runtime().protectedBookFormat()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.runtime().sqliteLibraryMode(),
            ProtocolCatalog.runtime().sqliteBundleHomeSystemProperty(),
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
                runtimeProbe.issue()),
            runtimeProbe.runtimeArtifactEvidence()));
  }
}
