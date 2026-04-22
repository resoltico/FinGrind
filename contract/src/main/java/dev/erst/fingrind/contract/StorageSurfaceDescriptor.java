package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Descriptor for the storage surface published by the CLI capabilities contract. */
public record StorageSurfaceDescriptor(List<String> engines, String bookBoundary) {
  /** Validates one storage-surface descriptor payload. */
  public StorageSurfaceDescriptor {
    engines = ContractDescriptorValidation.copyList(engines, "engines");
    bookBoundary = ContractDescriptorValidation.requireText(bookBoundary, "bookBoundary");
  }
}
