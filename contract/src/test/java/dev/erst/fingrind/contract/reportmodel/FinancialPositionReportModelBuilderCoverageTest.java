package dev.erst.fingrind.contract.reportmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for the financial-position shared report-model builder. */
class FinancialPositionReportModelBuilderCoverageTest {
  @Test
  void coversCurrentAndComparativeBranches() {
    ReportModel populated =
        FinancialPositionReportModelBuilder.INSTANCE.build(populatedFinancialPositionReport());
    ReportModel empty =
        FinancialPositionReportModelBuilder.INSTANCE.build(emptyFinancialPositionReport());
    ReportModel comparativeOutcome =
        FinancialPositionReportModelBuilder.INSTANCE.build(
            comparativeOutcomeFinancialPositionReport());
    ReportModel comparativeSectionsOnly =
        FinancialPositionReportModelBuilder.INSTANCE.build(
            comparativeSectionsOnlyFinancialPositionReport());
    ReportModel pluralContraNormalRows =
        FinancialPositionReportModelBuilder.INSTANCE.build(
            pluralContraNormalRowsFinancialPositionReport());

    assertEquals("financial-position", populated.family());
    assertTrue(hasVerdict(populated.verdicts(), "Sections with data"));
    assertTrue(hasVerdict(populated.verdicts(), "Empty sections"));
    assertEquals("1 row", verdictValue(populated, "Contra-normal rows"));
    assertTrue(
        hasVerdict(section(populated, "comparativeSummary").verdicts(), "Sections with data"));
    assertTrue(hasVerdict(section(populated, "comparativeSummary").verdicts(), "Empty sections"));
    assertEquals(
        "1 row", verdictValue(section(populated, "comparativeSummary"), "Contra-normal rows"));
    assertTrue(
        empty.verdicts().stream()
            .anyMatch(
                verdict ->
                    "No financial position lines matched the selected scope."
                        .equals(verdict.value())));
    assertTrue(
        comparativeOutcome.verdicts().stream()
            .anyMatch(
                verdict ->
                    "Accounting equation".equals(verdict.label())
                        && "Imbalanced".equals(verdict.value())));
    assertTrue(hasVerdict(section(comparativeOutcome, "comparativeSummary").verdicts(), "Outcome"));
    assertEquals(
        "(none)", verdictValue(section(comparativeSectionsOnly, "comparativeSummary"), "As of"));
    assertTrue(section(comparativeSectionsOnly, "comparative-ASSET").rows().isEmpty());
    assertFalse(section(comparativeSectionsOnly, "comparative-ASSET").totals().isEmpty());
    assertEquals("2 rows", verdictValue(pluralContraNormalRows, "Contra-normal rows"));
  }

