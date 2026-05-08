package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;

/** Sealed inventory root for the public discovery descriptor namespace. */
public sealed interface ContractDiscoveryDescriptor extends ProtocolSuccessPayload
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
