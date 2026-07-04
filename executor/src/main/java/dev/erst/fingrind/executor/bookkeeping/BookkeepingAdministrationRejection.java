package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FiscalYearStart;
import java.time.LocalDate;
import java.util.Objects;

/** Local bookkeeping refusal family for book initialization and account declaration. */
public sealed interface BookkeepingAdministrationRejection
    permits BookkeepingAdministrationRejection.BookAlreadyInitialized,
        BookkeepingAdministrationRejection.BookNotInitialized,
        BookkeepingAdministrationRejection.BookContainsSchema,
        BookkeepingAdministrationRejection.AccountTypeConflict,
        BookkeepingAdministrationRejection.AccountTaxonomyConflict,
        BookkeepingAdministrationRejection.ParentAccountMissing,
        BookkeepingAdministrationRejection.ParentAccountInactive,
        BookkeepingAdministrationRejection.ParentAccountTypeConflict,
        BookkeepingAdministrationRejection.ParentAccountNotHeader,
        BookkeepingAdministrationRejection.ParentAccountTaxonomyConflict,
        BookkeepingAdministrationRejection.AccountHierarchyCycle,
        CloseTargetAccountCandidateMissing,
        CloseTargetAccountCandidateAmbiguous,
        BookkeepingAdministrationRejection.InterimResultSweepMustStartAt,
        BookkeepingAdministrationRejection.InterimResultSweepFutureDate,
        BookkeepingAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary,
        BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt,
        BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt,
        BookkeepingAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon,
        BookkeepingAdministrationRejection.FiscalYearCloseFutureDate {

  /** Refusal for an explicit open-book request against an initialized book. */
  record BookAlreadyInitialized() implements BookkeepingAdministrationRejection {}

  /** Refusal for a bookkeeping command that requires an initialized book. */
  record BookNotInitialized() implements BookkeepingAdministrationRejection {}

  /** Refusal for open-book against a pre-existing SQLite file with schema objects. */
  record BookContainsSchema() implements BookkeepingAdministrationRejection {}

  /** Refusal for redeclaring an account with a conflicting immutable account type. */
  record AccountTypeConflict(
      AccountCode accountCode, AccountType existingAccountType, AccountType requestedAccountType)
      implements BookkeepingAdministrationRejection {
    public AccountTypeConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(existingAccountType, "existingAccountType");
      Objects.requireNonNull(requestedAccountType, "requestedAccountType");
    }
  }

  /** Refusal for redeclaring an account with a conflicting immutable taxonomy. */
  record AccountTaxonomyConflict(
      AccountCode accountCode,
      AccountTaxonomy existingAccountTaxonomy,
      AccountTaxonomy requestedAccountTaxonomy)
      implements BookkeepingAdministrationRejection {
    public AccountTaxonomyConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(existingAccountTaxonomy, "existingAccountTaxonomy");
      Objects.requireNonNull(requestedAccountTaxonomy, "requestedAccountTaxonomy");
    }
  }

  /** Refusal for one child account that names a missing parent account. */
  record ParentAccountMissing(AccountCode accountCode, AccountCode parentAccountCode)
      implements BookkeepingAdministrationRejection {
    public ParentAccountMissing {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
    }
  }

  /** Refusal for one child account that names an inactive parent account. */
  record ParentAccountInactive(AccountCode accountCode, AccountCode parentAccountCode)
      implements BookkeepingAdministrationRejection {
    public ParentAccountInactive {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
    }
  }

  /** Refusal for one child account whose parent belongs to a conflicting account type. */
  record ParentAccountTypeConflict(
      AccountCode accountCode,
      AccountType requestedAccountType,
      AccountCode parentAccountCode,
      AccountType parentAccountType)
      implements BookkeepingAdministrationRejection {
    public ParentAccountTypeConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(requestedAccountType, "requestedAccountType");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
      Objects.requireNonNull(parentAccountType, "parentAccountType");
    }
  }

  /** Refusal for one child account whose parent is not a header node. */
  record ParentAccountNotHeader(
      AccountCode accountCode, AccountCode parentAccountCode, AccountNodeKind parentAccountNodeKind)
      implements BookkeepingAdministrationRejection {
    public ParentAccountNotHeader {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
      Objects.requireNonNull(parentAccountNodeKind, "parentAccountNodeKind");
    }
  }

  /** Refusal for one child account whose parent carries an incompatible taxonomy family. */
  record ParentAccountTaxonomyConflict(
      AccountCode accountCode,
      AccountTaxonomy requestedAccountTaxonomy,
      AccountCode parentAccountCode,
      AccountTaxonomy parentAccountTaxonomy)
      implements BookkeepingAdministrationRejection {
    public ParentAccountTaxonomyConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(requestedAccountTaxonomy, "requestedAccountTaxonomy");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
      Objects.requireNonNull(parentAccountTaxonomy, "parentAccountTaxonomy");
    }
  }

  /** Refusal for one declaration that would introduce a cycle into the chart hierarchy. */
  record AccountHierarchyCycle(AccountCode accountCode, AccountCode parentAccountCode)
      implements BookkeepingAdministrationRejection {
    public AccountHierarchyCycle {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(parentAccountCode, "parentAccountCode");
    }
  }

  /** Refusal for interim-result sweep when the requested period start breaks contiguity. */
  record InterimResultSweepMustStartAt(LocalDate requiredEffectiveDateFrom)
      implements BookkeepingAdministrationRejection {
    public InterimResultSweepMustStartAt {
      Objects.requireNonNull(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  /**
   * Refusal for interim-result sweep when the requested period end lies after the current UTC date.
   */
  record InterimResultSweepFutureDate(LocalDate attemptedEffectiveDateTo)
      implements BookkeepingAdministrationRejection {
    public InterimResultSweepFutureDate {
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }

  /** Refusal for interim-result sweep when the requested range spans more than one fiscal year. */
  record InterimResultSweepCrossesFiscalYearBoundary(
      LocalDate attemptedEffectiveDateFrom,
      LocalDate attemptedEffectiveDateTo,
      FiscalYearStart fiscalYearStart)
      implements BookkeepingAdministrationRejection {
    public InterimResultSweepCrossesFiscalYearBoundary {
      Objects.requireNonNull(attemptedEffectiveDateFrom, "attemptedEffectiveDateFrom");
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
    }
  }

  /** Refusal for fiscal-year close when the requested period start misses the year boundary. */
  record FiscalYearCloseMustStartAt(LocalDate requiredEffectiveDateFrom)
      implements BookkeepingAdministrationRejection {
    public FiscalYearCloseMustStartAt {
      Objects.requireNonNull(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  /** Refusal for fiscal-year close when the requested period end misses the year boundary. */
  record FiscalYearCloseMustEndAt(LocalDate requiredEffectiveDateTo)
      implements BookkeepingAdministrationRejection {
    public FiscalYearCloseMustEndAt {
      Objects.requireNonNull(requiredEffectiveDateTo, "requiredEffectiveDateTo");
    }
  }

  /**
   * Refusal for fiscal-year close when the selected fiscal year ends before the live transferred
   * through horizon.
   */
  record FiscalYearClosePrecedesTransferredThroughHorizon(
      LocalDate attemptedEffectiveDateTo, LocalDate transferredThroughEffectiveDate)
      implements BookkeepingAdministrationRejection {
    public FiscalYearClosePrecedesTransferredThroughHorizon {
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    }
  }

  /**
   * Refusal for fiscal-year close when the requested period end lies after the current UTC date.
   */
  record FiscalYearCloseFutureDate(LocalDate attemptedEffectiveDateTo)
      implements BookkeepingAdministrationRejection {
    public FiscalYearCloseFutureDate {
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }
}