  private static FinancialPositionReport populatedFinancialPositionReport() {
    return new FinancialPositionReport(
        ReportModelTestSupport.bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
        PostingCoverage.ALL_POSTING_KINDS,
        true,
        List.of(
            financialPositionSection(
                AccountType.ASSET,
                List.of(
                    ReportModelTestSupport.financialPositionRow(
                        "1000",
                        "Cash",
                        AccountType.ASSET,
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "15.00", "0.00")),
                    ReportModelTestSupport.financialPositionRow(
                        "1400",
                        "Inventory",
                        AccountType.ASSET,
                        Optional.of(FinancialPositionLineClassification.INVENTORY),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "3.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "15.00", "3.00"))),
            financialPositionSection(AccountType.LIABILITY, List.of(), List.of()),
            financialPositionSection(
                AccountType.EQUITY,
                List.of(
                    ReportModelTestSupport.financialPositionRow(
                        "3200",
                        "Retained Earnings",
                        AccountType.EQUITY,
                        Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "15.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "15.00")))),
        List.of(
            financialPositionSection(
                AccountType.ASSET,
                List.of(
                    ReportModelTestSupport.financialPositionRow(
                        "1000",
                        "Prior Cash",
                        AccountType.ASSET,
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "10.00", "0.00")),
                    ReportModelTestSupport.financialPositionRow(
                        "1400",
                        "Prior Inventory",
                        AccountType.ASSET,
                        Optional.of(FinancialPositionLineClassification.INVENTORY),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "2.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "10.00", "2.00"))),
            financialPositionSection(AccountType.LIABILITY, List.of(), List.of())));
  }

  private static FinancialPositionReport emptyFinancialPositionReport() {
    return new FinancialPositionReport(
        ReportModelTestSupport.bookIdentity(),
        Optional.empty(),
        Optional.empty(),
        EffectiveDateRange.unbounded(),
        PostingCoverage.ALL_POSTING_KINDS,
        true,
        List.of(financialPositionSection(AccountType.ASSET, List.of(), List.of())),
        List.of());
  }

  private static FinancialPositionReport comparativeOutcomeFinancialPositionReport() {
    return new FinancialPositionReport(
        ReportModelTestSupport.bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
        PostingCoverage.ALL_POSTING_KINDS,
        false,
        List.of(
            financialPositionSection(
                AccountType.ASSET,
                List.of(
                    ReportModelTestSupport.financialPositionRow(
                        "1000",
                        "Cash",
                        AccountType.ASSET,
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "15.00", "0.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "15.00", "0.00")))),
        List.of());
  }

  private static FinancialPositionReport comparativeSectionsOnlyFinancialPositionReport() {
    return new FinancialPositionReport(
        ReportModelTestSupport.bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.unbounded(),
        PostingCoverage.ALL_POSTING_KINDS,
        true,
        List.of(
            financialPositionSection(
                AccountType.ASSET,
                List.of(
                    ReportModelTestSupport.financialPositionRow(
                        "1000",
                        "Cash",
                        AccountType.ASSET,
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "15.00", "0.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "15.00", "0.00")))),
        List.of(
            financialPositionSection(
                AccountType.ASSET,
                List.of(),
                List.of(ReportModelTestSupport.balance("EUR", "10.00", "0.00"))),
            financialPositionSection(AccountType.LIABILITY, List.of(), List.of())));
  }

  private static FinancialPositionReport pluralContraNormalRowsFinancialPositionReport() {
    return new FinancialPositionReport(
        ReportModelTestSupport.bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        Optional.empty(),
        EffectiveDateRange.unbounded(),
        PostingCoverage.ALL_POSTING_KINDS,
        true,
        List.of(
            financialPositionSection(
                AccountType.ASSET,
                List.of(
                    ReportModelTestSupport.financialPositionRow(
                        "1000",
                        "Cash",
                        AccountType.ASSET,
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "4.00")),
                    ReportModelTestSupport.financialPositionRow(
                        "1400",
                        "Inventory",
                        AccountType.ASSET,
                        Optional.of(FinancialPositionLineClassification.INVENTORY),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "6.00")),
                    ReportModelTestSupport.financialPositionRow(
                        "1450",
                        "Zero Inventory",
                        AccountType.ASSET,
                        Optional.of(FinancialPositionLineClassification.INVENTORY),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "0.00", "0.00")),
                    ReportModelTestSupport.financialPositionRow(
                        "1490",
                        "Unclassified Holding",
                        AccountType.ASSET,
                        Optional.empty(),
                        StatementLineKind.DECLARED_ACCOUNT,
                        ReportModelTestSupport.balance("EUR", "3.00", "0.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "3.00", "10.00")))),
        List.of());
  }

  private static FinancialPositionSection financialPositionSection(
      AccountType accountType,
      List<dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow> rows,
      List<dev.erst.fingrind.core.CurrencyBalance> totals) {
    return new FinancialPositionSection(accountType, rows, totals);
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

  private static String verdictValue(ReportModel model, String label) {
    return model.verdicts().stream()
        .filter(verdict -> label.equals(verdict.label()))
        .map(ReportVerdict::value)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing verdict: " + label));
  }

  private static String verdictValue(ReportSection section, String label) {
    return section.verdicts().stream()
        .filter(verdict -> label.equals(verdict.label()))
        .map(ReportVerdict::value)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing verdict: " + label));
  }
}
