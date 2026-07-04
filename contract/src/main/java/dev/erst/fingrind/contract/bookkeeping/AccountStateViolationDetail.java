package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import org.jspecify.annotations.Nullable;

/** Stable structured account-state issue emitted for one rejected posting attribute. */
public record AccountStateViolationDetail(
    String code,
    String field,
    String message,
    String category,
    String repair,
    String accountCode,
    @Nullable String accountNodeKind) {
  public AccountStateViolationDetail {
    code = ContractDescriptorValidation.requireText(code, "code");
    field = ContractDescriptorValidation.requireText(field, "field");
    message = ContractDescriptorValidation.requireText(message, "message");
    category = ContractDescriptorValidation.requireText(category, "category");
    repair = ContractDescriptorValidation.requireText(repair, "repair");
    accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
    accountNodeKind =
        ContractDescriptorValidation.requireOptionalText(accountNodeKind, "accountNodeKind");
  }
}
