package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.Money;
import java.util.Objects;

/** Local bookkeeping running-ledger row for one committed posting. */
public record AccountLedgerEntryView(
    CommittedPosting posting,
    CurrencyBalance movement,
    Money runningNetAmount,
    BalanceSide runningBalanceSide) {
  public AccountLedgerEntryView {
    Objects.requireNonNull(posting, "posting");
    Objects.requireNonNull(movement, "movement");
    Objects.requireNonNull(runningNetAmount, "runningNetAmount");
    Objects.requireNonNull(runningBalanceSide, "runningBalanceSide");
  }
}
