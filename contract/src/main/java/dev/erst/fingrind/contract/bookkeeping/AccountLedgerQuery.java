package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Filter request for one account-ledger report. */
public record AccountLedgerQuery(AccountCode accountCode, EffectiveDateRange effectiveDateRange) {
  /** Validates one account-ledger query. */
  public AccountLedgerQuery {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
  }

  /** Convenience constructor that lifts nullable date bounds into a typed range. */
  public AccountLedgerQuery(
      AccountCode accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo) {
    this(accountCode, EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo));
  }

  /** Returns the optional lower effective-date bound carried by this query. */
  public Optional<LocalDate> effectiveDateFrom() {
    return effectiveDateRange.effectiveDateFrom();
  }

  /** Returns the optional upper effective-date bound carried by this query. */
  public Optional<LocalDate> effectiveDateTo() {
    return effectiveDateRange.effectiveDateTo();
  }
}
