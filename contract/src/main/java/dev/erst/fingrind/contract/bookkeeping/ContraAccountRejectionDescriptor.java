package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;

/** Owns the published descriptor for invalid contra-account relationships. */
final class ContraAccountRejectionDescriptor {
  private ContraAccountRejectionDescriptor() {}

  static BookAdministrationRejectionDescriptorDefinition definition() {
    return new BookAdministrationRejectionDescriptorDefinition(
        ContractResponse.FailureCategory.DOMAIN_SEMANTIC,
        "contra-account-invalid",
        "Account declaration refused because contraOfAccountCode does not preserve the target account's live chart meaning.",
        List.of(
            new ContractResponse.FieldDescriptor(
                "accountCode", "Declared contra account code that named the target."),
            new ContractResponse.FieldDescriptor(
                "contraOfAccountCode",
                "Requested account code that the contra account would reduce."),
            new ContractResponse.FieldDescriptor(
                "violation", "Specific target relationship invariant that the request violates.")));
  }
}
