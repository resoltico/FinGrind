package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping line inside one income statement section. */
public record IncomeStatementRowView(
    String lineCode,
    String lineName,
    AccountType lineType,
    Optional<AccountRole> lineRole,
    ProfitAndLossLineClassification lineClassification,
    StatementLineKind lineKind,
    CurrencyBalance movement) {
  public IncomeStatementRowView {
    Objects.requireNonNull(lineCode, "lineCode");
    Objects.requireNonNull(lineName, "lineName");
    Objects.requireNonNull(lineType, "lineType");
    Objects.requireNonNull(lineRole, "lineRole");
    Objects.requireNonNull(lineClassification, "lineClassification");
    Objects.requireNonNull(lineKind, "lineKind");
    Objects.requireNonNull(movement, "movement");
  }
}
