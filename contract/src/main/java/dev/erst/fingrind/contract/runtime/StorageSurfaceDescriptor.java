package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.discovery.ContractDiscoveryDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.StorageEngine;
import java.util.List;

/** Descriptor for the storage surface published by the CLI capabilities contract. */
public record StorageSurfaceDescriptor(List<StorageEngine> engines, String bookBoundary)
    implements ContractDiscoveryDescriptor {
  /** Validates one storage-surface descriptor payload. */
  public StorageSurfaceDescriptor {
    engines = ContractDescriptorValidation.copyList(engines, "engines");
    bookBoundary = ContractDescriptorValidation.requireText(bookBoundary, "bookBoundary");
  }
}
