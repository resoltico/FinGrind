package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping line inside one income statement section. */
public record IncomeStatementRowView(
    String lineCode,
    String lineName,
    AccountType lineType,
    Optional<AccountRole> lineRole,
    boolean synthetic,
    CurrencyBalance movement) {
  public IncomeStatementRowView {
    Objects.requireNonNull(lineCode, "lineCode");
    Objects.requireNonNull(lineName, "lineName");
    Objects.requireNonNull(lineType, "lineType");
    Objects.requireNonNull(lineRole, "lineRole");
    Objects.requireNonNull(movement, "movement");
  }
}
