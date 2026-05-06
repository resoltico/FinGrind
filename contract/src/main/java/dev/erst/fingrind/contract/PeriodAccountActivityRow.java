package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;

/** One flattened account-and-currency activity row inside a period summary. */
public record PeriodAccountActivityRow(DeclaredAccount account, CurrencyBalance movement) {
  /** Validates one period account-activity row. */
  public PeriodAccountActivityRow {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(movement, "movement");
  }
}
