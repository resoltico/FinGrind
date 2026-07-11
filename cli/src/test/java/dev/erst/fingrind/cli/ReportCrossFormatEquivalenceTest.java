package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ChangesInEquityReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancialPositionReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.IncomeStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.InventoryValuationReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ReportCsvProjection;
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
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Locks the shared report model against cross-format fact drift. */
class ReportCrossFormatEquivalenceTest extends CliFixtureSupport {
  private static final PdfReportService PDF_REPORT_SERVICE =
      new PdfReportService(
          "FinGrind", "0.60.0", Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC));

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

  @Test
  void inventoryValuationKeepsExactPoolAndInformationalProjectionAcrossAllFormats()
      throws IOException {
    ReportModel model =
        InventoryValuationReportModelBuilder.buildModel(
            ReportCrossFormatInventoryFixture.sampleInventoryValuationReport(true));
    String text = TextReportProjector.render(model);
    String pdfText = ReportCrossFormatProjectionAssertions.pdfText(PDF_REPORT_SERVICE, model);
    String csv = CsvReportProjector.render(model);
    ReportCsvProjection tabularCsvProjection =
        Objects.requireNonNull(model.tabularCsvProjection(), "tabularCsvProjection");

    ReportCrossFormatProjectionAssertions.assertJsonFactsMatch(model);
    ReportCrossFormatProjectionAssertions.assertTextFactsMatch(model, text);
    ReportCrossFormatProjectionAssertions.assertPdfFactsMatch(model, pdfText);
    assertEquals(
        CliTextFormat.renderCsv(tabularCsvProjection.headers(), tabularCsvProjection.rows()), csv);
    assertInventoryValuationCsvFacts(model, csv);
    assertTrue(
        ReportCrossFormatProjectionAssertions.containsNormalized(text, "Unit cost (informational)"),
        text);
    assertTrue(ReportCrossFormatProjectionAssertions.containsNormalized(text, "EUR 50.00"), text);
    assertTrue(ReportCrossFormatProjectionAssertions.containsNormalized(text, "EUR 8.33"), text);
  }

  private static void assertInventoryValuationCsvFacts(ReportModel model, String csv) {
    List<String> lines = csv.lines().toList();
    List<String> headers = CliCsvFormat.parseRow(lines.getFirst());
    List<List<String>> rows =
        lines.subList(1, lines.size()).stream().map(CliCsvFormat::parseRow).toList();

    assertEquals(4, rows.size());
    assertInventoryAccountCsvFacts(model, headers, rows, "inventory", 3);
    assertInventoryAccountCsvFacts(model, headers, rows, "inventory-reserve", 1);
  }

  private static void assertInventoryAccountCsvFacts(
      ReportModel model,
      List<String> headers,
      List<List<String>> rows,
      String inventoryAccountCode,
      int expectedRowCount) {
    List<List<String>> accountRows =
        rows.stream()
            .filter(
                row -> inventoryAccountCode.equals(csvValue(headers, row, "inventoryAccountCode")))
            .toList();
    var account =
        model.sections().getFirst().rows().stream()
            .filter(row -> inventoryAccountCode.equals(row.cells().getFirst()))
            .findFirst()
            .orElseThrow();

    assertEquals(expectedRowCount, accountRows.size());
    for (List<String> row : accountRows) {
      assertEquals("inventory-valuation", csvValue(headers, row, "recordKind"));
      assertEquals(account.cells().get(1), csvValue(headers, row, "inventoryAccountName"));
      assertEquals(account.cells().get(2), csvValue(headers, row, "unitOfMeasure"));
      assertEquals(account.cells().get(3), csvValue(headers, row, "quantityOnHand"));
    }
    if ("inventory".equals(inventoryAccountCode)) {
      for (List<String> row : accountRows) {
        assertEquals(
            "833", csvValue(headers, row, "roundedMovingAverageUnitCostProjectionMinorUnits"));
        assertEquals("5000", csvValue(headers, row, "carryingValueMinorUnits"));
      }
      return;
    }
    List<String> accountRow = accountRows.getFirst();
    assertEquals("", csvValue(headers, accountRow, "movementPostingId"));
    assertEquals(
        "", csvValue(headers, accountRow, "roundedMovingAverageUnitCostProjectionMinorUnits"));
    assertEquals("0", csvValue(headers, accountRow, "carryingValueMinorUnits"));
  }

  private static String csvValue(List<String> headers, List<String> row, String key) {
    return row.get(headers.indexOf(key));
  }

  private static void assertCrossFormatEquivalence(ReportModel model) throws IOException {
    ReportCrossFormatProjectionAssertions.assertStructuredFactsMatch(model);
    ReportCrossFormatProjectionAssertions.assertTextFactsMatch(
        model, TextReportProjector.render(model));
    ReportCrossFormatProjectionAssertions.assertPdfFactsMatch(
        model, ReportCrossFormatProjectionAssertions.pdfText(PDF_REPORT_SERVICE, model));
  }
}
