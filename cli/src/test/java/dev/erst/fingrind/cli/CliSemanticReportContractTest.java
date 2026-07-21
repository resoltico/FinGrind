package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPageCursor;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Locks the report-only machine contract independently of human report projections. */
class CliSemanticReportContractTest extends CliFixtureSupport {
  private static final Instant GENERATED_AT = Instant.parse("2026-07-12T01:13:11Z");

  @Test
  void everyReportFamilyPublishesSemanticQueryAndResultFacts() throws Exception {
    List<CliReportJsonModels.ReportPayload> reports = sampleReports();

    assertEquals(12, reports.size());
    for (CliReportJsonModels.ReportPayload report : reports) {
      JsonNode payload =
          CliJsonObjectMappers.configuredObjectMapper().readTree(CliWireJson.jsonText(report));

      assertTrue(payload.has("family"), payload.toString());
      assertTrue(payload.has("bookIdentity"), payload.toString());
      assertTrue(payload.has("resolvedQuery"), payload.toString());
      assertEquals(GENERATED_AT.toString(), payload.path("generatedAt").stringValue());
      assertFalse(payload.has("context"), payload.toString());
      assertFalse(payload.has("columns"), payload.toString());
      assertFalse(payload.has("cells"), payload.toString());
      assertFalse(payload.has("reportTitle"), payload.toString());
      assertEquals(
          resolvedQueryFieldsByFamily().get(payload.path("family").stringValue()),
          fieldNames(payload.path("resolvedQuery")),
          payload.toString());
    }
  }

  @Test
  void dateBoundResolvedQueriesRetainExplicitNullsForOmittedBounds() throws Exception {
    JsonNode accountBalance =
        CliJsonObjectMappers.configuredObjectMapper()
            .readTree(
                CliWireJson.jsonText(
                    new CliReportJsonModels.AccountBalanceResolvedQuery(
                        "cash", null, null, "ALL_POSTING_KINDS")));
    JsonNode accountLedger =
        CliJsonObjectMappers.configuredObjectMapper()
            .readTree(
                CliWireJson.jsonText(
                    new CliReportJsonModels.AccountLedgerResolvedQuery(
                        "cash",
                        null,
                        null,
                        "ALL_POSTING_KINDS",
                        new CliReportJsonModels.PaginationPayload(50, null))));

    assertEquals(
        Set.of("accountCode", "effectiveDateFrom", "effectiveDateTo", "postingCoverage"),
        fieldNames(accountBalance));
    assertTrue(accountBalance.path("effectiveDateFrom").isNull(), accountBalance.toString());
    assertTrue(accountBalance.path("effectiveDateTo").isNull(), accountBalance.toString());
    assertEquals(
        Set.of(
            "accountCode", "effectiveDateFrom", "effectiveDateTo", "postingCoverage", "pagination"),
        fieldNames(accountLedger));
    assertTrue(accountLedger.path("effectiveDateFrom").isNull(), accountLedger.toString());
    assertTrue(accountLedger.path("effectiveDateTo").isNull(), accountLedger.toString());
    assertTrue(accountLedger.path("pagination").path("cursor").isNull(), accountLedger.toString());
  }

  @Test
  void trialBalanceUsesEnumTokensAndExactMoneyObjectsInFlattenedRows() throws Exception {
    JsonNode payload =
        CliJsonObjectMappers.configuredObjectMapper()
            .readTree(
                CliWireJson.jsonText(
                    CliReportPayloadMapper.trialBalance(sampleTrialBalanceReport(), GENERATED_AT)));
    JsonNode row = payload.path("rows").get(0);

    assertEquals("ASSET", row.path("accountType").stringValue());
    assertEquals("DEBIT", row.path("normalBalance").stringValue());
    assertEquals("EUR", row.path("debitTotal").path("currencyCode").stringValue());
    assertEquals("1000", row.path("debitTotal").path("minorUnits").stringValue());
    assertFalse(row.has("account"), row.toString());
    assertFalse(row.has("balance"), row.toString());
  }

