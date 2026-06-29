package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Objects;
import java.util.Optional;

/** One statement-of-financial-position line for one account or synthetic equity bucket. */
public record FinancialPositionRow(
    String lineCode,
    String lineName,
    AccountType lineType,
    Optional<FinancialPositionLineClassification> lineClassification,
    StatementLineKind lineKind,
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
    lineClassification =
        dev.erst.fingrind.contract.internal.ContractDescriptorValidation.requireValue(
            lineClassification, "lineClassification");
    Objects.requireNonNull(lineKind, "lineKind");
    Objects.requireNonNull(balance, "balance");
  }
}
