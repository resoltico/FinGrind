package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ChangesInEquityReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancialPositionReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.IncomeStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.TaxObligationReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Locks the shared report model against cross-format fact drift. */
class ReportCrossFormatEquivalenceTest extends CliFixtureSupport {
  private static final PdfReportService PDF_REPORT_SERVICE =
      new PdfReportService(
          "FinGrind", "0.59.0", Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC));

  @Test
  void accountBalanceKeepsSectionsAndBalancesAcrossStructuredAndReadableProjectors()
      throws IOException {
    assertCrossFormatEquivalence(
        AccountBalanceReportModelBuilder.buildModel(sampleAccountBalanceSnapshot()));
  }

  @Test
  void trialBalanceKeepsBalanceStateAndCurrentTotalsAcrossStructuredAndReadableProjectors()
      throws IOException {
    assertCrossFormatEquivalence(
        TrialBalanceReportModelBuilder.buildModel(sampleTrialBalanceReport()));
  }

  @Test
  void accountLedgerKeepsEntryTablesAndBalancesAcrossStructuredAndReadableProjectors()
      throws IOException {
    assertCrossFormatEquivalence(
        AccountLedgerReportModelBuilder.buildModel(
            accountLedgerReport(
                declaredAccount("cash", "Cash", NormalBalance.DEBIT),
                salePostingFact(),
                CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00")))));
  }

  @Test
  void periodSummaryKeepsTotalsAndActivitySectionsAcrossStructuredAndReadableProjectors()
      throws IOException {
    assertCrossFormatEquivalence(
        PeriodSummaryReportModelBuilder.buildModel(
            periodSummaryReport(
                declaredAccount("service-revenue", "Service Revenue", NormalBalance.CREDIT),
                CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00")))));
  }

  @Test
  void financialPositionKeepsSectionsAndComparativesAcrossStructuredAndReadableProjectors()
      throws IOException {
    assertCrossFormatEquivalence(
        FinancialPositionReportModelBuilder.buildModel(sampleFinancialPositionReport()));
  }

  @Test
  void incomeStatementKeepsSectionsAndColumnsAcrossStructuredAndReadableProjectors()
      throws IOException {
    ReportModel model = IncomeStatementReportModelBuilder.buildModel(sampleIncomeStatementReport());
    String text = TextReportProjector.render(model);
    String pdfText = ReportCrossFormatProjectionAssertions.pdfText(PDF_REPORT_SERVICE, model);

    ReportCrossFormatProjectionAssertions.assertStructuredFactsMatch(model);
    ReportCrossFormatProjectionAssertions.assertTextFactsMatch(model, text);
    ReportCrossFormatProjectionAssertions.assertPdfFactsMatch(model, pdfText);
    assertTrue(ReportCrossFormatProjectionAssertions.containsNormalized(text, "Line code"), text);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(text, "Classification"), text);
    assertTrue(ReportCrossFormatProjectionAssertions.containsNormalized(text, "Net amount"), text);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(pdfText, "Line code"), pdfText);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(pdfText, "Classification"),
        pdfText);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(pdfText, "Net amount"), pdfText);
  }

  @Test
  void cashFlowStatementKeepsSectionsAndBalancesAcrossStructuredAndReadableProjectors()
      throws IOException {
    assertCrossFormatEquivalence(
        CashFlowStatementReportModelBuilder.buildModel(sampleCashFlowStatementReport()));
  }

  @Test
  void changesInEquityKeepsSectionsAndComparativeTotalsAcrossStructuredAndReadableProjectors()
      throws IOException {
    assertCrossFormatEquivalence(
        ChangesInEquityReportModelBuilder.buildModel(sampleChangesInEquityReport()));
  }

  @Test
  void taxObligationKeepsCodeSummariesAndNetPositionAcrossJsonTextAndCsv() throws IOException {
    ReportModel model =
        TaxObligationReportModelBuilder.buildModel(
            ReportCrossFormatTaxFixture.sampleTaxObligationReport());
    ReportCrossFormatProjectionAssertions.assertStructuredFactsMatch(model);
    ReportCrossFormatProjectionAssertions.assertTextFactsMatch(
        model, TextReportProjector.render(model));
  }

  private static void assertCrossFormatEquivalence(ReportModel model) throws IOException {
    ReportCrossFormatProjectionAssertions.assertStructuredFactsMatch(model);
    ReportCrossFormatProjectionAssertions.assertTextFactsMatch(
        model, TextReportProjector.render(model));
    ReportCrossFormatProjectionAssertions.assertPdfFactsMatch(
        model, ReportCrossFormatProjectionAssertions.pdfText(PDF_REPORT_SERVICE, model));
  }
}
