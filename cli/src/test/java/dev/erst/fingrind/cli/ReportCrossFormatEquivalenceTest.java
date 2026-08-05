package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccrualCutoffScheduleReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ChangesInEquityReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancialPositionReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancingRegisterReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FixedAssetRegisterReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.IncomeStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.InventoryValuationReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.LatvianPayrollRegisterReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.RealizedForeignExchangeRegisterReportModelBuilder;
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

/** Locks the shared human report projections against fact drift. */
class ReportCrossFormatEquivalenceTest extends CliFixtureSupport {
  private static final PdfReportService PDF_REPORT_SERVICE =
      new PdfReportService(
          "FinGrind", "0.62.1", Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC));

  @Test
  void accountBalanceKeepsSectionsAndBalancesAcrossHumanProjectors() throws IOException {
    assertCrossFormatEquivalence(
        AccountBalanceReportModelBuilder.buildModel(sampleAccountBalanceSnapshot()));
  }

  @Test
  void trialBalanceKeepsBalanceStateAndCurrentTotalsAcrossHumanProjectors() throws IOException {
    assertCrossFormatEquivalence(
        TrialBalanceReportModelBuilder.buildModel(sampleTrialBalanceReport()));
  }

  @Test
  void accountLedgerKeepsEntryTablesAndBalancesAcrossHumanProjectors() throws IOException {
    assertCrossFormatEquivalence(
        AccountLedgerReportModelBuilder.buildModel(
            accountLedgerReport(
                declaredAccount("cash", "Cash", NormalBalance.DEBIT),
                salePostingFact(),
                CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00")))));
  }

  @Test
  void periodSummaryKeepsTotalsAndActivitySectionsAcrossHumanProjectors() throws IOException {
    assertCrossFormatEquivalence(
        PeriodSummaryReportModelBuilder.buildModel(
            periodSummaryReport(
                declaredAccount("service-revenue", "Service Revenue", NormalBalance.CREDIT),
                CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00")))));
  }

  @Test
  void financialPositionKeepsSectionsAndComparativesAcrossHumanProjectors() throws IOException {
    assertCrossFormatEquivalence(
        FinancialPositionReportModelBuilder.buildModel(sampleFinancialPositionReport()));
  }

  @Test
  void incomeStatementKeepsSectionsAndColumnsAcrossHumanProjectors() throws IOException {
    ReportModel model = IncomeStatementReportModelBuilder.buildModel(sampleIncomeStatementReport());
    String text = TextReportProjector.render(model);
    String pdfText = ReportCrossFormatProjectionAssertions.pdfText(PDF_REPORT_SERVICE, model);

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
  void cashFlowStatementKeepsSectionsAndBalancesAcrossHumanProjectors() throws IOException {
    assertCrossFormatEquivalence(
        CashFlowStatementReportModelBuilder.buildModel(sampleCashFlowStatementReport()));
  }

  @Test
  void changesInEquityKeepsSectionsAndComparativeTotalsAcrossHumanProjectors() throws IOException {
    assertCrossFormatEquivalence(
        ChangesInEquityReportModelBuilder.buildModel(sampleChangesInEquityReport()));
  }

  @Test
  void taxObligationKeepsCodeSummariesAndNetPositionInText() throws IOException {
    ReportModel model =
        TaxObligationReportModelBuilder.buildModel(
            ReportCrossFormatTaxFixture.sampleTaxObligationReport());
    ReportCrossFormatProjectionAssertions.assertTextFactsMatch(
        model, TextReportProjector.render(model));
  }

  @Test
  void inventoryValuationKeepsExactPoolAndInformationalProjectionInHumanFormats()
      throws IOException {
    ReportModel model =
        InventoryValuationReportModelBuilder.buildModel(
            ReportCrossFormatInventoryFixture.sampleInventoryValuationReport(true));
    String text = TextReportProjector.render(model);
    String pdfText = ReportCrossFormatProjectionAssertions.pdfText(PDF_REPORT_SERVICE, model);
    ReportCrossFormatProjectionAssertions.assertTextFactsMatch(model, text);
    ReportCrossFormatProjectionAssertions.assertPdfFactsMatch(model, pdfText);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(text, "Unit cost (informational)"),
        text);
    assertTrue(ReportCrossFormatProjectionAssertions.containsNormalized(text, "EUR 50.00"), text);
    assertTrue(ReportCrossFormatProjectionAssertions.containsNormalized(text, "EUR 8.33"), text);
  }

  @Test
  void accrualCutoffScheduleKeepsLifecycleFactsAcrossHumanFormats() throws IOException {
    ReportModel model =
        AccrualCutoffScheduleReportModelBuilder.buildModel(
            ReportCrossFormatAccrualCutoffFixture.sampleAccrualCutoffScheduleReport());
    String text = TextReportProjector.render(model);
    String pdfText = ReportCrossFormatProjectionAssertions.pdfText(PDF_REPORT_SERVICE, model);

    ReportCrossFormatProjectionAssertions.assertTextFactsMatch(model, text);
    ReportCrossFormatProjectionAssertions.assertPdfFactsMatch(model, pdfText);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(text, "prepayment-2026"), text);
    assertTrue(ReportCrossFormatProjectionAssertions.containsNormalized(text, "EUR 90.00"), text);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(text, "Not applicable"), text);
  }

  @Test
  void latvianPayrollRegisterKeepsRunAndSettlementLifecycleAcrossHumanFormats() throws IOException {
    ReportModel model =
        LatvianPayrollRegisterReportModelBuilder.buildModel(
            ReportCrossFormatLatvianPayrollFixture.sampleLatvianPayrollRegisterReport());
    String text = TextReportProjector.render(model);
    String pdfText = ReportCrossFormatProjectionAssertions.pdfText(PDF_REPORT_SERVICE, model);

    ReportCrossFormatProjectionAssertions.assertTextFactsMatch(model, text);
    ReportCrossFormatProjectionAssertions.assertPdfFactsMatch(model, pdfText);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(
            text, "payroll-run-2026-07-employee-001"),
        text);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(text, "State remittance"), text);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(
            text, "210633ad-7df4-3735-a675-6fde1a7f2c55"),
        text);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(
            text, "Settlement status : Reversed"),
        text);
  }

  @Test
  void fixedAssetRegisterKeepsCostAndDisposalFactsAcrossHumanFormats() throws IOException {
    ReportModel model =
        FixedAssetRegisterReportModelBuilder.buildModel(
            ReportCrossFormatLifecycleContextFixture.fixedAssetRegisterReport());
    assertCrossFormatEquivalence(model);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(
            TextReportProjector.render(model), "asset-vehicle-001"));
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(
            TextReportProjector.render(model), "Carrying before disposal"));
  }

  @Test
  void financingRegisterKeepsPrincipalAndInterestFactsAcrossHumanFormats() throws IOException {
    ReportModel model =
        FinancingRegisterReportModelBuilder.buildModel(
            ReportCrossFormatLifecycleContextFixture.financingRegisterReport());
    assertCrossFormatEquivalence(model);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(
            TextReportProjector.render(model), "loan-working-capital-001"));
  }

  @Test
  void realizedForeignExchangeRegisterKeepsSettlementFactsAcrossHumanFormats() throws IOException {
    ReportModel model =
        RealizedForeignExchangeRegisterReportModelBuilder.buildModel(
            ReportCrossFormatLifecycleContextFixture.realizedForeignExchangeRegisterReport());
    assertCrossFormatEquivalence(model);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(
            TextReportProjector.render(model), "receivable-usd-001"));
  }

  private static void assertCrossFormatEquivalence(ReportModel model) throws IOException {
    ReportCrossFormatProjectionAssertions.assertTextFactsMatch(
        model, TextReportProjector.render(model));
    ReportCrossFormatProjectionAssertions.assertPdfFactsMatch(
        model, ReportCrossFormatProjectionAssertions.pdfText(PDF_REPORT_SERVICE, model));
  }
}
