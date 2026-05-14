package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;
import java.util.Optional;

/** One statement-of-changes-in-equity row for one equity line or synthetic current earnings. */
public record ChangesInEquityRow(
    String lineCode,
    String lineName,
    Optional<AccountType> lineType,
    Optional<AccountRole> lineRole,
    boolean synthetic,
    CurrencyBalance openingBalance,
    CurrencyBalance movement,
    CurrencyBalance closingBalance) {
  /** Validates one changes-in-equity row. */
  public ChangesInEquityRow {
    lineCode = ContractDescriptorValidation.requireText(lineCode, "lineCode");
    lineName = ContractDescriptorValidation.requireText(lineName, "lineName");
    lineType = ContractDescriptorValidation.requireValue(lineType, "lineType");
    lineRole = ContractDescriptorValidation.requireValue(lineRole, "lineRole");
    Objects.requireNonNull(openingBalance, "openingBalance");
    Objects.requireNonNull(movement, "movement");
    Objects.requireNonNull(closingBalance, "closingBalance");
  }
}