  @Test
  void accountLedgerPublishesAnExplicitPaginationWindowAndOpaqueNextCursor() throws Exception {
    AccountLedgerReport firstPage = sampleAccountLedgerReport();
    JsonNode initialPayload =
        CliJsonObjectMappers.configuredObjectMapper()
            .readTree(
                CliWireJson.jsonText(
                    CliReportPayloadMapper.accountLedger(firstPage, GENERATED_AT)));
    JsonNode initialPagination = initialPayload.path("resolvedQuery").path("pagination");
    assertEquals(50, initialPagination.path("limit").intValue());
    assertTrue(initialPagination.path("cursor").isNull(), initialPayload.toString());
    assertFalse(initialPayload.has("nextCursor"), initialPayload.toString());

    AccountLedgerPageCursor suppliedCursor =
        new AccountLedgerPageCursor(
            java.time.LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T12:00:00Z"),
            new dev.erst.fingrind.core.PostingId("e888fd00-a501-341d-9a6b-8d9059757d1b"));
    AccountLedgerPageCursor nextCursor =
        new AccountLedgerPageCursor(
            java.time.LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T12:00:00Z"),
            new dev.erst.fingrind.core.PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"));
    AccountLedgerReport continuedPage =
        new AccountLedgerReport(
            firstPage.bookIdentity(),
            firstPage.account(),
            firstPage.effectiveDateRange(),
            firstPage.postingCoverage(),
            new AccountLedgerPagination(
                1, java.util.Optional.of(suppliedCursor), java.util.Optional.of(nextCursor)),
            firstPage.openingBalances(),
            firstPage.entries(),
            firstPage.closingBalances());
    JsonNode continuedPayload =
        CliJsonObjectMappers.configuredObjectMapper()
            .readTree(
                CliWireJson.jsonText(
                    CliReportPayloadMapper.accountLedger(continuedPage, GENERATED_AT)));
    assertEquals(
        suppliedCursor.wireValue(),
        continuedPayload.path("resolvedQuery").path("pagination").path("cursor").stringValue());
    assertEquals(nextCursor.wireValue(), continuedPayload.path("nextCursor").stringValue());
  }

  @Test
  void accountLedgerPaginationRejectsANonPositiveLimit() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new CliReportJsonModels.PaginationPayload(0, null));

