package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Local bookkeeping criteria for one running account-ledger view. */
public record AccountLedgerCriteria(
    AccountCode accountCode, EffectiveDateRange effectiveDateRange) {
  public AccountLedgerCriteria {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
  }

  /** Convenience constructor that lifts nullable bounds into the shared-kernel date range. */
  public AccountLedgerCriteria(
      AccountCode accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo) {
    this(accountCode, EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo));
  }
}
