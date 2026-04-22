package dev.erst.fingrind.contract;

import java.util.List;

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
}
