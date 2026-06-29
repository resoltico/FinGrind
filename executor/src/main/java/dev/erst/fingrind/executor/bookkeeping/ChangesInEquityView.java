package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;

/** Local bookkeeping statement-of-changes-in-equity view. */
public record ChangesInEquityView(
    BookIdentity bookIdentity,
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    EffectiveDateRange comparativeEffectiveDateRange,
    PostingCoverage postingCoverage,
    List<ChangesInEquityRowView> rows,
    List<CurrencyBalance> openingTotals,
    List<CurrencyBalance> movementTotals,
    List<CurrencyBalance> closingTotals,
    List<ChangesInEquityRowView> comparativeRows,
    List<CurrencyBalance> comparativeOpeningTotals,
    List<CurrencyBalance> comparativeMovementTotals,
    List<CurrencyBalance> comparativeClosingTotals) {
  public ChangesInEquityView {
    BookkeepingStatementViewValidation.requireComparativeStatementHeader(
        bookIdentity,
        effectiveDateFrom,
        effectiveDateTo,
        comparativeEffectiveDateRange,
        postingCoverage);
    rows = BookkeepingStatementViewValidation.immutableList("rows", rows);
    openingTotals =
        BookkeepingStatementViewValidation.immutableList("openingTotals", openingTotals);
    movementTotals =
        BookkeepingStatementViewValidation.immutableList("movementTotals", movementTotals);
    closingTotals =
        BookkeepingStatementViewValidation.immutableList("closingTotals", closingTotals);
    comparativeRows =
        BookkeepingStatementViewValidation.immutableList("comparativeRows", comparativeRows);
    comparativeOpeningTotals =
        BookkeepingStatementViewValidation.immutableList(
            "comparativeOpeningTotals", comparativeOpeningTotals);
    comparativeMovementTotals =
        BookkeepingStatementViewValidation.immutableList(
            "comparativeMovementTotals", comparativeMovementTotals);
    comparativeClosingTotals =
        BookkeepingStatementViewValidation.immutableList(
            "comparativeClosingTotals", comparativeClosingTotals);
  }
}
