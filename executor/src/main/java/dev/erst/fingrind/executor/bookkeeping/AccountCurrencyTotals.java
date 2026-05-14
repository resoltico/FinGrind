package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import java.util.Objects;

/** Exact debit and credit totals for one declared account in one currency bucket. */
public record AccountCurrencyTotals(
    RegisteredAccount account,
    CurrencyUnit currencyUnit,
    long debitTotalMinor,
    long creditTotalMinor) {
  /** Validates one aggregated account/currency total bucket. */
  public AccountCurrencyTotals {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(currencyUnit, "currencyUnit");
    if (debitTotalMinor < 0L) {
      throw new IllegalArgumentException("debitTotalMinor must be non-negative.");
    }
    if (creditTotalMinor < 0L) {
      throw new IllegalArgumentException("creditTotalMinor must be non-negative.");
    }
  }

  /** Projects this exact debit/credit bucket into the canonical balance view. */
  public CurrencyBalance balance() {
    return BalanceMath.currencyBalance(currencyUnit, debitTotalMinor, creditTotalMinor);
  }
}
