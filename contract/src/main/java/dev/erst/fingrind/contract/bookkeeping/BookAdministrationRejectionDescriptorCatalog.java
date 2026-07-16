package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Published descriptor catalog for book-administration rejection types. */
final class BookAdministrationRejectionDescriptorCatalog {
  private static final Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      DEFINITIONS_BY_DESCRIPTOR =
          Map.ofEntries(
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor.BOOK_ALREADY_INITIALIZED,
                  preconditionDefinition(
                      "book-already-initialized",
                      "Book initialization refused because the selected book is already initialized.",
                      List.of())),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor.BOOK_NOT_INITIALIZED,
                  preconditionDefinition(
                      "administration-book-not-initialized",
                      "Administration command refused because the selected book does not exist or has not been initialized with "
                          + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                          + ".",
                      List.of())),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor.BOOK_CONTAINS_SCHEMA,
                  preconditionDefinition(
                      "book-contains-schema",
                      "Book initialization refused because the selected SQLite file already contains schema objects.",
                      List.of())),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor.ACCOUNT_TYPE_CONFLICT,
                  definition(
                      "account-type-conflict",
                      "Account declaration refused because the requested accountType conflicts with the existing immutable value.",
                      List.of(
                          detailField(
                              "accountCode",
                              "Declared account code that already exists in the book."),
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
                              "accountCode",
                              "Declared account code that already exists in the book."),
                          detailField(
                              "existingAccountTaxonomy",
                              "Immutable live taxonomy already stored for this account."),
                          detailField(
                              "requestedAccountTaxonomy",
                              "Conflicting taxonomy that the caller attempted to declare.")))),
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
                              "accountCode",
                              "Requested accountCode whose lifecycle change is blocked."),
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
                              "Requested accountCode whose current balance must be zero before retirement.")))),
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
                  BookAdministrationRejectionDescriptors.Descriptor
                      .PARENT_ACCOUNT_TAXONOMY_CONFLICT,
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
                              "Requested parentAccountCode that caused the hierarchy refusal.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor
                      .CLOSING_EQUITY_ACCOUNT_CANDIDATE_MISSING,
                  definition(
                      "close-target-account-candidate-missing",
                      "Close command refused because policy could not find one active declared account for the required close-target classification.",
                      List.of(
                          detailField(
                              "requiredFinancialPositionLineClassification",
                              "Required financialPositionLineClassification for the selected close-target policy."),
                          detailField(
                              "inactiveCandidateAccountCodes",
                              "Matching declared account codes that satisfy the required close-target classification but are inactive.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor
                      .CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS,
                  definition(
                      "close-target-account-candidate-ambiguous",
                      "Close command refused because more than one active declared account satisfies the required close-target classification.",
                      List.of(
                          detailField(
                              "requiredFinancialPositionLineClassification",
                              "Required financialPositionLineClassification for the selected close-target policy."),
                          detailField(
                              "candidateAccountCodes",
                              "Active declared account codes that all satisfy the required close-target classification and therefore make the selected close target ambiguous.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor
                      .INTERIM_RESULT_SWEEP_MUST_START_AT,
                  definition(
                      "interim-result-sweep-must-start-at",
                      "Interim result sweep refused because the requested effectiveDateFrom does not match the live unswept horizon.",
                      List.of(
                          detailField(
                              "requiredEffectiveDateFrom",
                              "Only admissible effectiveDateFrom for the next contiguous interim-result sweep.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor
                      .INTERIM_RESULT_SWEEP_FUTURE_DATE,
                  definition(
                      "interim-result-sweep-future-date",
                      "Interim result sweep refused because the requested effectiveDateTo lies after the current UTC date.",
                      List.of(
                          detailField(
                              "attemptedEffectiveDateTo",
                              "Requested effectiveDateTo that lies after the current UTC date.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor
                      .INTERIM_RESULT_SWEEP_CROSSES_FISCAL_YEAR_BOUNDARY,
                  definition(
                      "interim-result-sweep-crosses-fiscal-year-boundary",
                      "Interim result sweep refused because the requested reporting period crosses the configured fiscal-year boundary.",
                      List.of(
                          detailField(
                              "attemptedEffectiveDateFrom",
                              "Requested effectiveDateFrom for a sweep period that crosses the fiscal-year boundary."),
                          detailField(
                              "attemptedEffectiveDateTo",
                              "Requested effectiveDateTo for a sweep period that crosses the fiscal-year boundary."),
                          detailField(
                              "fiscalYearStart",
                              "Configured fiscal-year start anchor that the requested period crosses.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor.FISCAL_YEAR_CLOSE_MUST_START_AT,
                  definition(
                      "fiscal-year-close-must-start-at",
                      "Fiscal-year close refused because the requested effectiveDateFrom does not match the fiscal year start for the selected period.",
                      List.of(
                          detailField(
                              "requiredEffectiveDateFrom",
                              "Only admissible effectiveDateFrom for a fiscal-year close covering the selected year.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor.FISCAL_YEAR_CLOSE_MUST_END_AT,
                  definition(
                      "fiscal-year-close-must-end-at",
                      "Fiscal-year close refused because the requested effectiveDateTo does not match the fiscal year end for the selected period.",
                      List.of(
                          detailField(
                              "requiredEffectiveDateTo",
                              "Only admissible effectiveDateTo for a fiscal-year close covering the selected year.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor
                      .FISCAL_YEAR_CLOSE_PRECEDES_TRANSFERRED_THROUGH_HORIZON,
                  definition(
                      "fiscal-year-close-precedes-transferred-through-horizon",
                      "Fiscal-year close refused because the selected fiscal year ends before the live transferred-through horizon already recorded in this book.",
                      List.of(
                          detailField(
                              "attemptedEffectiveDateTo",
                              "Selected fiscal-year end that precedes the live transferred-through horizon."),
                          detailField(
                              "transferredThroughEffectiveDate",
                              "Inclusive effective date through which interim-result sweeps already transfer this book.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor.FISCAL_YEAR_CLOSE_FUTURE_DATE,
                  definition(
                      "fiscal-year-close-future-date",
                      "Fiscal-year close refused because the requested effectiveDateTo lies after the current UTC date.",
                      List.of(
                          detailField(
                              "attemptedEffectiveDateTo",
                              "Requested effectiveDateTo that lies after the current UTC date.")))));

  private BookAdministrationRejectionDescriptorCatalog() {}

  static String code(BookAdministrationRejectionDescriptors.Descriptor descriptor) {
    return definition(descriptor).code();
  }

  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return List.of(BookAdministrationRejectionDescriptors.Descriptor.values()).stream()
        .map(BookAdministrationRejectionDescriptorCatalog::descriptor)
        .toList();
  }

  private static ContractResponse.RejectionDescriptor descriptor(
      BookAdministrationRejectionDescriptors.Descriptor descriptor) {
    BookAdministrationRejectionDescriptorDefinition definition = definition(descriptor);
    return new ContractResponse.RejectionDescriptor(
        definition.code(),
        definition.category(),
        definition.description(),
        definition.detailFields(),
        List.of());
  }

  private static BookAdministrationRejectionDescriptorDefinition definition(
      BookAdministrationRejectionDescriptors.Descriptor descriptor) {
    return Objects.requireNonNull(DEFINITIONS_BY_DESCRIPTOR.get(descriptor), "definition");
  }

  private static BookAdministrationRejectionDescriptorDefinition definition(
      String code, String description, List<ContractResponse.FieldDescriptor> detailFields) {
    return definition(
        ContractResponse.FailureCategory.DOMAIN_SEMANTIC, code, description, detailFields);
  }

  private static BookAdministrationRejectionDescriptorDefinition preconditionDefinition(
      String code, String description, List<ContractResponse.FieldDescriptor> detailFields) {
    return definition(
        ContractResponse.FailureCategory.PRECONDITION, code, description, detailFields);
  }

  private static BookAdministrationRejectionDescriptorDefinition definition(
      ContractResponse.FailureCategory category,
      String code,
      String description,
      List<ContractResponse.FieldDescriptor> detailFields) {
    return new BookAdministrationRejectionDescriptorDefinition(
        category, code, description, List.copyOf(detailFields));
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }
}
