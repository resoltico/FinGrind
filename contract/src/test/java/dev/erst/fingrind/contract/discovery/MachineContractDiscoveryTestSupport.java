package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentPublicationDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentRuntimeDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import org.jspecify.annotations.Nullable;

/** Shared discovery test fixtures for machine-contract surface coverage. */
final class MachineContractDiscoveryTestSupport {
  static final ApplicationIdentity IDENTITY =
      new ApplicationIdentity("FinGrind", "0.57.0", "Protected bookkeeping kernel");

  private MachineContractDiscoveryTestSupport() {}

  static EnvironmentDescriptor environment(RuntimeDistribution runtimeDistribution) {
    return new EnvironmentDescriptor(
        new EnvironmentRuntimeDescriptor(runtimeDistribution, OutputMode.TEXT, null),
        new EnvironmentPublicationDescriptor(
            ProtocolCatalog.distribution().publicCliDistribution(),
            ProtocolCatalog.distribution().supportedPublicCliBundleTargets(),
            ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets(),
            activeBundleTarget(runtimeDistribution),
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
            EnvironmentSqliteDescriptor.runtime(
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                "test fixture"),
            null));
  }

  private static @Nullable PublicCliBundleTarget activeBundleTarget(
      RuntimeDistribution runtimeDistribution) {
    return runtimeDistribution == RuntimeDistribution.SELF_CONTAINED_BUNDLE
        ? PublicCliBundleTarget.LINUX_X86_64
        : null;
  }
}
