package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping row inside one statement of changes in equity. */
public record ChangesInEquityRowView(
    String lineCode,
    String lineName,
    Optional<AccountType> lineType,
    Optional<AccountRole> lineRole,
    FinancialPositionLineClassification lineClassification,
    StatementLineKind lineKind,
    CurrencyBalance openingBalance,
    CurrencyBalance movement,
    CurrencyBalance closingBalance) {
  public ChangesInEquityRowView {
    Objects.requireNonNull(lineCode, "lineCode");
    Objects.requireNonNull(lineName, "lineName");
    Objects.requireNonNull(lineType, "lineType");
    Objects.requireNonNull(lineRole, "lineRole");
    Objects.requireNonNull(lineClassification, "lineClassification");
    Objects.requireNonNull(lineKind, "lineKind");
    Objects.requireNonNull(openingBalance, "openingBalance");
    Objects.requireNonNull(movement, "movement");
    Objects.requireNonNull(closingBalance, "closingBalance");
  }
}
