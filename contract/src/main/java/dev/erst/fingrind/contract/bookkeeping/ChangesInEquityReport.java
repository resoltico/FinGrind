package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;

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
    var statementWindow =
        BookkeepingComparativeReportValidation.requireStatementWindow(
            bookIdentity,
            effectiveDateFrom,
            effectiveDateTo,
            comparativeEffectiveDateRange,
            postingCoverage);
    bookIdentity = statementWindow.bookIdentity();
    effectiveDateFrom = statementWindow.effectiveDateFrom();
    effectiveDateTo = statementWindow.effectiveDateTo();
    comparativeEffectiveDateRange = statementWindow.comparativeEffectiveDateRange();
    postingCoverage = statementWindow.postingCoverage();
    var totals =
        BookkeepingComparativeReportValidation.copyComparativeCurrencyBalances(
            openingTotals,
            movementTotals,
            closingTotals,
            comparativeOpeningTotals,
            comparativeMovementTotals,
            comparativeClosingTotals);
    rows = ContractDescriptorValidation.copyList(rows, "rows");
    openingTotals = totals.openingTotals();
    movementTotals = totals.movementTotals();
    closingTotals = totals.closingTotals();
    comparativeRows = ContractDescriptorValidation.copyList(comparativeRows, "comparativeRows");
    comparativeOpeningTotals = totals.comparativeOpeningTotals();
    comparativeMovementTotals = totals.comparativeMovementTotals();
    comparativeClosingTotals = totals.comparativeClosingTotals();
  }
}
