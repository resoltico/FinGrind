package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;

/** Local bookkeeping income-statement view. */
public record IncomeStatementView(
    BookIdentity bookIdentity,
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    EffectiveDateRange comparativeEffectiveDateRange,
    PostingCoverage postingCoverage,
    List<IncomeStatementSectionView> sections,
    List<CurrencyBalance> netIncomeTotals,
    List<IncomeStatementSectionView> comparativeSections,
    List<CurrencyBalance> comparativeNetIncomeTotals) {
  public IncomeStatementView {
    BookkeepingStatementViewValidation.requireComparativeStatementHeader(
        bookIdentity,
        effectiveDateFrom,
        effectiveDateTo,
        comparativeEffectiveDateRange,
        postingCoverage);
    sections = BookkeepingStatementViewValidation.immutableList("sections", sections);
    netIncomeTotals =
        BookkeepingStatementViewValidation.immutableList("netIncomeTotals", netIncomeTotals);
    comparativeSections =
        BookkeepingStatementViewValidation.immutableList(
            "comparativeSections", comparativeSections);
    comparativeNetIncomeTotals =
        BookkeepingStatementViewValidation.immutableList(
            "comparativeNetIncomeTotals", comparativeNetIncomeTotals);
  }
}
