package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
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
        BookAdministrationRejection.AccountTaxonomyConflict,
        ContraAccountInvalid,
        AccountRegistryLifecycleRejection,
        BookAdministrationRejection.ParentAccountMissing,
        BookAdministrationRejection.ParentAccountInactive,
        BookAdministrationRejection.ParentAccountTypeConflict,
        BookAdministrationRejection.ParentAccountNotHeader,
        BookAdministrationRejection.ParentAccountTaxonomyConflict,
        BookAdministrationRejection.AccountHierarchyCycle,
        CloseTargetAccountCandidateMissing,
        CloseTargetAccountCandidateAmbiguous,
        BookAdministrationRejection.InterimResultSweepMustStartAt,
        BookAdministrationRejection.InterimResultSweepFutureDate,
        BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary,
        BookAdministrationRejection.FiscalYearCloseMustStartAt,
        BookAdministrationRejection.FiscalYearCloseMustEndAt,
        BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon,
        BookAdministrationRejection.FiscalYearCloseFutureDate,
        FiscalYearCloseRequiresGeneratedPostings {

  /** Returns the stable wire code for one book-administration rejection instance. */
  static String wireCode(BookAdministrationRejection rejection) {
    return BookAdministrationRejectionDescriptors.wireCode(rejection);
  }

  /** Returns the stable wire code for the missing-book administration rejection. */
  static String bookNotInitializedCode() {
    return BookAdministrationRejectionDescriptors.bookNotInitializedCode();
  }

  /** Returns the canonical machine descriptors for every permitted administration rejection. */
  static List<RejectionDescriptor> descriptors() {
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

  /* Rejection for interim-result sweep when the requested period start is not the live sweep horizon. */
  record InterimResultSweepMustStartAt(LocalDate requiredEffectiveDateFrom)
      implements BookAdministrationRejection {
    public InterimResultSweepMustStartAt {
      Objects.requireNonNull(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  /* Rejection for interim-result sweep when the requested period end lies in the future. */
  record InterimResultSweepFutureDate(LocalDate attemptedEffectiveDateTo)
      implements BookAdministrationRejection {
    public InterimResultSweepFutureDate {
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }

  /* Rejection for interim-result sweep when the requested range spans more than one fiscal year. */
  record InterimResultSweepCrossesFiscalYearBoundary(
      LocalDate attemptedEffectiveDateFrom,
      LocalDate attemptedEffectiveDateTo,
      FiscalYearStart fiscalYearStart)
      implements BookAdministrationRejection {
    public InterimResultSweepCrossesFiscalYearBoundary {
      Objects.requireNonNull(attemptedEffectiveDateFrom, "attemptedEffectiveDateFrom");
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
    }
  }

  /* Rejection for fiscal-year close when the requested period start misses the year boundary. */
  record FiscalYearCloseMustStartAt(LocalDate requiredEffectiveDateFrom)
      implements BookAdministrationRejection {
    public FiscalYearCloseMustStartAt {
      Objects.requireNonNull(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  /* Rejection for fiscal-year close when the requested period end misses the year boundary. */
  record FiscalYearCloseMustEndAt(LocalDate requiredEffectiveDateTo)
      implements BookAdministrationRejection {
    public FiscalYearCloseMustEndAt {
      Objects.requireNonNull(requiredEffectiveDateTo, "requiredEffectiveDateTo");
    }
  }

  /* Rejection for fiscal-year close when the selected year ends before the live close horizon. */
  record FiscalYearClosePrecedesTransferredThroughHorizon(
      LocalDate attemptedEffectiveDateTo, LocalDate transferredThroughEffectiveDate)
      implements BookAdministrationRejection {
    public FiscalYearClosePrecedesTransferredThroughHorizon {
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    }
  }

  /* Rejection for fiscal-year close when the requested period end lies in the future. */
  record FiscalYearCloseFutureDate(LocalDate attemptedEffectiveDateTo)
      implements BookAdministrationRejection {
    public FiscalYearCloseFutureDate {
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }
}
