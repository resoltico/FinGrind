package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.BookCipher;
import dev.erst.fingrind.contract.protocol.BookProtectionMode;
import dev.erst.fingrind.contract.protocol.StorageDriver;
import dev.erst.fingrind.contract.protocol.StorageEngine;

/** Descriptor for the storage engine exposed by the active runtime environment. */
public record EnvironmentStorageDescriptor(
    StorageDriver storageDriver,
    StorageEngine storageEngine,
    BookProtectionMode bookProtectionMode,
    BookCipher defaultBookCipher)
    implements ContractDiscoveryDescriptor {
  /** Validates one environment storage descriptor payload. */
  public EnvironmentStorageDescriptor {
    storageDriver = ContractDescriptorValidation.requireValue(storageDriver, "storageDriver");
    storageEngine = ContractDescriptorValidation.requireValue(storageEngine, "storageEngine");
    bookProtectionMode =
        ContractDescriptorValidation.requireValue(bookProtectionMode, "bookProtectionMode");
    defaultBookCipher =
        ContractDescriptorValidation.requireValue(defaultBookCipher, "defaultBookCipher");
  }
}
