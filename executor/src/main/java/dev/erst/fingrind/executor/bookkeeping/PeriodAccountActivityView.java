package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;

/** Local bookkeeping per-account activity row inside one period-summary view. */
public record PeriodAccountActivityView(RegisteredAccount account, CurrencyBalance movement) {
  public PeriodAccountActivityView {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(movement, "movement");
  }
}
