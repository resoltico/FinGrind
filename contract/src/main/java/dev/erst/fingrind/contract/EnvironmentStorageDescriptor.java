package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Descriptor for the storage engine exposed by the active runtime environment. */
public record EnvironmentStorageDescriptor(
    String storageDriver,
    String storageEngine,
    String bookProtectionMode,
    String defaultBookCipher) {
  /** Validates one environment storage descriptor payload. */
  public EnvironmentStorageDescriptor {
    storageDriver = ContractDescriptorValidation.requireText(storageDriver, "storageDriver");
    storageEngine = ContractDescriptorValidation.requireText(storageEngine, "storageEngine");
    bookProtectionMode =
        ContractDescriptorValidation.requireText(bookProtectionMode, "bookProtectionMode");
    defaultBookCipher =
        ContractDescriptorValidation.requireText(defaultBookCipher, "defaultBookCipher");
  }
}
