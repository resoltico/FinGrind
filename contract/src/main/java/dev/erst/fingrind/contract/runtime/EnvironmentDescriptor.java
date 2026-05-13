package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.discovery.ContractDiscoveryDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Descriptor for the active SQLite runtime environment. */
public record EnvironmentDescriptor(
    EnvironmentDistributionDescriptor distribution,
    EnvironmentStorageDescriptor storage,
    EnvironmentSqliteDescriptor sqlite)
    implements ContractDiscoveryDescriptor {
  /** Validates one runtime environment descriptor payload. */
  public EnvironmentDescriptor {
    distribution = ContractDescriptorValidation.requireValue(distribution, "distribution");
    storage = ContractDescriptorValidation.requireValue(storage, "storage");
    sqlite = ContractDescriptorValidation.requireValue(sqlite, "sqlite");
  }
}
