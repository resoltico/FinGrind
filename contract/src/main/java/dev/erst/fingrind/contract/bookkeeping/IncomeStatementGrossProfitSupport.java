package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.List;
import java.util.Objects;

/** Derives gross-profit subtotals for trading-doctrine income statements. */
public final class IncomeStatementGrossProfitSupport {
  private IncomeStatementGrossProfitSupport() {}

  /** Returns gross-profit totals for the current reporting window. */
  public static List<CurrencyBalance> grossProfitTotals(IncomeStatementReport report) {
    Objects.requireNonNull(report, "report");
    return grossProfitTotals(
        report.bookIdentity().bookDoctrine().bookTemplateId(), report.sections());
  }

  /** Returns gross-profit totals for the comparative reporting window. */
  public static List<CurrencyBalance> comparativeGrossProfitTotals(IncomeStatementReport report) {
    Objects.requireNonNull(report, "report");
    return grossProfitTotals(
        report.bookIdentity().bookDoctrine().bookTemplateId(), report.comparativeSections());
  }

  private static List<CurrencyBalance> grossProfitTotals(
      BookTemplateId bookTemplateId, List<IncomeStatementSection> sections) {
    if (Objects.requireNonNull(bookTemplateId, "bookTemplateId")
        != BookTemplateId.OWNER_MANAGED_TRADING) {
      return List.of();
    }
    List<IncomeStatementRow> selectedRows =
        Objects.requireNonNull(sections, "sections").stream()
            .flatMap(section -> section.rows().stream())
            .filter(IncomeStatementPresentationSupport::contributesToGrossProfit)
            .toList();
    if (selectedRows.isEmpty()) {
      return List.of();
    }
    return IncomeStatementPresentationSupport.aggregateRows(selectedRows);
  }
}
