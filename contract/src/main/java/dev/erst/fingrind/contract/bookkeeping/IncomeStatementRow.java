package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;

/** One income-statement line for one nominal account. */
public record IncomeStatementRow(
    String lineCode,
    String lineName,
    AccountType lineType,
    boolean synthetic,
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
    Objects.requireNonNull(movement, "movement");
  }
}
