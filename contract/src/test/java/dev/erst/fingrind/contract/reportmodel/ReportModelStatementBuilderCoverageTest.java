package dev.erst.fingrind.contract.reportmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for statement-oriented shared report-model builders. */
class ReportModelStatementBuilderCoverageTest {
  @Test
  void incomeStatementBuilder_coversComparativeEntryBranches() {
    ReportModel populated =
        IncomeStatementReportModelBuilder.INSTANCE.build(populatedIncomeStatementReport());
    ReportModel comparativeOutcome =
        IncomeStatementReportModelBuilder.INSTANCE.build(comparativeOutcomeIncomeStatementReport());
    ReportModel netIncomeOnly =
        IncomeStatementReportModelBuilder.INSTANCE.build(netIncomeOnlyIncomeStatementReport());
    ReportModel netIncomeOnlyNoReference =
        IncomeStatementReportModelBuilder.INSTANCE.build(
            netIncomeOnlyNoReferenceIncomeStatementReport());
    ReportModel comparativeSectionsOnly =
        IncomeStatementReportModelBuilder.INSTANCE.build(
            comparativeSectionsOnlyIncomeStatementReport());
    ReportModel noComparative =
        IncomeStatementReportModelBuilder.INSTANCE.build(noComparativeIncomeStatementReport());

    assertEquals("income-statement", populated.family());
    assertTrue(hasVerdict(populated.verdicts(), "Sections with data"));
    assertTrue(hasVerdict(populated.verdicts(), "Empty sections"));
    assertTrue(
        hasVerdict(section(populated, "comparativeSummary").verdicts(), "Sections with data"));
    assertTrue(hasVerdict(section(populated, "comparativeSummary").verdicts(), "Empty sections"));
    assertTrue(hasVerdict(section(comparativeOutcome, "comparativeSummary").verdicts(), "Outcome"));
    assertTrue(
        hasVerdict(
            section(netIncomeOnly, "comparativeSummary").verdicts(), "Comparative reference"));
    assertFalse(hasVerdict(section(netIncomeOnly, "comparativeSummary").verdicts(), "Outcome"));
    assertFalse(
        hasVerdict(section(netIncomeOnly, "comparativeSummary").verdicts(), "Sections with data"));
    assertEquals(
        "2026-04-01", verdictValue(section(netIncomeOnly, "comparativeSummary"), "Period start"));
    assertEquals(
        "2025-04-30", verdictValue(section(netIncomeOnly, "comparativeSummary"), "Period end"));
    assertTrue(
        hasVerdict(
            section(comparativeSectionsOnly, "comparativeSummary").verdicts(),
            "Sections with data"));
    assertTrue(
        hasVerdict(
            section(comparativeSectionsOnly, "comparative-net-income").verdicts(), "Outcome"));
    assertTrue(containsSection(netIncomeOnlyNoReference, "comparativeSummary"));
    assertFalse(containsSection(noComparative, "comparativeSummary"));
    assertFalse(containsSection(noComparative, "comparative-net-income"));
  }

  @Test
  void incomeStatementBuilder_projectsGrossProfitForTradingDoctrine() {
    ReportModel trading =
        IncomeStatementReportModelBuilder.INSTANCE.build(tradingIncomeStatementReport());

    assertTrue(containsSection(trading, "current-COST_OF_SALES"));
    assertTrue(containsSection(trading, "current-gross-profit"));
    assertEquals(
        "EUR 60.00", section(trading, "current-gross-profit").rows().get(0).cells().get(3));
    assertEquals(
        List.of("6100:EUR"),
        section(trading, "current-EXPENSE").rows().stream().map(ReportRow::rowId).toList());
    assertTrue(containsSection(trading, "comparative-gross-profit"));
    assertEquals(
        "EUR 25.00", section(trading, "comparative-gross-profit").rows().get(0).cells().get(3));
  }

