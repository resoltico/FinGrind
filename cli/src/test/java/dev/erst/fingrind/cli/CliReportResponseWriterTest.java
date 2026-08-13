package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Contract coverage for report response projections across all supported output modes. */
class CliReportResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writeTrialBalanceResult_projectsTransactionCompletionAcrossJsonAndText() throws IOException {
    Path publishedArtifactPath = Path.of("private-reports", "trial-balance.pdf");
    var publication =
        CliPublicationTransactionTestFixtures.completedArtifact(publishedArtifactPath);
    TrialBalanceResult.Reported result =
        new TrialBalanceResult.Reported(sampleTrialBalanceReport());

    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(jsonOutput))
        .writeTrialBalanceResult(result, OutputMode.JSON, publication);
    JsonNode artifact = readJson(jsonOutput).path("artifacts").get(0);
    assertEquals("pdf", artifact.path("format").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(publishedArtifactPath), artifact.path("path").stringValue());
    assertTrue(artifact.path("retainedStage").isMissingNode());
    assertEquals(
        "0123456789abcdef0123456789abcdef",
        artifact.path("publicationTransaction").path("id").stringValue());

    ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(textOutput))
        .writeTrialBalanceResult(result, OutputMode.TEXT, publication);
    String rendered = textOutput.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Artifact"));
    assertTrue(rendered.contains("Publication transaction"));
    assertFalse(rendered.contains("As of"));
  }

  @Test
  void writeTrialBalanceResult_supportJsonTextAndCsvOutputModes() throws IOException {
    TrialBalanceReport trialBalanceReport = sampleTrialBalanceReport();
    ByteArrayOutputStream trialBalanceJsonOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(trialBalanceJsonOutput))
        .writeTrialBalanceResult(
            new TrialBalanceResult.Reported(trialBalanceReport), OutputMode.JSON);
    JsonNode trialBalanceJson = readJson(trialBalanceJsonOutput);
    assertEquals("ok", trialBalanceJson.path("status").stringValue());
    assertEquals("trial-balance", trialBalanceJson.path("payload").path("family").stringValue());
    assertEquals(
        "2026-04-30",
        trialBalanceJson.path("payload").path("resolvedQuery").path("asOf").stringValue());
    assertEquals(
        "Acme Studio",
        trialBalanceJson.path("payload").path("bookIdentity").path("entityName").stringValue());
    assertEquals(
        "1000",
        trialBalanceJson.path("payload").path("rows").get(0).path("accountCode").stringValue());
    assertEquals(
        "1000",
        trialBalanceJson
            .path("payload")
            .path("totals")
            .get(0)
            .path("debitTotal")
            .path("minorUnits")
            .stringValue());
    ByteArrayOutputStream trialBalanceTextOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(trialBalanceTextOutput))
        .writeTrialBalanceResult(
            new TrialBalanceResult.Reported(trialBalanceReport), OutputMode.TEXT);
    String trialBalanceText = trialBalanceTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(trialBalanceText.contains("As of"));
    assertTrue(trialBalanceText.contains("2026-04-30"));
    assertTrue(trialBalanceText.contains("Account"));
    assertTrue(trialBalanceText.contains("Balance state"));
    assertTrue(trialBalanceText.contains("Imbalanced"));
    assertTrue(trialBalanceText.contains("6.00"));
  }

  @Test
  void writeAccountLedgerResult_supportJsonTextAndCsvOutputModes() throws IOException {
    AccountLedgerReport accountLedgerReport = sampleAccountLedgerReport();

    ByteArrayOutputStream accountLedgerTextOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(accountLedgerTextOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult.Reported(
                accountLedgerReport),
            OutputMode.TEXT);
    String accountLedgerText = accountLedgerTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(accountLedgerText.contains("Opening Balances"));
    assertTrue(accountLedgerText.contains("EUR 10.00 Debit"));
    assertTrue(accountLedgerText.contains("Ledger Entries"));
    assertTrue(accountLedgerText.contains("Counterpart account codes"));
    assertTrue(accountLedgerText.contains("bdc03c47-a16c-3688-a18f-2445894bbc69"));
    ByteArrayOutputStream accountLedgerJsonOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(accountLedgerJsonOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult.Reported(
                accountLedgerReport),
            OutputMode.JSON);
    JsonNode accountLedgerJson = readJson(accountLedgerJsonOutput);
    assertEquals("account-ledger", accountLedgerJson.path("payload").path("family").stringValue());
    assertEquals(
        "2026-04-01",
        accountLedgerJson
            .path("payload")
            .path("resolvedQuery")
            .path("effectiveDateFrom")
            .stringValue());
    assertEquals(
        50,
        accountLedgerJson
            .path("payload")
            .path("resolvedQuery")
            .path("pagination")
            .path("limit")
            .intValue());
    assertTrue(
        accountLedgerJson
            .path("payload")
            .path("resolvedQuery")
            .path("pagination")
            .path("cursor")
            .isNull());
    JsonNode ledgerRow = accountLedgerJson.path("payload").path("rows").get(0);
    assertEquals("bdc03c47-a16c-3688-a18f-2445894bbc69", ledgerRow.path("postingId").stringValue());
    assertEquals("2026-04-07", ledgerRow.path("effectiveDate").stringValue());
    assertEquals(
        "1000", ledgerRow.path("movement").path("debitTotal").path("minorUnits").stringValue());
    assertFalse(accountLedgerJson.toString().contains("\"postingFact\""));
    ByteArrayOutputStream accountLedgerCsvOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(accountLedgerCsvOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult.Reported(
                accountLedgerReport),
            OutputMode.CSV);
    String accountLedgerCsv = accountLedgerCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        accountLedgerCsv.startsWith(
            "family,accountCode,postingId,effectiveDate,movementCurrencyCode,debitTotalCurrencyCode,debitTotalMinorUnits"));
    assertTrue(
        accountLedgerCsv.contains(
            "account-ledger,1000,bdc03c47-a16c-3688-a18f-2445894bbc69,2026-04-07,EUR,EUR,1000"));
  }

  @Test
  void writePeriodSummaryResult_supportJsonTextAndCsvOutputModes() throws IOException {
    PeriodSummaryReport periodSummaryReport = samplePeriodSummaryReport();

    ByteArrayOutputStream periodSummaryTextOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(periodSummaryTextOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult.Reported(
                periodSummaryReport),
            OutputMode.TEXT);
    String periodSummaryText = periodSummaryTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(periodSummaryText.contains("Posting count"));
    assertTrue(periodSummaryText.contains("Posting line count"));
    assertTrue(periodSummaryText.contains("10.00"));
    ByteArrayOutputStream periodSummaryJsonOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(periodSummaryJsonOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult.Reported(
                periodSummaryReport),
            OutputMode.JSON);
    JsonNode periodSummaryJson = readJson(periodSummaryJsonOutput);
    assertEquals("period-summary", periodSummaryJson.path("payload").path("family").stringValue());
    assertEquals(
        "2026-04-01",
        periodSummaryJson.path("payload").path("resolvedQuery").path("periodStart").stringValue());
    assertEquals(1, periodSummaryJson.path("payload").path("postingCount").intValue());
    JsonNode accountActivity = periodSummaryJson.path("payload").path("accountActivity").get(0);
    assertEquals("1000", accountActivity.path("accountCode").stringValue());
    assertTrue(accountActivity.path("active").booleanValue());
    ByteArrayOutputStream periodSummaryCsvOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(periodSummaryCsvOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult.Reported(
                periodSummaryReport),
            OutputMode.CSV);
    String periodSummaryCsv = periodSummaryCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        periodSummaryCsv.startsWith(
            "family,recordScope,accountCode,accountName,accountType,normalBalance,active,currencyCode,debitTotalCurrencyCode"));
    assertTrue(
        periodSummaryCsv.contains("period-summary,activity,1000,Cash,ASSET,DEBIT,true,EUR,EUR"));
  }

  @Test
  void writeAccountLedgerJson_marksDirectEntriesWithoutReversalTarget() throws IOException {
    AccountLedgerReport directAccountLedgerReport =
        new AccountLedgerReport(
            bookIdentity(),
            declaredCashAccount(),
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            AccountLedgerPagination.firstPage(50),
            List.of(),
            List.of(
                new AccountLedgerEntry(
                    CliFixtureSupport.selfPostingFact(),
                    currencyBalance("EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT),
                    money("EUR", "5.00"),
                    BalanceSide.DEBIT,
                    null)),
            List.of(currencyBalance("EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT)));
    ByteArrayOutputStream directAccountLedgerJsonOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(directAccountLedgerJsonOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult.Reported(
                directAccountLedgerReport),
            OutputMode.JSON);

    JsonNode directAccountLedgerJson = readJson(directAccountLedgerJsonOutput);
    assertEquals(
        CliFixtureSupport.selfPostingFact().postingId().value(),
        directAccountLedgerJson
            .path("payload")
            .path("rows")
            .get(0)
            .path("postingId")
            .stringValue());
    assertFalse(directAccountLedgerJson.toString().contains("reversal"));
  }

  @Test
  void writePrimaryStatementResults_supportJsonTextAndCsvOutputModes() throws IOException {
    ByteArrayOutputStream financialPositionJsonOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(financialPositionJsonOutput))
        .writeFinancialPositionResult(
            new FinancialPositionResult.Reported(CliFixtureSupport.sampleFinancialPositionReport()),
            OutputMode.JSON);
    JsonNode financialPositionJson = readJson(financialPositionJsonOutput);
    assertEquals("ok", financialPositionJson.path("status").stringValue());
    assertEquals(
        "2026-04-30",
        financialPositionJson.path("payload").path("resolvedQuery").path("asOf").stringValue());
    assertEquals(
        "1000",
        financialPositionJson
            .path("payload")
            .path("sections")
            .get(0)
            .path("rows")
            .get(0)
            .path("lineCode")
            .stringValue());

    ByteArrayOutputStream financialPositionTextOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(financialPositionTextOutput))
        .writeFinancialPositionResult(
            new FinancialPositionResult.Reported(CliFixtureSupport.sampleFinancialPositionReport()),
            OutputMode.TEXT);
    assertTrue(
        financialPositionTextOutput
            .toString(StandardCharsets.UTF_8)
            .contains("Financial Position"));

    ByteArrayOutputStream financialPositionCsvOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(financialPositionCsvOutput))
        .writeFinancialPositionResult(
            new FinancialPositionResult.Reported(CliFixtureSupport.sampleFinancialPositionReport()),
            OutputMode.CSV);
    assertTrue(
        financialPositionCsvOutput
            .toString(StandardCharsets.UTF_8)
            .startsWith("family,reportPeriod,sectionKind,lineCode,lineName,lineType"));

    ByteArrayOutputStream incomeStatementJsonOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(incomeStatementJsonOutput))
        .writeIncomeStatementResult(
            new IncomeStatementResult.Reported(CliFixtureSupport.sampleIncomeStatementReport()),
            OutputMode.JSON);
    JsonNode incomeStatementJson = readJson(incomeStatementJsonOutput);
    assertEquals("ok", incomeStatementJson.path("status").stringValue());
    assertEquals(
        "2026-04-01",
        incomeStatementJson
            .path("payload")
            .path("resolvedQuery")
            .path("periodStart")
            .stringValue());
    assertEquals(
        "2000",
        incomeStatementJson
            .path("payload")
            .path("sections")
            .get(0)
            .path("rows")
            .get(0)
            .path("lineCode")
            .stringValue());

    ByteArrayOutputStream incomeStatementTextOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(incomeStatementTextOutput))
        .writeIncomeStatementResult(
            new IncomeStatementResult.Reported(CliFixtureSupport.sampleIncomeStatementReport()),
            OutputMode.TEXT);
    assertTrue(
        incomeStatementTextOutput.toString(StandardCharsets.UTF_8).contains("Income Statement"));

    ByteArrayOutputStream incomeStatementCsvOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(incomeStatementCsvOutput))
        .writeIncomeStatementResult(
            new IncomeStatementResult.Reported(CliFixtureSupport.sampleIncomeStatementReport()),
            OutputMode.CSV);
    assertTrue(
        incomeStatementCsvOutput
            .toString(StandardCharsets.UTF_8)
            .startsWith("family,reportPeriod,sectionKind,lineCode,lineName,lineType"));

    ByteArrayOutputStream changesInEquityJsonOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(changesInEquityJsonOutput))
        .writeChangesInEquityResult(
            new ChangesInEquityResult.Reported(CliFixtureSupport.sampleChangesInEquityReport()),
            OutputMode.JSON);
    JsonNode changesInEquityJson = readJson(changesInEquityJsonOutput);
    assertEquals("ok", changesInEquityJson.path("status").stringValue());
    assertEquals(
        "2026-04-30",
        changesInEquityJson.path("payload").path("resolvedQuery").path("periodEnd").stringValue());
    assertEquals(
        "3200",
        changesInEquityJson.path("payload").path("rows").get(0).path("lineCode").stringValue());

    ByteArrayOutputStream changesInEquityTextOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(changesInEquityTextOutput))
        .writeChangesInEquityResult(
            new ChangesInEquityResult.Reported(CliFixtureSupport.sampleChangesInEquityReport()),
            OutputMode.TEXT);
    assertTrue(
        changesInEquityTextOutput.toString(StandardCharsets.UTF_8).contains("Changes In Equity"));

    ByteArrayOutputStream changesInEquityCsvOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(changesInEquityCsvOutput))
        .writeChangesInEquityResult(
            new ChangesInEquityResult.Reported(CliFixtureSupport.sampleChangesInEquityReport()),
            OutputMode.CSV);
    assertTrue(
        changesInEquityCsvOutput
            .toString(StandardCharsets.UTF_8)
            .startsWith(
                "family,reportPeriod,lineCode,lineName,lineType,financialPositionLineClassification"));
  }

  private static TrialBalanceReport sampleTrialBalanceReport() {
    return trialBalanceReport(
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
        allPostingKinds(),
        List.of(
            new TrialBalanceRow(
                declaredCashAccount(),
                currencyBalance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT))),
        List.of());
  }

  private static AccountLedgerReport sampleAccountLedgerReport() {
    return new AccountLedgerReport(
        bookIdentity(),
        declaredCashAccount(),
        EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        allPostingKinds(),
        AccountLedgerPagination.firstPage(50),
        List.of(currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
        List.of(
            new AccountLedgerEntry(
                postingFact(),
                currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                money("EUR", "10.00"),
                BalanceSide.DEBIT,
                null)),
        List.of(currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)));
  }

  private static PeriodSummaryReport samplePeriodSummaryReport() {
    return new PeriodSummaryReport(
        bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        allPostingKinds(),
        1,
        2,
        2,
        List.of(
            new PeriodCurrencySummary(
                currencyBalance("EUR", "10.00", "10.00", "0.00", BalanceSide.ZERO))),
        List.of(
            new PeriodAccountActivityRow(
                declaredCashAccount(),
                currencyBalance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT))));
  }

  @Test
  void writeIncomeStatementText_rendersNoneWhenNetIncomeTotalsAreAbsent() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(outputStream))
        .writeIncomeStatementResult(
            new IncomeStatementResult.Reported(
                new dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport(
                    bookIdentity(),
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"),
                    EffectiveDateRange.of(
                        LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
                    standardOnly(),
                    CliFixtureSupport.sampleIncomeStatementReport().sections(),
                    List.of(),
                    CliFixtureSupport.sampleIncomeStatementReport().comparativeSections(),
                    List.of())),
            OutputMode.TEXT);
    String output = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("Net Income Totals"));
    assertTrue(output.contains("Zero across all currencies."));
  }

  @Test
  void writePrimaryStatementRejections_emitJsonEnvelopesAcrossOutputModes() throws IOException {
    ByteArrayOutputStream financialPositionOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(financialPositionOutput))
        .writeFinancialPositionResult(
            new FinancialPositionResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            OutputMode.JSON);
    assertEquals("rejected", readJson(financialPositionOutput).path("status").stringValue());

    ByteArrayOutputStream incomeStatementOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(incomeStatementOutput))
        .writeIncomeStatementResult(
            new IncomeStatementResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            OutputMode.TEXT);
    String rendered = incomeStatementOutput.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Rejected"), rendered);
    assertTrue(rendered.contains("query-book-not-initialized"), rendered);

    ByteArrayOutputStream changesInEquityOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(changesInEquityOutput))
        .writeChangesInEquityResult(
            new ChangesInEquityResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            OutputMode.JSON);
    assertEquals(
        "query-book-not-initialized", readJson(changesInEquityOutput).path("code").stringValue());
  }
}
