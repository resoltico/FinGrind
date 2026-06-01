package dev.erst.fingrind.contract.bookkeeping;

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
    return BookAdministrationRejectionDescriptors.wireCode(rejection);
  }

  /** Returns the stable wire code for the missing-book administration rejection. */
  static String bookNotInitializedCode() {
    return BookAdministrationRejectionDescriptors.bookNotInitializedCode();
  }

  /** Returns the canonical machine descriptors for every permitted administration rejection. */
  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return BookAdministrationRejectionDescriptors.descriptors();
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
}