  @Test
  void cashFlowBuilder_coversSummaryAndComparativePermutations() {
    ReportModel populated =
        CashFlowStatementReportModelBuilder.INSTANCE.build(populatedCashFlowStatementReport());
    ReportModel empty =
        CashFlowStatementReportModelBuilder.INSTANCE.build(emptyCashFlowStatementReport());
    ReportModel currentMovementOnly =
        CashFlowStatementReportModelBuilder.INSTANCE.build(
            currentMovementOnlyCashFlowStatementReport());
    ReportModel comparativeWithoutTotals =
        CashFlowStatementReportModelBuilder.INSTANCE.build(
            comparativeWithoutTotalsCashFlowStatementReport());
    ReportModel comparativeSectionsOnly =
        CashFlowStatementReportModelBuilder.INSTANCE.build(
            comparativeSectionsOnlyCashFlowStatementReport());
    ReportModel comparativeOpeningOnly =
        CashFlowStatementReportModelBuilder.INSTANCE.build(
            comparativeTotalsOnlyCashFlowStatementReport(
                List.of(ReportModelTestSupport.balance("EUR", "8.00", "0.00")),
                List.of(),
                List.of()));
    ReportModel comparativeMovementOnly =
        CashFlowStatementReportModelBuilder.INSTANCE.build(
            comparativeMovementOnlyCashFlowStatementReport());
    ReportModel comparativeClosingOnly =
        CashFlowStatementReportModelBuilder.INSTANCE.build(
            comparativeClosingOnlyCashFlowStatementReport());

    assertEquals("cash-flow-statement", populated.family());
    assertTrue(hasVerdict(section(populated, "currentSummary").verdicts(), "Sections with data"));
    assertTrue(hasVerdict(section(populated, "currentSummary").verdicts(), "Empty sections"));
    assertTrue(
        hasVerdict(
            section(populated, "comparativeSummary").verdicts(),
            "Comparative Opening Cash Totals"));
    assertTrue(
        hasVerdict(
            section(populated, "comparativeSummary").verdicts(), "Comparative Movement Totals"));
    assertTrue(
        hasVerdict(
            section(populated, "comparativeSummary").verdicts(),
            "Comparative Closing Cash Totals"));
    assertTrue(hasVerdict(section(empty, "currentSummary").verdicts(), "Outcome"));
    assertFalse(hasVerdict(section(currentMovementOnly, "currentSummary").verdicts(), "Outcome"));
    assertTrue(
        hasVerdict(
            section(comparativeWithoutTotals, "comparativeSummary").verdicts(),
            "Sections with data"));
    assertFalse(
        hasVerdict(
            section(comparativeWithoutTotals, "comparativeSummary").verdicts(),
            "Comparative Opening Cash Totals"));
    assertFalse(
        hasVerdict(
            section(comparativeWithoutTotals, "comparativeSummary").verdicts(),
            "Comparative Movement Totals"));
    assertFalse(
        hasVerdict(
            section(comparativeWithoutTotals, "comparativeSummary").verdicts(),
            "Comparative Closing Cash Totals"));
    assertTrue(
        hasVerdict(
            section(comparativeSectionsOnly, "comparativeSummary").verdicts(),
            "Sections with data"));
    assertTrue(
        hasVerdict(
            section(comparativeSectionsOnly, "comparativeSummary").verdicts(), "Empty sections"));
    assertTrue(section(comparativeSectionsOnly, "comparative-INVESTING").rows().isEmpty());
    assertEquals(1, section(comparativeSectionsOnly, "comparative-INVESTING").totals().size());
    assertTrue(
        hasVerdict(
            section(comparativeOpeningOnly, "comparativeSummary").verdicts(),
            "Comparative Opening Cash Totals"));
    assertTrue(
        hasVerdict(
            section(comparativeMovementOnly, "comparativeSummary").verdicts(),
            "Comparative Movement Totals"));
    assertFalse(
        hasVerdict(section(comparativeMovementOnly, "comparativeSummary").verdicts(), "Outcome"));
    assertTrue(
        hasVerdict(
            section(comparativeClosingOnly, "comparativeSummary").verdicts(),
            "Comparative Closing Cash Totals"));
  }

