package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;

/** One flattened account-and-currency row inside a trial balance report. */
public record TrialBalanceRow(DeclaredAccount account, CurrencyBalance balance) {
  /** Validates one trial-balance row. */
  public TrialBalanceRow {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(balance, "balance");
  }
}
