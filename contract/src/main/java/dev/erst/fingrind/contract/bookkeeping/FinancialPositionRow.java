package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;

/** One statement-of-financial-position line for one account or synthetic equity bucket. */
public record FinancialPositionRow(
    String lineCode,
    String lineName,
    AccountType lineType,
    boolean synthetic,
    CurrencyBalance balance) {
  /** Validates one financial-position statement row. */
  public FinancialPositionRow {
    lineCode =
        dev.erst.fingrind.contract.internal.ContractDescriptorValidation.requireText(
            lineCode, "lineCode");
    lineName =
        dev.erst.fingrind.contract.internal.ContractDescriptorValidation.requireText(
            lineName, "lineName");
    Objects.requireNonNull(lineType, "lineType");
    Objects.requireNonNull(balance, "balance");
  }
}
