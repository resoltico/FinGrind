package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Filter request for computing balances on one declared account. */
public record AccountBalanceQuery(AccountCode accountCode, EffectiveDateRange effectiveDateRange) {
  /** Validates one account-balance query. */
  public AccountBalanceQuery {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
  }

  /** Convenience constructor that lifts nullable bounds into a typed range. */
  public AccountBalanceQuery(
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
