package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
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
        BookkeepingAdministrationRejection.AccountRoleConflict,
        BookkeepingAdministrationRejection.RetainedEarningsAccountMissing,
        BookkeepingAdministrationRejection.RetainedEarningsAccountRoleMismatch,
        BookkeepingAdministrationRejection.RetainedEarningsAccountInactive,
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

  /** Refusal for period close when no retained-earnings account has been declared. */
  record RetainedEarningsAccountMissing(AccountCode accountCode)
      implements BookkeepingAdministrationRejection {
    public RetainedEarningsAccountMissing {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Refusal for period close when the selected account is not doctrinally retained earnings. */
  record RetainedEarningsAccountRoleMismatch(AccountCode accountCode, AccountRole actualAccountRole)
      implements BookkeepingAdministrationRejection {
    public RetainedEarningsAccountRoleMismatch {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(actualAccountRole, "actualAccountRole");
    }
  }

  /** Refusal for period close when the retained-earnings account exists but is inactive. */
  record RetainedEarningsAccountInactive(AccountCode accountCode)
      implements BookkeepingAdministrationRejection {
    public RetainedEarningsAccountInactive {
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
