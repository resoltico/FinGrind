package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Closed family of deterministic book-administration refusals. */
public sealed interface BookAdministrationRejection
    permits BookAdministrationRejection.BookAlreadyInitialized,
        BookAdministrationRejection.BookNotInitialized,
        BookAdministrationRejection.BookContainsSchema,
        BookAdministrationRejection.AccountTypeConflict,
        BookAdministrationRejection.AccountRoleConflict,
        BookAdministrationRejection.AccountTaxonomyConflict,
        BookAdministrationRejection.ParentAccountMissing,
        BookAdministrationRejection.ParentAccountInactive,
        BookAdministrationRejection.ParentAccountTypeConflict,
        BookAdministrationRejection.ParentAccountRoleConflict,
        BookAdministrationRejection.ParentAccountNotHeader,
        BookAdministrationRejection.ParentAccountTaxonomyConflict,
        BookAdministrationRejection.AccountHierarchyCycle,
        BookAdministrationRejection.ResultHoldingAccountCandidateMissing,
        BookAdministrationRejection.ResultHoldingAccountCandidateAmbiguous,
        BookAdministrationRejection.PeriodResultTransferMustStartAt,
        BookAdministrationRejection.PeriodResultTransferFutureDate,
        BookAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary {

  /** Returns the stable wire code for one book-administration rejection instance. */
  static String wireCode(BookAdministrationRejection rejection) {
    return descriptorFor(rejection).code();
  }

  /** Returns the stable wire code for the missing-book administration rejection. */
  static String bookNotInitializedCode() {
    return Descriptor.BOOK_NOT_INITIALIZED.code();
  }

  /** Returns the canonical machine descriptors for every permitted administration rejection. */
  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  /** Rejection for an explicit open-book request against an already initialized book. */
  record BookAlreadyInitialized() implements BookAdministrationRejection {}

  /** Rejection for commands that require an initialized book but found none. */
  record BookNotInitialized() implements BookAdministrationRejection {}

  /** Rejection for open-book against a pre-existing SQLite file that is not empty. */
  record BookContainsSchema() implements BookAdministrationRejection {}

  /** Rejection for redeclaring an account with a different immutable account type. */
  record AccountTypeConflict(
      AccountCode accountCode, AccountType existingAccountType, AccountType requestedAccountType)
      implements BookAdministrationRejection {
    /** Validates the conflicting account classification. */
    public AccountTypeConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(existingAccountType, "existingAccountType");
      Objects.requireNonNull(requestedAccountType, "requestedAccountType");
    }
  }

  /** Rejection for redeclaring an account with a different immutable account role. */
  record AccountRoleConflict(
      AccountCode accountCode, AccountRole existingAccountRole, AccountRole requestedAccountRole)
      implements BookAdministrationRejection {
    /** Validates the conflicting account doctrinal role. */
    public AccountRoleConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(existingAccountRole, "existingAccountRole");
      Objects.requireNonNull(requestedAccountRole, "requestedAccountRole");
    }
  }

  /** Rejection for redeclaring an account with a different immutable taxonomy. */
  record AccountTaxonomyConflict(
      AccountCode accountCode,
      AccountTaxonomy existingAccountTaxonomy,
      AccountTaxonomy requestedAccountTaxonomy)
      implements BookAdministrationRejection {
    public AccountTaxonomyConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(existingAccountTaxonomy, "existingAccountTaxonomy");
      Objects.requireNonNull(requestedAccountTaxonomy, "requestedAccountTaxonomy");
    }
  }

  /** Rejection for one child account whose declared parent account is missing. */
  record ParentAccountMissing(AccountCode accountCode, AccountCode parentAccountCode)
      implements BookAdministrationRejection {
    public ParentAccountMissing {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
    }
  }

  /** Rejection for one child account whose declared parent account is inactive. */
  record ParentAccountInactive(AccountCode accountCode, AccountCode parentAccountCode)
      implements BookAdministrationRejection {
    public ParentAccountInactive {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
    }
  }

  /** Rejection for one child account whose parent account type conflicts with the request. */
  record ParentAccountTypeConflict(
      AccountCode accountCode,
      AccountType requestedAccountType,
      AccountCode parentAccountCode,
      AccountType parentAccountType)
      implements BookAdministrationRejection {
    public ParentAccountTypeConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(requestedAccountType, "requestedAccountType");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
      Objects.requireNonNull(parentAccountType, "parentAccountType");
    }
  }

  /** Rejection for one child account whose parent account role conflicts with the request. */
  record ParentAccountRoleConflict(
      AccountCode accountCode,
      AccountRole requestedAccountRole,
      AccountCode parentAccountCode,
      AccountRole parentAccountRole)
      implements BookAdministrationRejection {
    public ParentAccountRoleConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(requestedAccountRole, "requestedAccountRole");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
      Objects.requireNonNull(parentAccountRole, "parentAccountRole");
    }
  }

  /** Rejection for one child account whose parent is not declared as a header node. */
  record ParentAccountNotHeader(
      AccountCode accountCode, AccountCode parentAccountCode, AccountNodeKind parentAccountNodeKind)
      implements BookAdministrationRejection {
    public ParentAccountNotHeader {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
      Objects.requireNonNull(parentAccountNodeKind, "parentAccountNodeKind");
    }
  }

  /** Rejection for one child account whose parent taxonomy family conflicts with the request. */
  record ParentAccountTaxonomyConflict(
      AccountCode accountCode,
      AccountTaxonomy requestedAccountTaxonomy,
      AccountCode parentAccountCode,
      AccountTaxonomy parentAccountTaxonomy)
      implements BookAdministrationRejection {
    public ParentAccountTaxonomyConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(requestedAccountTaxonomy, "requestedAccountTaxonomy");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
      Objects.requireNonNull(parentAccountTaxonomy, "parentAccountTaxonomy");
    }
  }

  /** Rejection for one declaration that would introduce a parent-child hierarchy cycle. */
  record AccountHierarchyCycle(AccountCode accountCode, AccountCode parentAccountCode)
      implements BookAdministrationRejection {
    public AccountHierarchyCycle {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
    }
  }

  /* Rejection for period-result transfer when policy finds no active declared result-holding target. */
  record ResultHoldingAccountCandidateMissing(
      FinancialPositionLineClassification requiredFinancialPositionLineClassification,
      List<AccountCode> inactiveCandidateAccountCodes)
      implements BookAdministrationRejection {
    public ResultHoldingAccountCandidateMissing {
      Objects.requireNonNull(
          requiredFinancialPositionLineClassification,
          "requiredFinancialPositionLineClassification");
      inactiveCandidateAccountCodes = List.copyOf(inactiveCandidateAccountCodes);
    }
  }

  /* Rejection for period-result transfer when policy finds more than one active declared result-holding target. */
  record ResultHoldingAccountCandidateAmbiguous(
      FinancialPositionLineClassification requiredFinancialPositionLineClassification,
      List<AccountCode> candidateAccountCodes)
      implements BookAdministrationRejection {
    public ResultHoldingAccountCandidateAmbiguous {
      Objects.requireNonNull(
          requiredFinancialPositionLineClassification,
          "requiredFinancialPositionLineClassification");
      candidateAccountCodes = List.copyOf(candidateAccountCodes);
    }
  }

  /* Rejection for period-result transfer when the requested period start is not the live transfer horizon. */
  record PeriodResultTransferMustStartAt(LocalDate requiredEffectiveDateFrom)
      implements BookAdministrationRejection {
    public PeriodResultTransferMustStartAt {
      Objects.requireNonNull(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  /* Rejection for period-result transfer when the requested period end lies in the future. */
  record PeriodResultTransferFutureDate(LocalDate attemptedEffectiveDateTo)
      implements BookAdministrationRejection {
    public PeriodResultTransferFutureDate {
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }

  /* Rejection for period-result transfer when the requested range spans more than one fiscal year. */
  record PeriodResultTransferCrossesFiscalYearBoundary(
      LocalDate attemptedEffectiveDateFrom,
      LocalDate attemptedEffectiveDateTo,
      FiscalYearStart fiscalYearStart)
      implements BookAdministrationRejection {
    public PeriodResultTransferCrossesFiscalYearBoundary {
      Objects.requireNonNull(attemptedEffectiveDateFrom, "attemptedEffectiveDateFrom");
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
    }
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }

  private static Descriptor descriptorFor(BookAdministrationRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookAdministrationRejection.BookAlreadyInitialized _ ->
          Descriptor.BOOK_ALREADY_INITIALIZED;
      case BookAdministrationRejection.BookNotInitialized _ -> Descriptor.BOOK_NOT_INITIALIZED;
      case BookAdministrationRejection.BookContainsSchema _ -> Descriptor.BOOK_CONTAINS_SCHEMA;
      case BookAdministrationRejection.AccountTypeConflict _ -> Descriptor.ACCOUNT_TYPE_CONFLICT;
      case BookAdministrationRejection.AccountRoleConflict _ -> Descriptor.ACCOUNT_ROLE_CONFLICT;
      case BookAdministrationRejection.AccountTaxonomyConflict _ ->
          Descriptor.ACCOUNT_TAXONOMY_CONFLICT;
      case BookAdministrationRejection.ParentAccountMissing _ -> Descriptor.PARENT_ACCOUNT_MISSING;
      case BookAdministrationRejection.ParentAccountInactive _ ->
          Descriptor.PARENT_ACCOUNT_INACTIVE;
      case BookAdministrationRejection.ParentAccountTypeConflict _ ->
          Descriptor.PARENT_ACCOUNT_TYPE_CONFLICT;
      case BookAdministrationRejection.ParentAccountRoleConflict _ ->
          Descriptor.PARENT_ACCOUNT_ROLE_CONFLICT;
      case BookAdministrationRejection.ParentAccountNotHeader _ ->
          Descriptor.PARENT_ACCOUNT_NOT_HEADER;
      case BookAdministrationRejection.ParentAccountTaxonomyConflict _ ->
          Descriptor.PARENT_ACCOUNT_TAXONOMY_CONFLICT;
      case BookAdministrationRejection.AccountHierarchyCycle _ ->
          Descriptor.ACCOUNT_HIERARCHY_CYCLE;
      case BookAdministrationRejection.ResultHoldingAccountCandidateMissing _ ->
          Descriptor.CLOSING_EQUITY_ACCOUNT_CANDIDATE_MISSING;
      case BookAdministrationRejection.ResultHoldingAccountCandidateAmbiguous _ ->
          Descriptor.CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS;
      case BookAdministrationRejection.PeriodResultTransferMustStartAt _ ->
          Descriptor.PERIOD_RESULT_TRANSFER_MUST_START_AT;
      case BookAdministrationRejection.PeriodResultTransferFutureDate _ ->
          Descriptor.PERIOD_RESULT_TRANSFER_FUTURE_DATE;
      case BookAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary _ ->
          Descriptor.PERIOD_RESULT_TRANSFER_CROSSES_FISCAL_YEAR_BOUNDARY;
    };
  }

  /** Canonical administration rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_ALREADY_INITIALIZED(
        "book-already-initialized",
        "Book initialization refused because the selected book is already initialized."),
    BOOK_NOT_INITIALIZED(
        "administration-book-not-initialized",
        "Administration command refused because the selected book does not exist or has not been initialized with "
            + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
            + "."),
    BOOK_CONTAINS_SCHEMA(
        "book-contains-schema",
        "Book initialization refused because the selected SQLite file already contains schema objects."),
    ACCOUNT_TYPE_CONFLICT(
        "account-type-conflict",
        "Account declaration refused because the requested accountType conflicts with the existing immutable value."),
    ACCOUNT_ROLE_CONFLICT(
        "account-role-conflict",
        "Account declaration refused because the requested accountRole conflicts with the existing immutable value."),
    ACCOUNT_TAXONOMY_CONFLICT(
        "account-taxonomy-conflict",
        "Account declaration refused because the requested account taxonomy conflicts with the existing immutable value."),
    PARENT_ACCOUNT_MISSING(
        "parent-account-missing",
        "Account declaration refused because the requested parentAccountCode is not declared in the selected book."),
    PARENT_ACCOUNT_INACTIVE(
        "parent-account-inactive",
        "Account declaration refused because the requested parentAccountCode exists but is inactive."),
    PARENT_ACCOUNT_TYPE_CONFLICT(
        "parent-account-type-conflict",
        "Account declaration refused because the requested parentAccountCode belongs to a different accountType than the child declaration."),
    PARENT_ACCOUNT_ROLE_CONFLICT(
        "parent-account-role-conflict",
        "Account declaration refused because the requested parentAccountCode belongs to a different accountRole than the child declaration."),
    PARENT_ACCOUNT_NOT_HEADER(
        "parent-account-not-header",
        "Account declaration refused because the requested parentAccountCode is not declared as a header node and therefore cannot own child accounts."),
    PARENT_ACCOUNT_TAXONOMY_CONFLICT(
        "parent-account-taxonomy-conflict",
        "Account declaration refused because the requested parentAccountCode belongs to a different statement-classification family than the child declaration."),
    ACCOUNT_HIERARCHY_CYCLE(
        "account-hierarchy-cycle",
        "Account declaration refused because the requested parentAccountCode would create a cycle in the chart hierarchy."),
    CLOSING_EQUITY_ACCOUNT_CANDIDATE_MISSING(
        "result-holding-account-candidate-missing",
        "Period result transfer refused because policy could not find one active declared result-holding account for the selected book."),
    CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS(
        "result-holding-account-candidate-ambiguous",
        "Period result transfer refused because policy found more than one active declared result-holding account for the selected book."),
    PERIOD_RESULT_TRANSFER_MUST_START_AT(
        "period-result-transfer-must-start-at",
        "Period result transfer refused because the requested effectiveDateFrom does not match the live unclosed horizon."),
    PERIOD_RESULT_TRANSFER_FUTURE_DATE(
        "period-result-transfer-future-date",
        "Period result transfer refused because the requested effectiveDateTo lies after the current UTC date."),
    PERIOD_RESULT_TRANSFER_CROSSES_FISCAL_YEAR_BOUNDARY(
        "period-result-transfer-crosses-fiscal-year-boundary",
        "Period result transfer refused because the requested reporting period crosses the configured fiscal-year boundary.");

    private static final Map<Descriptor, List<ContractResponse.FieldDescriptor>>
        DETAIL_FIELDS_BY_DESCRIPTOR =
            Map.ofEntries(
                Map.entry(BOOK_ALREADY_INITIALIZED, List.of()),
                Map.entry(BOOK_NOT_INITIALIZED, List.of()),
                Map.entry(BOOK_CONTAINS_SCHEMA, List.of()),
                Map.entry(
                    ACCOUNT_TYPE_CONFLICT,
                    List.of(
                        detailField(
                            "accountCode",
                            "Declared account code that already exists in the book."),
                        detailField(
                            "existingAccountType",
                            "Immutable live accountType already stored for this account."),
                        detailField(
                            "requestedAccountType",
                            "Conflicting accountType that the caller attempted to declare."))),
                Map.entry(
                    ACCOUNT_ROLE_CONFLICT,
                    List.of(
                        detailField(
                            "accountCode",
                            "Declared account code that already exists in the book."),
                        detailField(
                            "existingAccountRole",
                            "Immutable live accountRole already stored for this account."),
                        detailField(
                            "requestedAccountRole",
                            "Conflicting accountRole that the caller attempted to declare."))),
                Map.entry(
                    ACCOUNT_TAXONOMY_CONFLICT,
                    List.of(
                        detailField(
                            "accountCode",
                            "Declared account code that already exists in the book."),
                        detailField(
                            "existingAccountTaxonomy",
                            "Immutable live taxonomy already stored for this account."),
                        detailField(
                            "requestedAccountTaxonomy",
                            "Conflicting taxonomy that the caller attempted to declare."))),
                Map.entry(
                    PARENT_ACCOUNT_MISSING,
                    List.of(
                        detailField(
                            "accountCode",
                            "Declared child account code that named this parent account."),
                        detailField(
                            "parentAccountCode",
                            "Requested parentAccountCode that caused the hierarchy refusal."))),
                Map.entry(
                    PARENT_ACCOUNT_INACTIVE,
                    List.of(
                        detailField(
                            "accountCode",
                            "Declared child account code that named this parent account."),
                        detailField(
                            "parentAccountCode",
                            "Requested parentAccountCode that caused the hierarchy refusal."))),
                Map.entry(
                    PARENT_ACCOUNT_TYPE_CONFLICT,
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
                            "Declared parent accountType that conflicts with the child request."))),
                Map.entry(
                    PARENT_ACCOUNT_ROLE_CONFLICT,
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
                            "Declared parent accountRole that conflicts with the child request."))),
                Map.entry(
                    PARENT_ACCOUNT_NOT_HEADER,
                    List.of(
                        detailField(
                            "accountCode",
                            "Declared child account code whose requested parent is not a header node."),
                        detailField(
                            "parentAccountCode",
                            "Requested parentAccountCode that cannot own child accounts."),
                        detailField(
                            "parentAccountNodeKind",
                            "Declared parent accountNodeKind that forbids child accounts."))),
                Map.entry(
                    PARENT_ACCOUNT_TAXONOMY_CONFLICT,
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
                            "Declared parent taxonomy that conflicts with the child request."))),
                Map.entry(
                    ACCOUNT_HIERARCHY_CYCLE,
                    List.of(
                        detailField(
                            "accountCode",
                            "Declared child account code that named this parent account."),
                        detailField(
                            "parentAccountCode",
                            "Requested parentAccountCode that caused the hierarchy refusal."))),
                Map.entry(
                    CLOSING_EQUITY_ACCOUNT_CANDIDATE_MISSING,
                    List.of(
                        detailField(
                            "requiredFinancialPositionLineClassification",
                            "Required financialPositionLineClassification for the selected book's active result-transfer policy."),
                        detailField(
                            "inactiveCandidateAccountCodes",
                            "Matching declared account codes that satisfy the required classification but are inactive."))),
                Map.entry(
                    CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS,
                    List.of(
                        detailField(
                            "requiredFinancialPositionLineClassification",
                            "Required financialPositionLineClassification for the selected book's active result-transfer policy."),
                        detailField(
                            "candidateAccountCodes",
                            "Active declared account codes that all satisfy the required result-transfer policy and therefore make the result-holding target ambiguous."))),
                Map.entry(
                    PERIOD_RESULT_TRANSFER_MUST_START_AT,
                    List.of(
                        detailField(
                            "requiredEffectiveDateFrom",
                            "Only admissible effectiveDateFrom for the next contiguous period result transfer."))),
                Map.entry(
                    PERIOD_RESULT_TRANSFER_FUTURE_DATE,
                    List.of(
                        detailField(
                            "attemptedEffectiveDateTo",
                            "Requested effectiveDateTo that lies after the current UTC date."))),
                Map.entry(
                    PERIOD_RESULT_TRANSFER_CROSSES_FISCAL_YEAR_BOUNDARY,
                    List.of(
                        detailField(
                            "attemptedEffectiveDateFrom",
                            "Requested effectiveDateFrom for a close period that crosses the fiscal-year boundary."),
                        detailField(
                            "attemptedEffectiveDateTo",
                            "Requested effectiveDateTo for a close period that crosses the fiscal-year boundary."),
                        detailField(
                            "fiscalYearStart",
                            "Configured fiscal-year start anchor that the requested period crosses."))));

    private final String code;
    private final String description;

    Descriptor(String code, String description) {
      this.code = code;
      this.description = description;
    }

    private String code() {
      return code;
    }

    private String description() {
      return description;
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code(),
          description(),
          Objects.requireNonNull(DETAIL_FIELDS_BY_DESCRIPTOR.get(this), "detailFields"),
          List.of());
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return List.of(
              BOOK_ALREADY_INITIALIZED,
              BOOK_NOT_INITIALIZED,
              BOOK_CONTAINS_SCHEMA,
              ACCOUNT_TYPE_CONFLICT,
              ACCOUNT_ROLE_CONFLICT,
              ACCOUNT_TAXONOMY_CONFLICT,
              PARENT_ACCOUNT_MISSING,
              PARENT_ACCOUNT_INACTIVE,
              PARENT_ACCOUNT_TYPE_CONFLICT,
              PARENT_ACCOUNT_ROLE_CONFLICT,
              PARENT_ACCOUNT_NOT_HEADER,
              PARENT_ACCOUNT_TAXONOMY_CONFLICT,
              ACCOUNT_HIERARCHY_CYCLE,
              CLOSING_EQUITY_ACCOUNT_CANDIDATE_MISSING,
              CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS,
              PERIOD_RESULT_TRANSFER_MUST_START_AT,
              PERIOD_RESULT_TRANSFER_FUTURE_DATE,
              PERIOD_RESULT_TRANSFER_CROSSES_FISCAL_YEAR_BOUNDARY)
          .stream()
          .map(Descriptor::descriptor)
          .toList();
    }
  }
}
