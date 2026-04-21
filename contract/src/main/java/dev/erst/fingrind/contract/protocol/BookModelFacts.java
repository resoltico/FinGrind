package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Core-owned structured facts describing the FinGrind book model and its hard limits. */
public record BookModelFacts(
    String boundary,
    String entityScope,
    String filesystem,
    String credential,
    String initialization,
    String accountRegistry,
    String migration,
    String currencyScope) {
  /** Validates one book-model facts payload. */
  public BookModelFacts {
    boundary = ContractDescriptorValidation.requireText(boundary, "boundary");
    entityScope = ContractDescriptorValidation.requireText(entityScope, "entityScope");
    filesystem = ContractDescriptorValidation.requireText(filesystem, "filesystem");
    credential = ContractDescriptorValidation.requireText(credential, "credential");
    initialization = ContractDescriptorValidation.requireText(initialization, "initialization");
    accountRegistry = ContractDescriptorValidation.requireText(accountRegistry, "accountRegistry");
    migration = ContractDescriptorValidation.requireText(migration, "migration");
    currencyScope = ContractDescriptorValidation.requireText(currencyScope, "currencyScope");
  }
}
