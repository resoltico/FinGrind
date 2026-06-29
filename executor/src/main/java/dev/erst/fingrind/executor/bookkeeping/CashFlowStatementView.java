package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;

/** Local bookkeeping statement-of-cash-receipts-and-payments view. */
public record CashFlowStatementView(
    BookIdentity bookIdentity,
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    EffectiveDateRange comparativeEffectiveDateRange,
    PostingCoverage postingCoverage,
    List<CurrencyBalance> openingCashTotals,
    List<CashFlowSectionView> sections,
    List<CurrencyBalance> movementTotals,
    List<CurrencyBalance> closingCashTotals,
    List<CurrencyBalance> comparativeOpeningCashTotals,
    List<CashFlowSectionView> comparativeSections,
    List<CurrencyBalance> comparativeMovementTotals,
    List<CurrencyBalance> comparativeClosingCashTotals) {
  public CashFlowStatementView {
    BookkeepingStatementViewValidation.requireComparativeStatementHeader(
        bookIdentity,
        effectiveDateFrom,
        effectiveDateTo,
        comparativeEffectiveDateRange,
        postingCoverage);
    openingCashTotals =
        BookkeepingStatementViewValidation.immutableList("openingCashTotals", openingCashTotals);
    sections = BookkeepingStatementViewValidation.immutableList("sections", sections);
    movementTotals =
        BookkeepingStatementViewValidation.immutableList("movementTotals", movementTotals);
    closingCashTotals =
        BookkeepingStatementViewValidation.immutableList("closingCashTotals", closingCashTotals);
    comparativeOpeningCashTotals =
        BookkeepingStatementViewValidation.immutableList(
            "comparativeOpeningCashTotals", comparativeOpeningCashTotals);
    comparativeSections =
        BookkeepingStatementViewValidation.immutableList(
            "comparativeSections", comparativeSections);
    comparativeMovementTotals =
        BookkeepingStatementViewValidation.immutableList(
            "comparativeMovementTotals", comparativeMovementTotals);
    comparativeClosingCashTotals =
        BookkeepingStatementViewValidation.immutableList(
            "comparativeClosingCashTotals", comparativeClosingCashTotals);
  }
}
