package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Published descriptor catalog for book-administration rejection types. */
final class BookAdministrationRejectionDescriptorCatalog {
  private static final Map<BookAdministrationRejectionDescriptors.Descriptor, Definition>
      DEFINITIONS_BY_DESCRIPTOR =
          Map.ofEntries(
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor.BOOK_ALREADY_INITIALIZED,
                  definition(
                      "book-already-initialized",
                      "Book initialization refused because the selected book is already initialized.",
                      List.of())),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor.BOOK_NOT_INITIALIZED,
                  definition(
                      "administration-book-not-initialized",
                      "Administration command refused because the selected book does not exist or has not been initialized with "
                          + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                          + ".",
                      List.of())),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor.BOOK_CONTAINS_SCHEMA,
                  definition(
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
                  BookAdministrationRejectionDescriptors.Descriptor.ACCOUNT_ROLE_CONFLICT,
                  definition(
                      "account-role-conflict",
                      "Account declaration refused because the requested accountRole conflicts with the existing immutable value.",
                      List.of(
                          detailField(
                              "accountCode",
                              "Declared account code that already exists in the book."),
                          detailField(
                              "existingAccountRole",
                              "Immutable live accountRole already stored for this account."),
                          detailField(
                              "requestedAccountRole",
                              "Conflicting accountRole that the caller attempted to declare.")))),
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
                  BookAdministrationRejectionDescriptors.Descriptor.PARENT_ACCOUNT_ROLE_CONFLICT,
                  definition(
                      "parent-account-role-conflict",
                      "Account declaration refused because the requested parentAccountCode belongs to a different accountRole than the child declaration.",
                      List.of(
                          detailField(
                              "accountCode",
                              "Declared child account code whose requested accountRole conflicts with the parent account."),
                          detailField(
                              "requestedAccountRole",
                              "Requested child accountRole that does not match the declared parent account role."),
                          detailField(
                              "parentAccountCode",
                              "Requested parentAccountCode whose declared accountRole conflicts with the child."),
                          detailField(
                              "parentAccountRole",
                              "Declared parent accountRole that conflicts with the child request.")))),
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
                      "result-holding-account-candidate-missing",
                      "Period result transfer refused because policy could not find one active declared result-holding account for the selected book.",
                      List.of(
                          detailField(
                              "requiredFinancialPositionLineClassification",
                              "Required financialPositionLineClassification for the selected book's active result-transfer policy."),
                          detailField(
                              "inactiveCandidateAccountCodes",
                              "Matching declared account codes that satisfy the required classification but are inactive.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor
                      .CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS,
                  definition(
                      "result-holding-account-candidate-ambiguous",
                      "Book administration refused because more than one active declared result-holding account exists for the selected book.",
                      List.of(
                          detailField(
                              "requiredFinancialPositionLineClassification",
                              "Required financialPositionLineClassification for the selected book's active result-transfer policy."),
                          detailField(
                              "candidateAccountCodes",
                              "Active declared account codes that all satisfy the required result-transfer policy and therefore make the result-holding target ambiguous.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor
                      .PERIOD_RESULT_TRANSFER_MUST_START_AT,
                  definition(
                      "period-result-transfer-must-start-at",
                      "Period result transfer refused because the requested effectiveDateFrom does not match the live unclosed horizon.",
                      List.of(
                          detailField(
                              "requiredEffectiveDateFrom",
                              "Only admissible effectiveDateFrom for the next contiguous period result transfer.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor
                      .PERIOD_RESULT_TRANSFER_FUTURE_DATE,
                  definition(
                      "period-result-transfer-future-date",
                      "Period result transfer refused because the requested effectiveDateTo lies after the current UTC date.",
                      List.of(
                          detailField(
                              "attemptedEffectiveDateTo",
                              "Requested effectiveDateTo that lies after the current UTC date.")))),
              Map.entry(
                  BookAdministrationRejectionDescriptors.Descriptor
                      .PERIOD_RESULT_TRANSFER_CROSSES_FISCAL_YEAR_BOUNDARY,
                  definition(
                      "period-result-transfer-crosses-fiscal-year-boundary",
                      "Period result transfer refused because the requested reporting period crosses the configured fiscal-year boundary.",
                      List.of(
                          detailField(
                              "attemptedEffectiveDateFrom",
                              "Requested effectiveDateFrom for a close period that crosses the fiscal-year boundary."),
                          detailField(
                              "attemptedEffectiveDateTo",
                              "Requested effectiveDateTo for a close period that crosses the fiscal-year boundary."),
                          detailField(
                              "fiscalYearStart",
                              "Configured fiscal-year start anchor that the requested period crosses.")))));

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
    Definition definition = definition(descriptor);
    return new ContractResponse.RejectionDescriptor(
        definition.code(), definition.description(), definition.detailFields(), List.of());
  }

  private static Definition definition(
      BookAdministrationRejectionDescriptors.Descriptor descriptor) {
    return Objects.requireNonNull(DEFINITIONS_BY_DESCRIPTOR.get(descriptor), "definition");
  }

  private static Definition definition(
      String code, String description, List<ContractResponse.FieldDescriptor> detailFields) {
    return new Definition(code, description, List.copyOf(detailFields));
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }

  private record Definition(
      String code, String description, List<ContractResponse.FieldDescriptor> detailFields) {}
}
