package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;
import java.util.Map;

/** Account Registry-owned rejection descriptors. */
final class AccountRegistryRejectionDescriptorDefinitions {
  private AccountRegistryRejectionDescriptorDefinitions() {}

  static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      definitions() {
    return BookAdministrationRejectionDescriptorDefinitionSupport.merge(
        declarationDefinitions(), lifecycleDefinitions(), hierarchyDefinitions());
  }

  private static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      declarationDefinitions() {
    return Map.ofEntries(
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.ACCOUNT_TYPE_CONFLICT,
            definition(
                "account-type-conflict",
                "Account declaration refused because the requested accountType conflicts with the existing immutable value.",
                List.of(
                    detailField(
                        "accountCode", "Declared account code that already exists in the book."),
                    detailField(
                        "existingAccountType",
                        "Immutable live accountType already stored for this account."),
                    detailField(
                        "requestedAccountType",
                        "Conflicting accountType that the caller attempted to declare.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.ACCOUNT_TAXONOMY_CONFLICT,
            definition(
                "account-taxonomy-conflict",
                "Account declaration refused because the requested account taxonomy conflicts with the existing immutable value.",
                List.of(
                    detailField(
                        "accountCode", "Declared account code that already exists in the book."),
                    detailField(
                        "existingAccountTaxonomy",
                        "Immutable live taxonomy already stored for this account."),
                    detailField(
                        "requestedAccountTaxonomy",
                        "Conflicting taxonomy that the caller attempted to declare.")))));
  }

  private static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      lifecycleDefinitions() {
    return Map.ofEntries(
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.ACCOUNT_NOT_FOUND,
            preconditionDefinition(
                "account-not-found",
                "Account lifecycle command refused because accountCode is not declared in the selected book.",
                List.of(
                    detailField(
                        "accountCode",
                        "Requested accountCode that is not declared in the selected book.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.ACCOUNT_HAS_DEPENDENTS,
            preconditionDefinition(
                "account-has-dependents",
                "Account lifecycle command refused because durable relationships still depend on this account.",
                List.of(
                    detailField(
                        "accountCode", "Requested accountCode whose lifecycle change is blocked."),
                    detailField(
                        "dependencies",
                        "Durable relationship kinds that must be removed or moved before amendment or retirement.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.ACCOUNT_BALANCE_NOT_ZERO,
            preconditionDefinition(
                "account-balance-not-zero",
                "Account retirement refused because the account has a non-zero current balance.",
                List.of(
                    detailField(
                        "accountCode",
                        "Requested accountCode whose current balance must be zero before retirement.")))));
  }

  private static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      hierarchyDefinitions() {
    return Map.ofEntries(
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.PARENT_ACCOUNT_MISSING,
            definition(
                "parent-account-missing",
                "Account declaration refused because the requested parentAccountCode is not declared in the selected book.",
                List.of(
                    detailField(
                        "accountCode",
                        "Declared child account code that named this parent account."),
                    detailField(
                        "parentAccountCode",
                        "Requested parentAccountCode that caused the hierarchy refusal.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.PARENT_ACCOUNT_INACTIVE,
            definition(
                "parent-account-inactive",
                "Account declaration refused because the requested parentAccountCode exists but is inactive.",
                List.of(
                    detailField(
                        "accountCode",
                        "Declared child account code that named this parent account."),
                    detailField(
                        "parentAccountCode",
                        "Requested parentAccountCode that caused the hierarchy refusal.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.PARENT_ACCOUNT_TYPE_CONFLICT,
            definition(
                "parent-account-type-conflict",
                "Account declaration refused because the requested parentAccountCode belongs to a different accountType than the child declaration.",
                List.of(
                    detailField(
                        "accountCode",
                        "Declared child account code whose requested accountType conflicts with the parent account."),
                    detailField(
                        "requestedAccountType",
                        "Requested child accountType that does not match the declared parent account type."),
                    detailField(
                        "parentAccountCode",
                        "Requested parentAccountCode whose declared accountType conflicts with the child."),
                    detailField(
                        "parentAccountType",
                        "Declared parent accountType that conflicts with the child request.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.PARENT_ACCOUNT_NOT_HEADER,
            definition(
                "parent-account-not-header",
                "Account declaration refused because the requested parentAccountCode is not declared as a header node and therefore cannot own child accounts.",
                List.of(
                    detailField(
                        "accountCode",
                        "Declared child account code whose requested parent is not a header node."),
                    detailField(
                        "parentAccountCode",
                        "Requested parentAccountCode that cannot own child accounts."),
                    detailField(
                        "parentAccountNodeKind",
                        "Declared parent accountNodeKind that forbids child accounts.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.PARENT_ACCOUNT_TAXONOMY_CONFLICT,
            definition(
                "parent-account-taxonomy-conflict",
                "Account declaration refused because the requested parentAccountCode belongs to a different statement-classification family than the child declaration.",
                List.of(
                    detailField(
                        "accountCode",
                        "Declared child account code whose taxonomy family conflicts with the parent account."),
                    detailField(
                        "requestedAccountTaxonomy",
                        "Requested child taxonomy that does not share the parent's statement-classification family."),
                    detailField(
                        "parentAccountCode",
                        "Requested parentAccountCode whose taxonomy family conflicts with the child."),
                    detailField(
                        "parentAccountTaxonomy",
                        "Declared parent taxonomy that conflicts with the child request.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.ACCOUNT_HIERARCHY_CYCLE,
            definition(
                "account-hierarchy-cycle",
                "Account declaration refused because the requested parentAccountCode would create a cycle in the chart hierarchy.",
                List.of(
                    detailField(
                        "accountCode",
                        "Declared child account code that named this parent account."),
                    detailField(
                        "parentAccountCode",
                        "Requested parentAccountCode that caused the hierarchy refusal.")))));
  }

  private static BookAdministrationRejectionDescriptorDefinition definition(
      String code, String description, List<ContractResponse.FieldDescriptor> detailFields) {
    return new BookAdministrationRejectionDescriptorDefinition(
        ContractResponse.FailureCategory.DOMAIN_SEMANTIC,
        code,
        description,
        List.copyOf(detailFields));
  }

  private static BookAdministrationRejectionDescriptorDefinition preconditionDefinition(
      String code, String description, List<ContractResponse.FieldDescriptor> detailFields) {
    return new BookAdministrationRejectionDescriptorDefinition(
        ContractResponse.FailureCategory.PRECONDITION,
        code,
        description,
        List.copyOf(detailFields));
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }
}