  @Test
  void changesInEquityBuilder_coversComparativeReferenceAndTotalsPermutations() {
    ReportModel populated =
        ChangesInEquityReportModelBuilder.INSTANCE.build(populatedChangesInEquityReport());
    ReportModel empty =
        ChangesInEquityReportModelBuilder.INSTANCE.build(emptyChangesInEquityReport());
    ReportModel comparativeReferenceOnly =
        ChangesInEquityReportModelBuilder.INSTANCE.build(
            comparativeReferenceOnlyChangesInEquityReport());
    ReportModel comparativeOpeningOnly =
        ChangesInEquityReportModelBuilder.INSTANCE.build(
            comparativeOpeningOnlyChangesInEquityReport());
    ReportModel comparativeMovementOnly =
        ChangesInEquityReportModelBuilder.INSTANCE.build(
            comparativeMovementOnlyChangesInEquityReport());
    ReportModel comparativeClosingOnly =
        ChangesInEquityReportModelBuilder.INSTANCE.build(
            comparativeClosingOnlyChangesInEquityReport());

    assertEquals("changes-in-equity", populated.family());
    assertTrue(containsSection(populated, "equityTotals"));
    assertTrue(containsSection(populated, "comparativeSummary"));
    assertTrue(containsSection(populated, "comparativeEquityLines"));
    assertTrue(containsSection(populated, "comparativeEquityTotals"));
    assertTrue(hasVerdict(populated.verdicts(), "Opening totals"));
    assertTrue(hasVerdict(populated.verdicts(), "Movement totals"));
    assertTrue(hasVerdict(populated.verdicts(), "Closing totals"));
    assertTrue(
        hasVerdict(
            section(populated, "comparativeSummary").verdicts(), "Comparative opening totals"));
    assertTrue(
        hasVerdict(
            section(populated, "comparativeSummary").verdicts(), "Comparative movement totals"));
    assertTrue(
        hasVerdict(
            section(populated, "comparativeSummary").verdicts(), "Comparative closing totals"));
    assertTrue(
        section(empty, "equityLines").verdicts().stream()
            .anyMatch(
                verdict -> "No equity lines matched the selected scope.".equals(verdict.value())));
    assertFalse(containsSection(empty, "comparativeSummary"));
    assertTrue(
        hasVerdict(section(comparativeReferenceOnly, "comparativeSummary").verdicts(), "Outcome"));
    assertEquals(
        "2026-04-01",
        verdictValue(section(comparativeReferenceOnly, "comparativeSummary"), "Period start"));
    assertEquals(
        "2025-04-30",
        verdictValue(section(comparativeReferenceOnly, "comparativeSummary"), "Period end"));
    assertTrue(containsSection(comparativeOpeningOnly, "comparativeEquityTotals"));
    assertTrue(
        hasVerdict(
            section(comparativeOpeningOnly, "comparativeSummary").verdicts(),
            "Comparative opening totals"));
    assertTrue(
        hasVerdict(
            section(comparativeMovementOnly, "comparativeSummary").verdicts(),
            "Comparative movement totals"));
    assertTrue(
        hasVerdict(
            section(comparativeClosingOnly, "comparativeSummary").verdicts(),
            "Comparative closing totals"));
  }

