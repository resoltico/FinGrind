package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import java.time.LocalDate;
import java.util.List;
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
        BookAdministrationRejection.ParentAccountTaxonomyConflict,
        BookAdministrationRejection.AccountHierarchyCycle,
        BookAdministrationRejection.ClosingEquityAccountMissing,
        BookAdministrationRejection.ClosingEquityAccountClassificationMismatch,
        BookAdministrationRejection.ClosingEquityAccountInactive,
        BookAdministrationRejection.PeriodCloseMustStartAt,
        BookAdministrationRejection.PeriodCloseFutureDate,
        BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary {

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

  /* Rejection for period close when no closing-equity account is declared. */
  record ClosingEquityAccountMissing(AccountCode accountCode)
      implements BookAdministrationRejection {
    public ClosingEquityAccountMissing {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /*
   * Rejection for period close when the selected account does not satisfy the active close
   * classification policy.
   */
  record ClosingEquityAccountClassificationMismatch(
      AccountCode accountCode,
      FinancialPositionLineClassification requiredFinancialPositionLineClassification,
      FinancialPositionLineClassification actualFinancialPositionLineClassification)
      implements BookAdministrationRejection {
    public ClosingEquityAccountClassificationMismatch {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(
          requiredFinancialPositionLineClassification,
          "requiredFinancialPositionLineClassification");
      Objects.requireNonNull(
          actualFinancialPositionLineClassification, "actualFinancialPositionLineClassification");
    }
  }

  /* Rejection for period close when the selected closing-equity account exists but is inactive. */
  record ClosingEquityAccountInactive(AccountCode accountCode)
      implements BookAdministrationRejection {
    public ClosingEquityAccountInactive {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /* Rejection for period close when the requested period start is not the live close horizon. */
  record PeriodCloseMustStartAt(LocalDate requiredEffectiveDateFrom)
      implements BookAdministrationRejection {
    public PeriodCloseMustStartAt {
      Objects.requireNonNull(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  /* Rejection for period close when the requested period end lies in the future. */
  record PeriodCloseFutureDate(LocalDate attemptedEffectiveDateTo)
      implements BookAdministrationRejection {
    public PeriodCloseFutureDate {
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }

  /* Rejection for period close when the requested range spans more than one fiscal year. */
  record PeriodCloseCrossesFiscalYearBoundary(
      LocalDate attemptedEffectiveDateFrom,
      LocalDate attemptedEffectiveDateTo,
      FiscalYearStart fiscalYearStart)
      implements BookAdministrationRejection {
    public PeriodCloseCrossesFiscalYearBoundary {
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
      case BookAdministrationRejection.ParentAccountTaxonomyConflict _ ->
          Descriptor.PARENT_ACCOUNT_TAXONOMY_CONFLICT;
      case BookAdministrationRejection.AccountHierarchyCycle _ ->
          Descriptor.ACCOUNT_HIERARCHY_CYCLE;
      case BookAdministrationRejection.ClosingEquityAccountMissing _ ->
          Descriptor.CLOSING_EQUITY_ACCOUNT_MISSING;
      case BookAdministrationRejection.ClosingEquityAccountClassificationMismatch _ ->
          Descriptor.CLOSING_EQUITY_ACCOUNT_CLASSIFICATION_MISMATCH;
      case BookAdministrationRejection.ClosingEquityAccountInactive _ ->
          Descriptor.CLOSING_EQUITY_ACCOUNT_INACTIVE;
      case BookAdministrationRejection.PeriodCloseMustStartAt _ ->
          Descriptor.PERIOD_CLOSE_MUST_START_AT;
      case BookAdministrationRejection.PeriodCloseFutureDate _ ->
          Descriptor.PERIOD_CLOSE_FUTURE_DATE;
      case BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary _ ->
          Descriptor.PERIOD_CLOSE_CROSSES_FISCAL_YEAR_BOUNDARY;
    };
  }

  /** Canonical administration rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_ALREADY_INITIALIZED,
    BOOK_NOT_INITIALIZED,
    BOOK_CONTAINS_SCHEMA,
    ACCOUNT_TYPE_CONFLICT,
    ACCOUNT_ROLE_CONFLICT,
    ACCOUNT_TAXONOMY_CONFLICT,
    PARENT_ACCOUNT_MISSING,
    PARENT_ACCOUNT_INACTIVE,
    PARENT_ACCOUNT_TYPE_CONFLICT,
    PARENT_ACCOUNT_TAXONOMY_CONFLICT,
    ACCOUNT_HIERARCHY_CYCLE,
    CLOSING_EQUITY_ACCOUNT_MISSING,
    CLOSING_EQUITY_ACCOUNT_CLASSIFICATION_MISMATCH,
    CLOSING_EQUITY_ACCOUNT_INACTIVE,
    PERIOD_CLOSE_MUST_START_AT,
    PERIOD_CLOSE_FUTURE_DATE,
    PERIOD_CLOSE_CROSSES_FISCAL_YEAR_BOUNDARY;

    private String code() {
      return switch (this) {
        case BOOK_ALREADY_INITIALIZED -> "book-already-initialized";
        case BOOK_NOT_INITIALIZED -> "administration-book-not-initialized";
        case BOOK_CONTAINS_SCHEMA -> "book-contains-schema";
        case ACCOUNT_TYPE_CONFLICT -> "account-type-conflict";
        case ACCOUNT_ROLE_CONFLICT -> "account-role-conflict";
        case ACCOUNT_TAXONOMY_CONFLICT -> "account-taxonomy-conflict";
        case PARENT_ACCOUNT_MISSING -> "parent-account-missing";
        case PARENT_ACCOUNT_INACTIVE -> "parent-account-inactive";
        case PARENT_ACCOUNT_TYPE_CONFLICT -> "parent-account-type-conflict";
        case PARENT_ACCOUNT_TAXONOMY_CONFLICT -> "parent-account-taxonomy-conflict";
        case ACCOUNT_HIERARCHY_CYCLE -> "account-hierarchy-cycle";
        case CLOSING_EQUITY_ACCOUNT_MISSING -> "closing-equity-account-missing";
        case CLOSING_EQUITY_ACCOUNT_CLASSIFICATION_MISMATCH ->
            "closing-equity-account-classification-mismatch";
        case CLOSING_EQUITY_ACCOUNT_INACTIVE -> "closing-equity-account-inactive";
        case PERIOD_CLOSE_MUST_START_AT -> "period-close-must-start-at";
        case PERIOD_CLOSE_FUTURE_DATE -> "period-close-future-date";
        case PERIOD_CLOSE_CROSSES_FISCAL_YEAR_BOUNDARY ->
            "period-close-crosses-fiscal-year-boundary";
      };
    }

    private String description() {
      return switch (this) {
        case BOOK_ALREADY_INITIALIZED ->
            "Book initialization refused because the selected book is already initialized.";
        case BOOK_NOT_INITIALIZED ->
            "Administration command refused because the selected book does not exist or has not been initialized with "
                + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                + ".";
        case BOOK_CONTAINS_SCHEMA ->
            "Book initialization refused because the selected SQLite file already contains schema objects.";
        case ACCOUNT_TYPE_CONFLICT ->
            "Account declaration refused because the requested accountType conflicts with the existing immutable value.";
        case ACCOUNT_ROLE_CONFLICT ->
            "Account declaration refused because the requested accountRole conflicts with the existing immutable value.";
        case ACCOUNT_TAXONOMY_CONFLICT ->
            "Account declaration refused because the requested account taxonomy conflicts with the existing immutable value.";
        case PARENT_ACCOUNT_MISSING ->
            "Account declaration refused because the requested parentAccountCode is not declared in the selected book.";
        case PARENT_ACCOUNT_INACTIVE ->
            "Account declaration refused because the requested parentAccountCode exists but is inactive.";
        case PARENT_ACCOUNT_TYPE_CONFLICT ->
            "Account declaration refused because the requested parentAccountCode belongs to a different accountType than the child declaration.";
        case PARENT_ACCOUNT_TAXONOMY_CONFLICT ->
            "Account declaration refused because the requested parentAccountCode belongs to a different statement-classification family than the child declaration.";
        case ACCOUNT_HIERARCHY_CYCLE ->
            "Account declaration refused because the requested parentAccountCode would create a cycle in the chart hierarchy.";
        case CLOSING_EQUITY_ACCOUNT_MISSING ->
            "Period close refused because the selected closing-equity account code is not declared in the selected book.";
        case CLOSING_EQUITY_ACCOUNT_CLASSIFICATION_MISMATCH ->
            "Period close refused because the selected account does not satisfy the active closing-equity classification policy.";
        case CLOSING_EQUITY_ACCOUNT_INACTIVE ->
            "Period close refused because the selected closing-equity account is inactive.";
        case PERIOD_CLOSE_MUST_START_AT ->
            "Period close refused because the requested effectiveDateFrom does not match the live unclosed horizon.";
        case PERIOD_CLOSE_FUTURE_DATE ->
            "Period close refused because the requested effectiveDateTo lies after the current UTC date.";
        case PERIOD_CLOSE_CROSSES_FISCAL_YEAR_BOUNDARY ->
            "Period close refused because the requested reporting period crosses the configured fiscal-year boundary.";
      };
    }

    private List<ContractResponse.FieldDescriptor> detailFields() {
      return switch (this) {
        case BOOK_ALREADY_INITIALIZED, BOOK_NOT_INITIALIZED, BOOK_CONTAINS_SCHEMA -> List.of();
        case CLOSING_EQUITY_ACCOUNT_MISSING ->
            List.of(
                detailField(
                    "accountCode",
                    "Selected closing-equity account code that was not declared in this book."));
        case ACCOUNT_TYPE_CONFLICT ->
            List.of(
                detailField(
                    "accountCode", "Declared account code that already exists in the book."),
                detailField(
                    "existingAccountType",
                    "Immutable live accountType already stored for this account."),
                detailField(
                    "requestedAccountType",
                    "Conflicting accountType that the caller attempted to declare."));
        case ACCOUNT_ROLE_CONFLICT ->
            List.of(
                detailField(
                    "accountCode", "Declared account code that already exists in the book."),
                detailField(
                    "existingAccountRole",
                    "Immutable live accountRole already stored for this account."),
                detailField(
                    "requestedAccountRole",
                    "Conflicting accountRole that the caller attempted to declare."));
        case ACCOUNT_TAXONOMY_CONFLICT ->
            List.of(
                detailField(
                    "accountCode", "Declared account code that already exists in the book."),
                detailField(
                    "existingAccountTaxonomy",
                    "Immutable live taxonomy already stored for this account."),
                detailField(
                    "requestedAccountTaxonomy",
                    "Conflicting taxonomy that the caller attempted to declare."));
        case PARENT_ACCOUNT_MISSING, PARENT_ACCOUNT_INACTIVE, ACCOUNT_HIERARCHY_CYCLE ->
            List.of(
                detailField(
                    "accountCode", "Declared child account code that named this parent account."),
                detailField(
                    "parentAccountCode",
                    "Requested parentAccountCode that caused the hierarchy refusal."));
        case PARENT_ACCOUNT_TYPE_CONFLICT ->
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
                    "Declared parent accountType that conflicts with the child request."));
        case PARENT_ACCOUNT_TAXONOMY_CONFLICT ->
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
                    "Declared parent taxonomy that conflicts with the child request."));
        case CLOSING_EQUITY_ACCOUNT_CLASSIFICATION_MISMATCH ->
            List.of(
                detailField(
                    "accountCode",
                    "Selected account code that exists but does not satisfy the active closing-equity classification policy."),
                detailField(
                    "requiredFinancialPositionLineClassification",
                    "Required financialPositionLineClassification for the selected book's active close policy."),
                detailField(
                    "actualFinancialPositionLineClassification",
                    "Actual financialPositionLineClassification stored for the selected account."));
        case CLOSING_EQUITY_ACCOUNT_INACTIVE ->
            List.of(
                detailField(
                    "accountCode",
                    "Declared closing-equity account code that exists but is inactive."));
        case PERIOD_CLOSE_MUST_START_AT ->
            List.of(
                detailField(
                    "requiredEffectiveDateFrom",
                    "Only admissible effectiveDateFrom for the next contiguous period close."));
        case PERIOD_CLOSE_FUTURE_DATE ->
            List.of(
                detailField(
                    "attemptedEffectiveDateTo",
                    "Requested effectiveDateTo that lies after the current UTC date."));
        case PERIOD_CLOSE_CROSSES_FISCAL_YEAR_BOUNDARY ->
            List.of(
                detailField(
                    "attemptedEffectiveDateFrom",
                    "Requested effectiveDateFrom for a close period that crosses the fiscal-year boundary."),
                detailField(
                    "attemptedEffectiveDateTo",
                    "Requested effectiveDateTo for a close period that crosses the fiscal-year boundary."),
                detailField(
                    "fiscalYearStart",
                    "Configured fiscal-year start anchor that the requested period crosses."));
      };
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code(), description(), detailFields(), List.of());
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
              PARENT_ACCOUNT_TAXONOMY_CONFLICT,
              ACCOUNT_HIERARCHY_CYCLE,
              CLOSING_EQUITY_ACCOUNT_MISSING,
              CLOSING_EQUITY_ACCOUNT_CLASSIFICATION_MISMATCH,
              CLOSING_EQUITY_ACCOUNT_INACTIVE,
              PERIOD_CLOSE_MUST_START_AT,
              PERIOD_CLOSE_FUTURE_DATE,
              PERIOD_CLOSE_CROSSES_FISCAL_YEAR_BOUNDARY)
          .stream()
          .map(Descriptor::descriptor)
          .toList();
    }
  }
}
