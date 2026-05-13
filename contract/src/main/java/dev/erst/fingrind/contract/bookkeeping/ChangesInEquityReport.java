package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Canonical statement of changes in equity for one bounded reporting period. */
public record ChangesInEquityReport(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    List<ChangesInEquityRow> rows,
    List<CurrencyBalance> openingTotals,
    List<CurrencyBalance> movementTotals,
    List<CurrencyBalance> closingTotals) {
  /** Validates one changes-in-equity report. */
  public ChangesInEquityReport {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    rows = ContractDescriptorValidation.copyList(rows, "rows");
    openingTotals = ContractDescriptorValidation.copyList(openingTotals, "openingTotals");
    movementTotals = ContractDescriptorValidation.copyList(movementTotals, "movementTotals");
    closingTotals = ContractDescriptorValidation.copyList(closingTotals, "closingTotals");
  }
}
