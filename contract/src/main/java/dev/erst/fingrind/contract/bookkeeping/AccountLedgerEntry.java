package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.Money;
import java.util.Objects;

/** One running-balance activity row inside an account-ledger report. */
public record AccountLedgerEntry(
    PostingFact postingFact,
    CurrencyBalance movement,
    Money runningNetAmount,
    BalanceSide runningBalanceSide) {
  /** Validates one account-ledger activity row. */
  public AccountLedgerEntry {
    Objects.requireNonNull(postingFact, "postingFact");
    Objects.requireNonNull(movement, "movement");
    Objects.requireNonNull(runningNetAmount, "runningNetAmount");
    Objects.requireNonNull(runningBalanceSide, "runningBalanceSide");
  }
}
