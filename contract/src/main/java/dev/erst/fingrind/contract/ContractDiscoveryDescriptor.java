package dev.erst.fingrind.contract;

/** Sealed inventory root for the public discovery descriptor namespace. */
public sealed interface ContractDiscoveryDescriptor
    permits ApplicationIdentity,
        HelpDescriptor,
        CapabilitiesDescriptor,
        StorageSurfaceDescriptor,
        CommandCatalogDescriptor,
        VersionDescriptor,
        ArtifactOutputDescriptor,
        CommandDescriptor,
        ExitCodeDescriptor,
        EnvironmentDistributionDescriptor,
        EnvironmentStorageDescriptor,
        EnvironmentSqliteDescriptor,
        EnvironmentDescriptor {}
