package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Canonical statement of changes in equity for one bounded reporting period. */
public record ChangesInEquityReport(
    BookIdentity bookIdentity,
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    EffectiveDateRange comparativeEffectiveDateRange,
    PostingCoverage postingCoverage,
    List<ChangesInEquityRow> rows,
    List<CurrencyBalance> openingTotals,
    List<CurrencyBalance> movementTotals,
    List<CurrencyBalance> closingTotals,
    List<ChangesInEquityRow> comparativeRows,
    List<CurrencyBalance> comparativeOpeningTotals,
    List<CurrencyBalance> comparativeMovementTotals,
    List<CurrencyBalance> comparativeClosingTotals) {
  /** Validates one changes-in-equity report. */
  public ChangesInEquityReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    Objects.requireNonNull(comparativeEffectiveDateRange, "comparativeEffectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
    openingTotals = ContractDescriptorValidation.copyList(openingTotals, "openingTotals");
    movementTotals = ContractDescriptorValidation.copyList(movementTotals, "movementTotals");
    closingTotals = ContractDescriptorValidation.copyList(closingTotals, "closingTotals");
    comparativeRows = ContractDescriptorValidation.copyList(comparativeRows, "comparativeRows");
    comparativeOpeningTotals =
        ContractDescriptorValidation.copyList(comparativeOpeningTotals, "comparativeOpeningTotals");
    comparativeMovementTotals =
        ContractDescriptorValidation.copyList(
            comparativeMovementTotals, "comparativeMovementTotals");
    comparativeClosingTotals =
        ContractDescriptorValidation.copyList(comparativeClosingTotals, "comparativeClosingTotals");
  }
}
