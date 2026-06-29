package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import org.jspecify.annotations.Nullable;

/** Stable structured violation for one rejected tax-registration declaration. */
public record TaxDefinitionViolation(String code, @Nullable String field, String message) {
  /** Validates one structured tax-definition violation. */
  public TaxDefinitionViolation {
    code = ContractDescriptorValidation.requireText(code, "code");
    field = ContractDescriptorValidation.requireOptionalText(field, "field");
    message = ContractDescriptorValidation.requireText(message, "message");
  }
}
