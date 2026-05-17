package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import java.time.LocalDate;
import java.util.Objects;

/** Local bookkeeping refusal family for book initialization and account declaration. */
public sealed interface BookkeepingAdministrationRejection
    permits BookkeepingAdministrationRejection.BookAlreadyInitialized,
        BookkeepingAdministrationRejection.BookNotInitialized,
        BookkeepingAdministrationRejection.BookContainsSchema,
        BookkeepingAdministrationRejection.AccountTypeConflict,
        BookkeepingAdministrationRejection.AccountRoleConflict,
        BookkeepingAdministrationRejection.AccountTaxonomyConflict,
        BookkeepingAdministrationRejection.ParentAccountMissing,
        BookkeepingAdministrationRejection.ParentAccountInactive,
        BookkeepingAdministrationRejection.ParentAccountTypeConflict,
        BookkeepingAdministrationRejection.ParentAccountTaxonomyConflict,
        BookkeepingAdministrationRejection.AccountHierarchyCycle,
        BookkeepingAdministrationRejection.ClosingEquityAccountMissing,
        BookkeepingAdministrationRejection.ClosingEquityAccountClassificationMismatch,
        BookkeepingAdministrationRejection.ClosingEquityAccountInactive,
        BookkeepingAdministrationRejection.PeriodCloseMustStartAt,
        BookkeepingAdministrationRejection.PeriodCloseFutureDate,
        BookkeepingAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary {

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

  /** Refusal for redeclaring an account with a conflicting immutable account role. */
  record AccountRoleConflict(
      AccountCode accountCode, AccountRole existingAccountRole, AccountRole requestedAccountRole)
      implements BookkeepingAdministrationRejection {
    public AccountRoleConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(existingAccountRole, "existingAccountRole");
      Objects.requireNonNull(requestedAccountRole, "requestedAccountRole");
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

  /** Refusal for period close when no closing-equity account has been declared. */
  record ClosingEquityAccountMissing(AccountCode accountCode)
      implements BookkeepingAdministrationRejection {
    public ClosingEquityAccountMissing {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /**
   * Refusal for period close when the selected account does not satisfy the active close
   * classification policy.
   */
  record ClosingEquityAccountClassificationMismatch(
      AccountCode accountCode,
      FinancialPositionLineClassification requiredFinancialPositionLineClassification,
      FinancialPositionLineClassification actualFinancialPositionLineClassification)
      implements BookkeepingAdministrationRejection {
    public ClosingEquityAccountClassificationMismatch {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(
          requiredFinancialPositionLineClassification,
          "requiredFinancialPositionLineClassification");
      Objects.requireNonNull(
          actualFinancialPositionLineClassification, "actualFinancialPositionLineClassification");
    }
  }

  /** Refusal for period close when the closing-equity account exists but is inactive. */
  record ClosingEquityAccountInactive(AccountCode accountCode)
      implements BookkeepingAdministrationRejection {
    public ClosingEquityAccountInactive {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Refusal for period close when the requested period start is not the live unclosed horizon. */
  record PeriodCloseMustStartAt(LocalDate requiredEffectiveDateFrom)
      implements BookkeepingAdministrationRejection {
    public PeriodCloseMustStartAt {
      Objects.requireNonNull(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  /** Refusal for period close when the requested period ends after the current UTC date. */
  record PeriodCloseFutureDate(LocalDate attemptedEffectiveDateTo)
      implements BookkeepingAdministrationRejection {
    public PeriodCloseFutureDate {
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }

  /** Refusal for period close when the requested range spans more than one fiscal year. */
  record PeriodCloseCrossesFiscalYearBoundary(
      LocalDate attemptedEffectiveDateFrom,
      LocalDate attemptedEffectiveDateTo,
      FiscalYearStart fiscalYearStart)
      implements BookkeepingAdministrationRejection {
    public PeriodCloseCrossesFiscalYearBoundary {
      Objects.requireNonNull(attemptedEffectiveDateFrom, "attemptedEffectiveDateFrom");
      Objects.requireNonNull(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
    }
  }
}
