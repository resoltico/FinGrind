package dev.erst.fingrind.contract.reportmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.IncomeStatementGrossProfitSupport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementPresentationSupport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Coverage tests for trading-doctrine gross-profit support and multi-step presentation. */
class IncomeStatementTradingCoverageTest {
  @Test
  void grossProfitTotals_returnsEmptyWhenTradingReportHasNoRevenueOrCostOfSalesLines() {
    IncomeStatementReport report =
        new IncomeStatementReport(
            ReportModelTestSupport.tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                section(
                    AccountType.EXPENSE,
                    List.of(
                        ReportModelTestSupport.incomeStatementRow(
                            "6100",
                            "Operating Expense",
                            AccountType.EXPENSE,
                            ProfitAndLossLineClassification.OPERATING_EXPENSE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            ReportModelTestSupport.balance("EUR", "20.00", "0.00"))),
                    List.of(ReportModelTestSupport.balance("EUR", "20.00", "0.00")))),
            List.of(ReportModelTestSupport.balance("EUR", "20.00", "0.00")),
            List.of(),
            List.of());

    assertTrue(IncomeStatementGrossProfitSupport.grossProfitTotals(report).isEmpty());
    assertTrue(IncomeStatementGrossProfitSupport.comparativeGrossProfitTotals(report).isEmpty());
  }

  @Test
  void grossProfitTotals_aggregateAndSortMultipleCurrencies() {
    IncomeStatementReport report =
        new IncomeStatementReport(
            ReportModelTestSupport.tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                section(
                    AccountType.REVENUE,
                    List.of(
                        ReportModelTestSupport.incomeStatementRow(
                            "4100",
                            "Sales Revenue EUR",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            ReportModelTestSupport.balance("EUR", "0.00", "100.00")),
                        ReportModelTestSupport.incomeStatementRow(
                            "4200",
                            "Sales Revenue USD",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            ReportModelTestSupport.balance("USD", "0.00", "50.00"))),
                    List.of()),
                section(
                    AccountType.EXPENSE,
                    List.of(
                        ReportModelTestSupport.incomeStatementRow(
                            "5100",
                            "Cost of Sales EUR",
                            AccountType.EXPENSE,
                            ProfitAndLossLineClassification.COST_OF_SALES,
                            StatementLineKind.DECLARED_ACCOUNT,
                            ReportModelTestSupport.balance("EUR", "30.00", "0.00")),
                        ReportModelTestSupport.incomeStatementRow(
                            "5200",
                            "Cost of Sales USD",
                            AccountType.EXPENSE,
                            ProfitAndLossLineClassification.COST_OF_SALES,
                            StatementLineKind.DECLARED_ACCOUNT,
                            ReportModelTestSupport.balance("USD", "20.00", "0.00"))),
                    List.of())),
            List.of(),
            List.of(),
            List.of());

    assertEquals(
        List.of(
            ReportModelTestSupport.balance("EUR", "30.00", "100.00"),
            ReportModelTestSupport.balance("USD", "20.00", "50.00")),
        IncomeStatementGrossProfitSupport.grossProfitTotals(report));
  }

  @Test
  void incomeStatementBuilder_projectsMultiStepTradingSectionsWithoutDoubleCountingCostOfSales() {
    IncomeStatementReport report =
        new IncomeStatementReport(
            ReportModelTestSupport.tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                section(AccountType.REVENUE, List.of(), List.of()),
                section(
                    AccountType.REVENUE,
                    List.of(
                        ReportModelTestSupport.incomeStatementRow(
                            "4100",
                            "Sales Revenue",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            ReportModelTestSupport.balance("EUR", "0.00", "100.00"))),
                    List.of(ReportModelTestSupport.balance("EUR", "0.00", "100.00"))),
                section(
                    AccountType.EXPENSE,
                    List.of(
                        ReportModelTestSupport.incomeStatementRow(
                            "5100",
                            "Cost of Sales",
                            AccountType.EXPENSE,
                            ProfitAndLossLineClassification.COST_OF_SALES,
                            StatementLineKind.DECLARED_ACCOUNT,
                            ReportModelTestSupport.balance("EUR", "40.00", "0.00")),
                        ReportModelTestSupport.incomeStatementRow(
                            "6100",
                            "Operating Expense",
                            AccountType.EXPENSE,
                            ProfitAndLossLineClassification.OPERATING_EXPENSE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            ReportModelTestSupport.balance("EUR", "10.00", "0.00"))),
                    List.of(ReportModelTestSupport.balance("EUR", "50.00", "0.00")))),
            List.of(ReportModelTestSupport.balance("EUR", "50.00", "100.00")),
            List.of(),
            List.of());

    ReportModel model = IncomeStatementReportModelBuilder.buildModel(report);
    List<String> sectionKeys = model.sections().stream().map(ReportSection::key).toList();

    assertIterableEquals(
        List.of(
            "current-REVENUE",
            "current-COST_OF_SALES",
            "current-gross-profit",
            "current-EXPENSE",
            "current-net-income"),
        sectionKeys);
    assertEquals(
        "EUR 60.00", section(model, "current-gross-profit").rows().getFirst().cells().get(3));
    assertEquals(
        List.of("6100:EUR"),
        section(model, "current-EXPENSE").rows().stream().map(ReportRow::rowId).toList());
    assertEquals(
        List.of("5100:EUR"),
        section(model, "current-COST_OF_SALES").rows().stream().map(ReportRow::rowId).toList());
  }

  @Test
  void
      incomeStatementBuilder_insertsGrossProfitImmediatelyAfterRevenueWhenNoCostOfSalesSectionExists() {
    IncomeStatementReport report =
        new IncomeStatementReport(
            ReportModelTestSupport.tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                section(
                    AccountType.REVENUE,
                    List.of(
                        ReportModelTestSupport.incomeStatementRow(
                            "4100",
                            "Sales Revenue",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            ReportModelTestSupport.balance("EUR", "0.00", "100.00"))),
                    List.of(ReportModelTestSupport.balance("EUR", "0.00", "100.00"))),
                section(AccountType.EXPENSE, List.of(), List.of())),
            List.of(ReportModelTestSupport.balance("EUR", "0.00", "100.00")),
            List.of(),
            List.of());

    ReportModel model = IncomeStatementReportModelBuilder.buildModel(report);

    assertIterableEquals(
        List.of("current-REVENUE", "current-gross-profit", "current-net-income"),
        model.sections().stream().map(ReportSection::key).toList());
    assertEquals(
        "EUR 100.00", section(model, "current-gross-profit").rows().getFirst().cells().get(3));
  }

  @Test
  void grossProfitInsertionHelper_coversNonRenderableAndRenderableSectionBranches() {
    List<ReportSection> rendered = new ArrayList<>();
    List<dev.erst.fingrind.core.CurrencyBalance> grossProfitTotals =
        List.of(ReportModelTestSupport.balance("EUR", "40.00", "100.00"));
    IncomeStatementPresentationSupport.PresentationSection emptyCostOfSales =
        new IncomeStatementPresentationSupport.PresentationSection(
            IncomeStatementPresentationSupport.SectionCode.COST_OF_SALES, List.of(), List.of());
    IncomeStatementPresentationSupport.PresentationSection emptyRevenue =
        new IncomeStatementPresentationSupport.PresentationSection(
            IncomeStatementPresentationSupport.SectionCode.REVENUE, List.of(), List.of());
    IncomeStatementPresentationSupport.PresentationSection expenseSection =
        new IncomeStatementPresentationSupport.PresentationSection(
            IncomeStatementPresentationSupport.SectionCode.EXPENSE,
            List.of(
                ReportModelTestSupport.incomeStatementRow(
                    "6100",
                    "Operating Expense",
                    AccountType.EXPENSE,
                    ProfitAndLossLineClassification.OPERATING_EXPENSE,
                    StatementLineKind.DECLARED_ACCOUNT,
                    ReportModelTestSupport.balance("EUR", "10.00", "0.00"))),
            List.of(ReportModelTestSupport.balance("EUR", "10.00", "0.00")));

    assertFalse(
        IncomeStatementReportModelBuilder.insertGrossProfitSectionIfNeeded(
            rendered, "current", "", emptyCostOfSales, true, false, grossProfitTotals));
    assertFalse(
        IncomeStatementReportModelBuilder.insertGrossProfitSectionIfNeeded(
            rendered, "current", "", emptyRevenue, false, false, grossProfitTotals));
    assertFalse(
        IncomeStatementReportModelBuilder.insertGrossProfitSectionIfNeeded(
            rendered, "current", "", expenseSection, false, false, grossProfitTotals));
    assertTrue(rendered.isEmpty());
  }

  private static ReportSection section(ReportModel model, String key) {
    return model.sections().stream()
        .filter(section -> key.equals(section.key()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing section " + key));
  }

  private static IncomeStatementSection section(
      AccountType accountType,
      List<dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow> rows,
      List<dev.erst.fingrind.core.CurrencyBalance> totals) {
    return new IncomeStatementSection(accountType, rows, totals);
  }
}
