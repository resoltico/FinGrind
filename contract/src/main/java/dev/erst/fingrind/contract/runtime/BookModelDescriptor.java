package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Descriptor for the machine-readable book model. */
public record BookModelDescriptor(
    String boundary,
    String entityScope,
    String filesystem,
    String credential,
    String initialization,
    String accountRegistry,
    String currencyScope)
    implements ResponseDescriptorType {
  /** Validates one book-model descriptor payload. */
  public BookModelDescriptor {
    boundary = ContractDescriptorValidation.requireText(boundary, "boundary");
    entityScope = ContractDescriptorValidation.requireText(entityScope, "entityScope");
    filesystem = ContractDescriptorValidation.requireText(filesystem, "filesystem");
    credential = ContractDescriptorValidation.requireText(credential, "credential");
    initialization = ContractDescriptorValidation.requireText(initialization, "initialization");
    accountRegistry = ContractDescriptorValidation.requireText(accountRegistry, "accountRegistry");
    currencyScope = ContractDescriptorValidation.requireText(currencyScope, "currencyScope");
  }
}
