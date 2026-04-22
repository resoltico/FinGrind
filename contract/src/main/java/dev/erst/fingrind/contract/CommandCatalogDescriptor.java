package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Descriptor for the grouped command catalog published by the CLI capabilities contract. */
public record CommandCatalogDescriptor(
    List<String> discovery, List<String> administration, List<String> query, List<String> write) {
  /** Validates one command-catalog descriptor payload. */
  public CommandCatalogDescriptor {
    discovery = ContractDescriptorValidation.copyList(discovery, "discovery");
    administration = ContractDescriptorValidation.copyList(administration, "administration");
    query = ContractDescriptorValidation.copyList(query, "query");
    write = ContractDescriptorValidation.copyList(write, "write");
  }
}
