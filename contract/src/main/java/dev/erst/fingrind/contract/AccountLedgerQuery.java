package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Filter request for one account-ledger report. */
public record AccountLedgerQuery(AccountCode accountCode, EffectiveDateRange effectiveDateRange) {
  /** Validates one account-ledger query. */
  public AccountLedgerQuery {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
  }

  /** Compatibility constructor that lifts optional date bounds into a typed range. */
  public AccountLedgerQuery(
      AccountCode accountCode,
      Optional<LocalDate> effectiveDateFrom,
      Optional<LocalDate> effectiveDateTo) {
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
