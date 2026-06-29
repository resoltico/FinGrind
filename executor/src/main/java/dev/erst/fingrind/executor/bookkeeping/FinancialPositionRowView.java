package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping line inside one statement-of-financial-position section. */
public record FinancialPositionRowView(
    String lineCode,
    String lineName,
    AccountType lineType,
    Optional<FinancialPositionLineClassification> lineClassification,
    StatementLineKind lineKind,
    CurrencyBalance balance) {
  public FinancialPositionRowView {
    Objects.requireNonNull(lineCode, "lineCode");
    Objects.requireNonNull(lineName, "lineName");
    Objects.requireNonNull(lineType, "lineType");
    Objects.requireNonNull(lineClassification, "lineClassification");
    Objects.requireNonNull(lineKind, "lineKind");
    Objects.requireNonNull(balance, "balance");
  }
}