  private static IncomeStatementReport populatedIncomeStatementReport() {
    return new IncomeStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(
            incomeStatementSection(
                AccountType.REVENUE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "2000",
                        "Revenue",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.OPERATING_REVENUE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "15.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00"))),
            incomeStatementSection(AccountType.EXPENSE, List.of(), List.of())),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00")),
        List.of(
            incomeStatementSection(
                AccountType.REVENUE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "2000",
                        "Prior Revenue",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.OPERATING_REVENUE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "10.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "10.00"))),
            incomeStatementSection(AccountType.EXPENSE, List.of(), List.of())),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "10.00")));
  }

  private static IncomeStatementReport comparativeOutcomeIncomeStatementReport() {
    return new IncomeStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(
            incomeStatementSection(
                AccountType.REVENUE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "2000",
                        "Revenue",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.OPERATING_REVENUE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "15.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00")))),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00")),
        List.of(),
        List.of());
  }

  private static IncomeStatementReport netIncomeOnlyIncomeStatementReport() {
    return new IncomeStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(
            incomeStatementSection(
                AccountType.REVENUE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "2000",
                        "Revenue",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.OPERATING_REVENUE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "15.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00")))),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00")),
        List.of(),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "10.00")));
  }

  private static IncomeStatementReport netIncomeOnlyNoReferenceIncomeStatementReport() {
    return new IncomeStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.unbounded(),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(
            incomeStatementSection(
                AccountType.REVENUE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "2000",
                        "Revenue",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.OPERATING_REVENUE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "15.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00")))),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00")),
        List.of(),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "10.00")));
  }

  private static IncomeStatementReport comparativeSectionsOnlyIncomeStatementReport() {
    return new IncomeStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.unbounded(),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(
            incomeStatementSection(
                AccountType.REVENUE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "2000",
                        "Revenue",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.OPERATING_REVENUE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "15.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00")))),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00")),
        List.of(
            incomeStatementSection(
                AccountType.REVENUE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "2000",
                        "Prior Revenue",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.OPERATING_REVENUE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "10.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "10.00")))),
        List.of());
  }

  private static IncomeStatementReport noComparativeIncomeStatementReport() {
    return new IncomeStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.unbounded(),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static IncomeStatementReport tradingIncomeStatementReport() {
    return new IncomeStatementReport(
        ReportModelTestSupport.tradingBookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(
            incomeStatementSection(
                AccountType.REVENUE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "4100",
                        "Sales Revenue",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.OPERATING_REVENUE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "100.00")),
                    ReportModelTestSupport.incomeStatementRow(
                        "4110",
                        "Sales Discount Allowance",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "10.00", "0.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "10.00", "100.00"))),
            incomeStatementSection(
                AccountType.EXPENSE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "5100",
                        "Cost of Sales",
                        AccountType.EXPENSE,
                        ProfitAndLossLineClassification.COST_OF_SALES,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "30.00", "0.00")),
                    ReportModelTestSupport.incomeStatementRow(
                        "6100",
                        "Operating Expense",
                        AccountType.EXPENSE,
                        ProfitAndLossLineClassification.OPERATING_EXPENSE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "20.00", "0.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "50.00", "0.00")))),
        List.of(ReportModelTestSupport.balance("EUR", "50.00", "90.00")),
        List.of(
            incomeStatementSection(
                AccountType.REVENUE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "4100",
                        "Prior Sales Revenue",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.OPERATING_REVENUE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "60.00")),
                    ReportModelTestSupport.incomeStatementRow(
                        "4110",
                        "Prior Sales Discount Allowance",
                        AccountType.REVENUE,
                        ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "5.00", "0.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "5.00", "60.00"))),
            incomeStatementSection(
                AccountType.EXPENSE,
                List.of(
                    ReportModelTestSupport.incomeStatementRow(
                        "5100",
                        "Prior Cost of Sales",
                        AccountType.EXPENSE,
                        ProfitAndLossLineClassification.COST_OF_SALES,
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "30.00", "0.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "30.00", "0.00")))),
        List.of(ReportModelTestSupport.balance("EUR", "30.00", "55.00")));
  }

  private static CashFlowStatementReport populatedCashFlowStatementReport() {
    return new CashFlowStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(ReportModelTestSupport.balance("EUR", "10.00", "0.00")),
        List.of(
            cashFlowSection(
                CashFlowSectionKind.OPERATING,
                List.of(CashFlowRows.revenue("2000", "Revenue", "0.00", "10.00")),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "10.00"))),
            cashFlowSection(CashFlowSectionKind.INVESTING, List.of(), List.of()),
            cashFlowSection(
                CashFlowSectionKind.FINANCING,
                List.of(CashFlowRows.equity("3000", "Owner Capital", "5.00", "0.00")),
                List.of(ReportModelTestSupport.balance("EUR", "5.00", "0.00")))),
        List.of(ReportModelTestSupport.balance("EUR", "5.00", "10.00")),
        List.of(ReportModelTestSupport.balance("EUR", "15.00", "0.00")),
        List.of(ReportModelTestSupport.balance("EUR", "8.00", "0.00")),
        List.of(
            cashFlowSection(
                CashFlowSectionKind.OPERATING,
                List.of(CashFlowRows.revenue("2000", "Prior Revenue", "0.00", "12.00")),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "12.00"))),
            cashFlowSection(CashFlowSectionKind.INVESTING, List.of(), List.of()),
            cashFlowSection(CashFlowSectionKind.FINANCING, List.of(), List.of())),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "12.00")),
        List.of(ReportModelTestSupport.balance("EUR", "20.00", "0.00")));
  }

  private static CashFlowStatementReport emptyCashFlowStatementReport() {
    return new CashFlowStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.unbounded(),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(),
        List.of(cashFlowSection(CashFlowSectionKind.OPERATING, List.of(), List.of())),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport currentMovementOnlyCashFlowStatementReport() {
    return new CashFlowStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.unbounded(),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(),
        List.of(),
        List.of(ReportModelTestSupport.balance("EUR", "2.00", "0.00")),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport comparativeWithoutTotalsCashFlowStatementReport() {
    return new CashFlowStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(),
        List.of(),
        List.of(ReportModelTestSupport.balance("EUR", "2.00", "0.00")),
        List.of(),
        List.of(),
        List.of(
            cashFlowSection(
                CashFlowSectionKind.OPERATING,
                List.of(CashFlowRows.revenue("2000", "Prior Revenue", "0.00", "12.00")),
                List.of())),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport comparativeSectionsOnlyCashFlowStatementReport() {
    return new CashFlowStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.unbounded(),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(),
        List.of(),
        List.of(ReportModelTestSupport.balance("EUR", "2.00", "0.00")),
        List.of(),
        List.of(),
        List.of(
            cashFlowSection(
                CashFlowSectionKind.INVESTING,
                List.of(),
                List.of(ReportModelTestSupport.balance("EUR", "3.00", "0.00"))),
            cashFlowSection(CashFlowSectionKind.FINANCING, List.of(), List.of())),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport comparativeMovementOnlyCashFlowStatementReport() {
    return comparativeTotalsOnlyCashFlowStatementReport(
        List.of(), List.of(ReportModelTestSupport.balance("EUR", "0.00", "4.00")), List.of());
  }

  private static CashFlowStatementReport comparativeClosingOnlyCashFlowStatementReport() {
    return comparativeTotalsOnlyCashFlowStatementReport(
        List.of(), List.of(), List.of(ReportModelTestSupport.balance("EUR", "4.00", "0.00")));
  }

  private static CashFlowStatementReport comparativeTotalsOnlyCashFlowStatementReport(
      List<dev.erst.fingrind.core.CurrencyBalance> comparativeOpening,
      List<dev.erst.fingrind.core.CurrencyBalance> comparativeMovement,
      List<dev.erst.fingrind.core.CurrencyBalance> comparativeClosing) {
    return new CashFlowStatementReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.unbounded(),
        PostingCoverage.NON_CLOSING_POSTINGS,
        List.of(),
        List.of(),
        List.of(ReportModelTestSupport.balance("EUR", "2.00", "0.00")),
        List.of(),
        comparativeOpening,
        List.of(),
        comparativeMovement,
        comparativeClosing);
  }

  private static ChangesInEquityReport populatedChangesInEquityReport() {
    return new ChangesInEquityReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.unbounded(),
        PostingCoverage.ALL_POSTING_KINDS,
        List.of(
            ReportModelTestSupport.changesInEquityRow(
                "3200",
                "Retained Earnings",
                Optional.of(AccountType.EQUITY),
                Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                StatementLineKind.DECLARED_ACCOUNT,
                ReportModelTestSupport.balance("EUR", "0.00", "0.00"),
                ReportModelTestSupport.balance("EUR", "0.00", "10.00"),
                ReportModelTestSupport.balance("EUR", "0.00", "10.00"))),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "0.00")),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "10.00")),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "10.00")),
        List.of(
            ReportModelTestSupport.changesInEquityRow(
                "3200",
                "Prior Retained Earnings",
                Optional.of(AccountType.EQUITY),
                Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                StatementLineKind.DECLARED_ACCOUNT,
                ReportModelTestSupport.balance("EUR", "0.00", "0.00"),
                ReportModelTestSupport.balance("EUR", "0.00", "7.00"),
                ReportModelTestSupport.balance("EUR", "0.00", "7.00"))),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "0.00")),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "7.00")),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "7.00")));
  }

  private static ChangesInEquityReport emptyChangesInEquityReport() {
    return new ChangesInEquityReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.unbounded(),
        PostingCoverage.ALL_POSTING_KINDS,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static ChangesInEquityReport comparativeReferenceOnlyChangesInEquityReport() {
    return new ChangesInEquityReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
        PostingCoverage.ALL_POSTING_KINDS,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static ChangesInEquityReport comparativeOpeningOnlyChangesInEquityReport() {
    return new ChangesInEquityReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
        PostingCoverage.ALL_POSTING_KINDS,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(ReportModelTestSupport.balance("EUR", "0.00", "3.00")),
        List.of(),
        List.of());
  }

  private static ChangesInEquityReport comparativeMovementOnlyChangesInEquityReport() {
    return comparativeTotalsOnlyChangesInEquityReport(
        List.of(), List.of(ReportModelTestSupport.balance("EUR", "0.00", "4.00")), List.of());
  }

  private static ChangesInEquityReport comparativeClosingOnlyChangesInEquityReport() {
    return comparativeTotalsOnlyChangesInEquityReport(
        List.of(), List.of(), List.of(ReportModelTestSupport.balance("EUR", "0.00", "5.00")));
  }

  private static ChangesInEquityReport comparativeTotalsOnlyChangesInEquityReport(
      List<dev.erst.fingrind.core.CurrencyBalance> comparativeOpening,
      List<dev.erst.fingrind.core.CurrencyBalance> comparativeMovement,
      List<dev.erst.fingrind.core.CurrencyBalance> comparativeClosing) {
    return new ChangesInEquityReport(
        ReportModelTestSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.unbounded(),
        PostingCoverage.ALL_POSTING_KINDS,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        comparativeOpening,
        comparativeMovement,
        comparativeClosing);
  }

  private static IncomeStatementSection incomeStatementSection(
      AccountType accountType,
      List<dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow> rows,
      List<dev.erst.fingrind.core.CurrencyBalance> totals) {
    return new IncomeStatementSection(accountType, rows, totals);
  }

  private static CashFlowSection cashFlowSection(
      CashFlowSectionKind sectionKind,
      List<CashFlowRow> rows,
      List<dev.erst.fingrind.core.CurrencyBalance> totals) {
    return new CashFlowSection(sectionKind, rows, totals);
  }

  private static boolean containsSection(ReportModel model, String sectionKey) {
    return model.sections().stream().anyMatch(section -> sectionKey.equals(section.key()));
  }

  private static ReportSection section(ReportModel model, String sectionKey) {
    return model.sections().stream()
        .filter(candidate -> sectionKey.equals(candidate.key()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing section: " + sectionKey));
  }

  private static boolean hasVerdict(List<ReportVerdict> verdicts, String label) {
    return verdicts.stream().anyMatch(verdict -> label.equals(verdict.label()));
  }

  private static String verdictValue(ReportSection section, String label) {
    return section.verdicts().stream()
        .filter(verdict -> label.equals(verdict.label()))
        .map(ReportVerdict::value)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing verdict: " + label));
  }

  /** Shared row factories that keep the outer coverage test under the method-count gate. */
  private static final class CashFlowRows {
    private CashFlowRows() {}

    private static CashFlowRow revenue(
        String lineCode, String lineName, String debitAmount, String creditAmount) {
      return new CashFlowRow(
          lineCode,
          lineName,
          AccountType.REVENUE,
          Optional.empty(),
          Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
          StatementLineKind.DECLARED_ACCOUNT,
          ReportModelTestSupport.balance("EUR", debitAmount, creditAmount));
    }

    private static CashFlowRow equity(
        String lineCode, String lineName, String debitAmount, String creditAmount) {
      return new CashFlowRow(
          lineCode,
          lineName,
          AccountType.EQUITY,
          Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
          Optional.empty(),
          StatementLineKind.DECLARED_ACCOUNT,
          ReportModelTestSupport.balance("EUR", debitAmount, creditAmount));
    }
  }
}