    assertEquals("limit must be positive.", failure.getMessage());
  }

  @Test
  void everyReportCsvIsOneTypedTableWithPairedExactMoneyColumns() {
    for (CliReportJsonModels.ReportPayload report : sampleReports()) {
      String csv = CliSemanticReportCsvRenderer.render(report);
      String header = csv.lines().findFirst().orElseThrow();

      assertTrue(header.contains("CurrencyCode"), csv);
      assertTrue(header.contains("MinorUnits"), csv);
      assertFalse(header.contains("context"), csv);
      assertFalse(header.contains("reportTitle"), csv);
      assertFalse(header.contains("rowId"), csv);
    }
  }

  @Test
  void inventoryValuationCsvCarriesExactPoolFactsWithoutFormattedMoney() {
    String csv =
        CliSemanticReportCsvRenderer.render(
            CliReportPayloadMapper.inventoryValuation(
                ReportCrossFormatInventoryFixture.sampleInventoryValuationReport(true),
                GENERATED_AT));
    List<String> lines = csv.lines().toList();
    List<String> headers = CliCsvFormat.parseRow(lines.getFirst());
    List<List<String>> rows =
        lines.subList(1, lines.size()).stream().map(CliCsvFormat::parseRow).toList();

    assertEquals(4, rows.size());
    List<List<String>> inventoryRows =
        rows.stream()
            .filter(row -> "inventory".equals(value(headers, row, "inventoryAccountCode")))
            .toList();
    assertEquals(3, inventoryRows.size());
    for (List<String> row : inventoryRows) {
      assertEquals("5000", value(headers, row, "carryingValueMinorUnits"));
      assertEquals("833", value(headers, row, "roundedMovingAverageUnitCostProjectionMinorUnits"));
      assertFalse(value(headers, row, "carryingValueMinorUnits").contains("EUR"));
    }
  }

  @Test
  void comparativeStatementPayloads_distinguishEveryComparativeFactCombination() {
    CashFlowStatementReport cashFlow = sampleCashFlowStatementReport();
    assertNotNull(
        CliReportPayloadMapper.cashFlowStatement(
                cashFlowWithComparative(
                    cashFlow, List.of(), cashFlow.openingCashTotals(), List.of(), List.of()),
                GENERATED_AT)
            .comparative());
    assertNotNull(
        CliReportPayloadMapper.cashFlowStatement(
                cashFlowWithComparative(
                    cashFlow, List.of(), List.of(), cashFlow.movementTotals(), List.of()),
                GENERATED_AT)
            .comparative());
    assertNotNull(
        CliReportPayloadMapper.cashFlowStatement(
                cashFlowWithComparative(
                    cashFlow, List.of(), List.of(), List.of(), cashFlow.closingCashTotals()),
                GENERATED_AT)
            .comparative());
    assertNull(
        CliReportPayloadMapper.cashFlowStatement(
                cashFlowWithComparative(cashFlow, List.of(), List.of(), List.of(), List.of()),
                GENERATED_AT)
            .comparative());

    ChangesInEquityReport equity = sampleChangesInEquityReport();
    assertNotNull(
        CliReportPayloadMapper.changesInEquity(
                changesInEquityWithComparative(
                    equity, List.of(), equity.openingTotals(), List.of(), List.of()),
                GENERATED_AT)
            .comparative());
    assertNotNull(
        CliReportPayloadMapper.changesInEquity(
                changesInEquityWithComparative(
                    equity, List.of(), List.of(), equity.movementTotals(), List.of()),
                GENERATED_AT)
            .comparative());
    assertNotNull(
        CliReportPayloadMapper.changesInEquity(
                changesInEquityWithComparative(
                    equity, List.of(), List.of(), List.of(), equity.closingTotals()),
                GENERATED_AT)
            .comparative());
    assertNull(
        CliReportPayloadMapper.changesInEquity(
                changesInEquityWithComparative(equity, List.of(), List.of(), List.of(), List.of()),
                GENERATED_AT)
            .comparative());
  }

  private static String value(List<String> headers, List<String> row, String key) {
    return row.get(headers.indexOf(key));
  }

  private static Map<String, Set<String>> resolvedQueryFieldsByFamily() {
    return Map.ofEntries(
        Map.entry(
            "account-balance",
            Set.of("accountCode", "effectiveDateFrom", "effectiveDateTo", "postingCoverage")),
        Map.entry("trial-balance", Set.of("asOf", "postingCoverage", "comparative")),
        Map.entry(
            "account-ledger",
            Set.of(
                "accountCode",
                "effectiveDateFrom",
                "effectiveDateTo",
                "postingCoverage",
                "pagination")),
        Map.entry("period-summary", Set.of("periodStart", "periodEnd", "postingCoverage")),
        Map.entry("financial-position", Set.of("asOf", "postingCoverage", "comparative")),
        Map.entry(
            "income-statement",
            Set.of("periodStart", "periodEnd", "postingCoverage", "comparative")),
        Map.entry("inventory-valuation", Set.of("asOf", "movements")),
        Map.entry("accrual-cutoff-schedule", Set.of("asOf")),
        Map.entry("latvian-payroll-register", Set.of()),
        Map.entry(
            "cash-flow-statement",
            Set.of("periodStart", "periodEnd", "postingCoverage", "comparative")),
        Map.entry(
            "changes-in-equity",
            Set.of("periodStart", "periodEnd", "postingCoverage", "comparative")),
        Map.entry("tax-obligation", Set.of("taxRegistrationId", "periodStart", "periodEnd")));
  }

  private static Set<String> fieldNames(JsonNode object) {
    return Set.copyOf(object.properties().stream().map(entry -> entry.getKey()).toList());
  }

  private static CashFlowStatementReport cashFlowWithComparative(
      CashFlowStatementReport report,
      List<CashFlowSection> comparativeSections,
      List<CurrencyBalance> comparativeOpeningCashTotals,
      List<CurrencyBalance> comparativeMovementTotals,
      List<CurrencyBalance> comparativeClosingCashTotals) {
    return new CashFlowStatementReport(
        report.bookIdentity(),
        report.effectiveDateFrom(),
        report.effectiveDateTo(),
        report.comparativeEffectiveDateRange(),
        report.postingCoverage(),
        report.openingCashTotals(),
        report.sections(),
        report.movementTotals(),
        report.closingCashTotals(),
        comparativeOpeningCashTotals,
        comparativeSections,
        comparativeMovementTotals,
        comparativeClosingCashTotals);
  }

  private static ChangesInEquityReport changesInEquityWithComparative(
      ChangesInEquityReport report,
      List<ChangesInEquityRow> comparativeRows,
      List<CurrencyBalance> comparativeOpeningTotals,
      List<CurrencyBalance> comparativeMovementTotals,
      List<CurrencyBalance> comparativeClosingTotals) {
    return new ChangesInEquityReport(
        report.bookIdentity(),
        report.effectiveDateFrom(),
        report.effectiveDateTo(),
        report.comparativeEffectiveDateRange(),
        report.postingCoverage(),
        report.rows(),
        report.openingTotals(),
        report.movementTotals(),
        report.closingTotals(),
        comparativeRows,
        comparativeOpeningTotals,
        comparativeMovementTotals,
        comparativeClosingTotals);
  }

  private static List<CliReportJsonModels.ReportPayload> sampleReports() {
    return List.of(
        CliReportPayloadMapper.accountBalance(sampleAccountBalanceSnapshot(), GENERATED_AT),
        CliReportPayloadMapper.trialBalance(sampleTrialBalanceReport(), GENERATED_AT),
        CliReportPayloadMapper.accountLedger(sampleAccountLedgerReport(), GENERATED_AT),
        CliReportPayloadMapper.periodSummary(samplePeriodSummaryReport(), GENERATED_AT),
        CliReportPayloadMapper.financialPosition(sampleFinancialPositionReport(), GENERATED_AT),
        CliReportPayloadMapper.incomeStatement(sampleIncomeStatementReport(), GENERATED_AT),
        CliReportPayloadMapper.inventoryValuation(
            ReportCrossFormatInventoryFixture.sampleInventoryValuationReport(true), GENERATED_AT),
        CliReportPayloadMapper.accrualCutoffSchedule(
            ReportCrossFormatAccrualCutoffFixture.sampleAccrualCutoffScheduleReport(),
            GENERATED_AT),
        CliReportPayloadMapper.latvianPayrollRegister(
            ReportCrossFormatLatvianPayrollFixture.sampleLatvianPayrollRegisterReport(),
            GENERATED_AT),
        CliReportPayloadMapper.cashFlowStatement(sampleCashFlowStatementReport(), GENERATED_AT),
        CliReportPayloadMapper.changesInEquity(sampleChangesInEquityReport(), GENERATED_AT),
        CliReportPayloadMapper.taxObligation(
            ReportCrossFormatTaxFixture.sampleTaxObligationReport(), GENERATED_AT));
  }
}
