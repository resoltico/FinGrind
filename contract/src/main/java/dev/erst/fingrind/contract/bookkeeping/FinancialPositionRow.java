package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;
import java.util.Optional;

/** One statement-of-financial-position line for one account or synthetic equity bucket. */
public record FinancialPositionRow(
    String lineCode,
    String lineName,
    AccountType lineType,
    Optional<AccountRole> lineRole,
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
    lineRole =
        dev.erst.fingrind.contract.internal.ContractDescriptorValidation.requireValue(
            lineRole, "lineRole");
    Objects.requireNonNull(balance, "balance");
  }
}
