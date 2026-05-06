package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;

/** Local bookkeeping row in one trial-balance view. */
public record TrialBalanceRowView(RegisteredAccount account, CurrencyBalance balance) {
  public TrialBalanceRowView {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(balance, "balance");
  }
}
