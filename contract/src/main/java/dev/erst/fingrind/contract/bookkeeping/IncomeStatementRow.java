package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Objects;

/** One income-statement line for one nominal account. */
public record IncomeStatementRow(
    String lineCode,
    String lineName,
    AccountType lineType,
    ProfitAndLossLineClassification lineClassification,
    StatementLineKind lineKind,
    CurrencyBalance movement) {
  /** Validates one income-statement row. */
  public IncomeStatementRow {
    lineCode =
        dev.erst.fingrind.contract.internal.ContractDescriptorValidation.requireText(
            lineCode, "lineCode");
    lineName =
        dev.erst.fingrind.contract.internal.ContractDescriptorValidation.requireText(
            lineName, "lineName");
    Objects.requireNonNull(lineType, "lineType");
    Objects.requireNonNull(lineClassification, "lineClassification");
    Objects.requireNonNull(lineKind, "lineKind");
    Objects.requireNonNull(movement, "movement");
  }
}
