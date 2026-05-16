package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;

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
        SelectableOutputDefaultsDescriptor,
        ExitCodeDescriptor,
        EnvironmentDistributionDescriptor,
        EnvironmentStorageDescriptor,
        EnvironmentSqliteDescriptor,
        EnvironmentDescriptor {}
