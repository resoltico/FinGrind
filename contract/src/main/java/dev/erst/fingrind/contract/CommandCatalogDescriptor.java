package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;

/** Descriptor for the grouped command catalog published by the CLI capabilities contract. */
public record CommandCatalogDescriptor(
    List<OperationId> discovery,
    List<OperationId> administration,
    List<OperationId> query,
    List<OperationId> write)
    implements ContractDiscoveryDescriptor {
  /** Validates one command-catalog descriptor payload. */
  public CommandCatalogDescriptor {
    discovery = ContractDescriptorValidation.copyList(discovery, "discovery");
    administration = ContractDescriptorValidation.copyList(administration, "administration");
    query = ContractDescriptorValidation.copyList(query, "query");
    write = ContractDescriptorValidation.copyList(write, "write");
  }
}
