package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Protocol-owned managed-SQLite version pins shared across runtime, build, and operator seams. */
record ManagedSqliteContract(String requiredMinimumSqliteVersion, String requiredSqlite3mcVersion) {
  ManagedSqliteContract {
    requiredMinimumSqliteVersion =
        ContractDescriptorValidation.requireText(
            requiredMinimumSqliteVersion, "requiredMinimumSqliteVersion");
    requiredSqlite3mcVersion =
        ContractDescriptorValidation.requireText(
            requiredSqlite3mcVersion, "requiredSqlite3mcVersion");
  }
}
