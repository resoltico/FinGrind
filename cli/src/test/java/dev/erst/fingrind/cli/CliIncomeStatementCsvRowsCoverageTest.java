package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.IncomeStatementGrossProfitSupport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementPresentationSupport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Coverage for income-statement CSV row insertion around gross-profit fallback placement. */
class CliIncomeStatementCsvRowsCoverageTest extends CliFixtureSupport {
  @Test
  void rows_insertGrossProfitTotalsAfterRevenueWhenNoCostOfSalesSectionExists() {
    IncomeStatementSection revenueSection =
        new IncomeStatementSection(
            AccountType.REVENUE,
            List.of(
                new IncomeStatementRow(
                    "4100",
                    "Sales Revenue",
                    AccountType.REVENUE,
                    ProfitAndLossLineClassification.OPERATING_REVENUE,
                    StatementLineKind.DECLARED_ACCOUNT,
                    CliResponseWriterTestSupport.currencyBalance(
                        "EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT))),
            List.of());
    IncomeStatementReport report =
        new IncomeStatementReport(
            tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(revenueSection),
            List.of(),
            List.of(),
            List.of());
    List<List<String>> rows =
        CliIncomeStatementCsvRows.rows(
                report,
                "current",
                IncomeStatementPresentationSupport.currentSections(report),
                IncomeStatementGrossProfitSupport.grossProfitTotals(report),
                List.of())
            .toList();

    assertTrue(rows.stream().anyMatch(row -> "REVENUE".equals(row.get(8))), rows.toString());
    assertTrue(rows.stream().anyMatch(row -> "GROSS_PROFIT".equals(row.get(8))), rows.toString());
    assertTrue(
        rows.indexOf(
                rows.stream()
                    .filter(row -> "REVENUE".equals(row.get(8)) && "4100".equals(row.get(9)))
                    .findFirst()
                    .orElseThrow())
            < rows.indexOf(
                rows.stream()
                    .filter(row -> "GROSS_PROFIT".equals(row.get(8)))
                    .findFirst()
                    .orElseThrow()),
        rows.toString());
  }

  @Test
  void rows_insertGrossProfitTotalsAfterCostOfSalesForTradingBooks() {
    IncomeStatementSection expenseSection =
        new IncomeStatementSection(
            AccountType.EXPENSE,
            List.of(
                new IncomeStatementRow(
                    "5100",
                    "Cost of Sales",
                    AccountType.EXPENSE,
                    ProfitAndLossLineClassification.COST_OF_SALES,
                    StatementLineKind.DECLARED_ACCOUNT,
                    CliResponseWriterTestSupport.currencyBalance(
                        "EUR", "40.00", "0.00", "40.00", BalanceSide.DEBIT))),
            List.of());
    IncomeStatementReport report =
        new IncomeStatementReport(
            tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(expenseSection),
            List.of(),
            List.of(),
            List.of());
    List<List<String>> rows =
        CliIncomeStatementCsvRows.rows(
                report,
                "current",
                IncomeStatementPresentationSupport.currentSections(report),
                IncomeStatementGrossProfitSupport.grossProfitTotals(report),
                List.of())
            .toList();

    assertTrue(rows.stream().anyMatch(row -> row.contains("GROSS_PROFIT_TOTAL")), rows.toString());
    assertTrue(
        rows.stream().anyMatch(row -> row.contains("income-statement-gross-profit:current:EUR")));
    assertTrue(rows.stream().anyMatch(row -> "COST_OF_SALES".equals(row.get(8))), rows.toString());
    assertTrue(
        rows.indexOf(
                rows.stream()
                    .filter(row -> "COST_OF_SALES".equals(row.get(8)) && "5100".equals(row.get(9)))
                    .findFirst()
                    .orElseThrow())
            < rows.indexOf(
                rows.stream()
                    .filter(row -> "GROSS_PROFIT".equals(row.get(8)))
                    .findFirst()
                    .orElseThrow()),
        rows.toString());
  }

  @Test
  void rows_emitGrossProfitTotalsWhenSectionsDoNotRenderThemInline() {
    IncomeStatementSection revenueSection =
        new IncomeStatementSection(
            AccountType.REVENUE,
            List.of(
                new IncomeStatementRow(
                    "4100",
                    "Sales Revenue",
                    AccountType.REVENUE,
                    ProfitAndLossLineClassification.OPERATING_REVENUE,
                    StatementLineKind.DECLARED_ACCOUNT,
                    CliResponseWriterTestSupport.currencyBalance(
                        "EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT))),
            List.of());
    IncomeStatementReport report =
        new IncomeStatementReport(
            tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(revenueSection),
            List.of(),
            List.of(),
            List.of());

    List<List<String>> rows =
        CliIncomeStatementCsvRows.rows(
                report,
                "current",
                List.of(),
                IncomeStatementGrossProfitSupport.grossProfitTotals(report),
                List.of())
            .toList();

    assertTrue(rows.stream().anyMatch(row -> "GROSS_PROFIT".equals(row.get(8))), rows.toString());
  }

  @Test
  void rows_separateTradingCostOfSalesFromExpenses() {
    IncomeStatementSection firstRevenueSection =
        new IncomeStatementSection(
            AccountType.REVENUE,
            List.of(
                new IncomeStatementRow(
                    "4100",
                    "Sales Revenue",
                    AccountType.REVENUE,
                    ProfitAndLossLineClassification.OPERATING_REVENUE,
                    StatementLineKind.DECLARED_ACCOUNT,
                    CliResponseWriterTestSupport.currencyBalance(
                        "EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT))),
            List.of());
    IncomeStatementSection expenseSection =
        new IncomeStatementSection(
            AccountType.EXPENSE,
            List.of(
                new IncomeStatementRow(
                    "5100",
                    "Cost of Sales",
                    AccountType.EXPENSE,
                    ProfitAndLossLineClassification.COST_OF_SALES,
                    StatementLineKind.DECLARED_ACCOUNT,
                    CliResponseWriterTestSupport.currencyBalance(
                        "EUR", "40.00", "0.00", "40.00", BalanceSide.DEBIT)),
                new IncomeStatementRow(
                    "6100",
                    "Operating Expense",
                    AccountType.EXPENSE,
                    ProfitAndLossLineClassification.OPERATING_EXPENSE,
                    StatementLineKind.DECLARED_ACCOUNT,
                    CliResponseWriterTestSupport.currencyBalance(
                        "EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
            List.of());
    IncomeStatementReport report =
        new IncomeStatementReport(
            tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(firstRevenueSection, expenseSection),
            List.of(),
            List.of(),
            List.of());
    List<List<String>> rows =
        CliIncomeStatementCsvRows.rows(
                report,
                "current",
                IncomeStatementPresentationSupport.currentSections(report),
                IncomeStatementGrossProfitSupport.grossProfitTotals(report),
                List.of())
            .toList();

    assertTrue(
        rows.stream()
            .anyMatch(row -> "COST_OF_SALES".equals(row.get(8)) && "5100".equals(row.get(9))),
        rows.toString());
    assertTrue(
        rows.stream().noneMatch(row -> "EXPENSE".equals(row.get(8)) && "5100".equals(row.get(9))),
        rows.toString());
  }

  @Test
  void rows_emitOneReportEmptyRowWhenNoRenderableRowsExist() {
    IncomeStatementReport report =
        new IncomeStatementReport(
            tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    List<List<String>> rows =
        CliIncomeStatementCsvRows.rows(report, "current", List.of(), List.of(), List.of()).toList();

    assertEquals(1, rows.size());
    assertEquals("report-empty", rows.getFirst().get(3));
    assertTrue(
        rows.getFirst().contains(CliQueryScopeText.noMatchesLabel("income statement lines")));
  }
}
