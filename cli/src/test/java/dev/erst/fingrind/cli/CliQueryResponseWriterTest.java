package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for {@link CliResponseWriter}. */
class CliQueryResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writeQueryRejection_keepsJsonEnvelopeOutsideTextMode() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
        OutputMode.JSON);
    JsonNode json = readJson(outputStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("query-book-not-initialized", json.path("code").stringValue());
  }

  @Test
  void writeQueryRejection_supportsTextOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
        OutputMode.TEXT);
    String text = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("Rejected"));
    assertTrue(text.contains("query-book-not-initialized"));
  }

  @Test
  void queryRejectionWriter_coversJsonAndTextBranches() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));
    CliEnvelopeJsonModels.RejectedEnvelope envelope =
        new CliEnvelopeJsonModels.RejectedEnvelope(
            ProtocolEnvelopeStatus.REJECTED,
            "query-book-not-initialized",
            "The book is not initialized.",
            null,
            null,
            null);
    outputChannel.writeQueryRejection(OutputMode.TEXT, envelope);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Rejected"));
    outputStream.reset();
    outputChannel.writeQueryRejection(OutputMode.JSON, envelope);
    JsonNode json = readJson(outputStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("query-book-not-initialized", json.path("code").stringValue());
    outputStream.reset();
    outputChannel.writeQueryRejection(OutputMode.CSV, envelope);
    json = readJson(outputStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("query-book-not-initialized", json.path("code").stringValue());
  }

  @Test
  void queryRejectionWriter_routesMachineReadableRejectionsToDiagnosticsStream()
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = outputChannel(outputStream, diagnosticsStream);
    CliEnvelopeJsonModels.RejectedEnvelope envelope =
        new CliEnvelopeJsonModels.RejectedEnvelope(
            ProtocolEnvelopeStatus.REJECTED,
            "query-book-not-initialized",
            "The book is not initialized.",
            null,
            null,
            null);

    outputChannel.writeQueryRejection(OutputMode.JSON, envelope);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode json = readJson(diagnosticsStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("query-book-not-initialized", json.path("code").stringValue());

    diagnosticsStream.reset();
    outputChannel.writeQueryRejection(OutputMode.CSV, envelope);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    json = readJson(diagnosticsStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("query-book-not-initialized", json.path("code").stringValue());
  }

  @Test
  void writeQueryResults_writeSuccessAndRejectionEnvelopes() {
    PostingFact postingFact = postingFact();
    AccountBalanceSnapshot balanceSnapshot =
        new AccountBalanceSnapshot(
            bookIdentity(),
            CliIoFixtureSupport.declaredAccount(
                "1000",
                "Cash",
                dev.erst.fingrind.core.AccountType.ASSET,
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z")),
            java.util.Optional.of(LocalDate.parse("2026-04-01")),
            java.util.Optional.of(LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            List.of(currencyBalance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT)));
    ByteArrayOutputStream inspectionOutput = new ByteArrayOutputStream();
    CliResponseWriter inspectionWriter = new CliResponseWriter(utf8PrintStream(inspectionOutput));
    inspectionWriter.writeBookInspection(
        Path.of("book.sqlite"),
        initializedBookInspection(1_179_079_236, 3, 3, Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream missingInspectionOutput = new ByteArrayOutputStream();
    CliResponseWriter missingInspectionWriter =
        new CliResponseWriter(utf8PrintStream(missingInspectionOutput));
    missingInspectionWriter.writeBookInspection(
        Path.of("missing.sqlite"), new BookInspection.Missing(3));
    ByteArrayOutputStream getPostingOutput = new ByteArrayOutputStream();
    CliResponseWriter getPostingWriter = new CliResponseWriter(utf8PrintStream(getPostingOutput));
    getPostingWriter.writeGetPostingResult(foundPosting(postingFact));
    ByteArrayOutputStream getPostingRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter getPostingRejectionWriter =
        new CliResponseWriter(utf8PrintStream(getPostingRejectionOutput));
    getPostingRejectionWriter.writeGetPostingResult(
        new GetPostingResult.Rejected(
            new BookQueryRejection.PostingNotFound(new PostingId("posting-9"))));
    ByteArrayOutputStream listPostingsOutput = new ByteArrayOutputStream();
    CliResponseWriter listPostingsWriter =
        new CliResponseWriter(utf8PrintStream(listPostingsOutput));
    listPostingsWriter.writeListPostingsResult(
        new ListPostingsResult.Listed(
            postingPage(List.of(postingFact), 10, java.util.Optional.empty())));
    ByteArrayOutputStream listPostingsRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter listPostingsRejectionWriter =
        new CliResponseWriter(utf8PrintStream(listPostingsRejectionOutput));
    listPostingsRejectionWriter.writeListPostingsResult(
        new ListPostingsResult.Rejected(
            new BookQueryRejection.UnknownAccount(new AccountCode("9999"))));
    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    CliResponseWriter balanceWriter = new CliResponseWriter(utf8PrintStream(balanceOutput));
    balanceWriter.writeAccountBalanceResult(new AccountBalanceResult.Reported(balanceSnapshot));
    ByteArrayOutputStream balanceRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter balanceRejectionWriter =
        new CliResponseWriter(utf8PrintStream(balanceRejectionOutput));
    balanceRejectionWriter.writeAccountBalanceResult(
        new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()));
    assertJsonContains(inspectionOutput, "\"bookFile\"");
    assertJsonContains(inspectionOutput, "\"state\":\"initialized\"");
    assertJsonContains(inspectionOutput, "\"entityName\":\"Acme Studio\"");
    assertJsonContains(inspectionOutput, "\"functionalCurrency\":\"EUR\"");
    assertJsonContains(inspectionOutput, "\"fiscalYearStart\":\"01-01\"");
    assertJsonContains(missingInspectionOutput, "\"canInitializeWithOpenBook\":true");
    assertFalse(
        missingInspectionOutput.toString(StandardCharsets.UTF_8).contains("\"initializedAt\""));
    assertJsonContains(getPostingOutput, "\"reason\":\"full reversal\"");
    assertJsonContains(getPostingOutput, "\"priorPostingId\":\"posting-0\"");
    assertJsonContains(getPostingRejectionOutput, "\"code\":\"posting-not-found\"");
    assertJsonContains(getPostingRejectionOutput, "\"postingId\":\"posting-9\"");
    assertJsonContains(listPostingsOutput, "\"postings\":[");
    assertJsonContains(listPostingsRejectionOutput, "\"accountCode\":\"9999\"");
    assertJsonContains(balanceOutput, "\"effectiveDateFrom\":\"2026-04-01\"");
    assertJsonContains(balanceOutput, "\"balanceSide\":\"DEBIT\"");
    assertJsonContains(balanceRejectionOutput, "\"code\":\"query-book-not-initialized\"");
  }

  @Test
  void writeQueryResults_supportTextAndCsvOutputModes() {
    PostingFact postingFact = postingFact();
    AccountBalanceSnapshot balanceSnapshot =
        new AccountBalanceSnapshot(
            bookIdentity(),
            declaredCashAccount(),
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            List.of(currencyBalance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT)));
    ByteArrayOutputStream postingRegisterTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterTextOutput))
        .writeListPostingsResult(
            new ListPostingsResult.Listed(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.TEXT);
    String postingRegisterText = postingRegisterTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(postingRegisterText.contains("Accounts"));
    assertTrue(postingRegisterText.contains("Reversal adjustment"));
    assertTrue(postingRegisterText.contains("Reversal"));
    assertTrue(postingRegisterText.contains("1000, 2000"));
    assertTrue(postingRegisterText.contains("10.00"));
    assertTrue(postingRegisterText.contains("posting-"));
    ByteArrayOutputStream postingRegisterCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterCsvOutput))
        .writeListPostingsResult(
            new ListPostingsResult.Listed(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.CSV);
    String postingRegisterCsv = postingRegisterCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        postingRegisterCsv.startsWith(
            "exportFamily,rowId,parentRowId,relationKind,recordKind,effectiveDate,recordedAt,postingId,postingKind,postingOriginKind,reversalState,reversalTarget,currencyCode,debitTotal,creditTotal,accountCode,sourceDocumentId,sourceDocumentType,approvalId,approvalDecision,message"));
    assertTrue(
        postingRegisterCsv.contains(
            "posting-relationships,posting:posting-1,,posting,posting,2026-04-07,2026-04-07T10:15:30Z,posting-1,STANDARD,REVERSAL_ADJUSTMENT,reversal,posting-0,EUR,10.00,10.00"));
    assertTrue(
        postingRegisterCsv.contains(
            "posting-relationships,posting-account:posting-1:1000,posting:posting-1,counterpart-account,account,2026-04-07,2026-04-07T10:15:30Z,posting-1"));
    assertTrue(postingRegisterCsv.contains("document-idem-1"));
    assertTrue(postingRegisterCsv.contains("cash-receipt"));
    assertTrue(postingRegisterCsv.contains("approval-idem-1") || postingRegisterCsv.contains(",,"));
    ByteArrayOutputStream balanceTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(balanceTextOutput))
        .writeAccountBalanceResult(
            new AccountBalanceResult.Reported(balanceSnapshot), OutputMode.TEXT);
    String balanceText = balanceTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(balanceText.contains("Account"));
    assertTrue(balanceText.contains("1000"));
    assertTrue(balanceText.contains("Entity"));
    assertTrue(balanceText.contains("Acme Studio"));
    assertTrue(balanceText.contains("Debit total"));
    assertTrue(balanceText.contains("10.00"));
    assertTrue(balanceText.contains("6.00"));
    ByteArrayOutputStream balanceCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(balanceCsvOutput))
        .writeAccountBalanceResult(
            new AccountBalanceResult.Reported(balanceSnapshot), OutputMode.CSV);
    String balanceCsv = balanceCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        balanceCsv.startsWith(
            "exportFamily,rowId,parentRowId,relationKind,recordKind,accountCode,accountName,accountType,accountRole,normalBalance,effectiveDateFrom,effectiveDateTo,currencyCode,debitTotal,creditTotal,netAmount,balanceSide,message"));
    assertTrue(
        balanceCsv.contains(
            "flat-register,account-balance:1000:EUR,,balance,row,1000,Cash,ASSET,ORDINARY,DEBIT,2026-04-01,2026-04-30,EUR,10.00,4.00,6.00,DEBIT,"));
  }

  @Test
  void writeQueryResults_preserveApprovalEvidenceInCsvOutputWhenPresent() {
    PostingFact postingFact = postingFactWithApproval();

    ByteArrayOutputStream postingRegisterTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterTextOutput))
        .writeListPostingsResult(
            new ListPostingsResult.Listed(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.TEXT);
    String postingRegisterText = postingRegisterTextOutput.toString(StandardCharsets.UTF_8);
    assertFalse(postingRegisterText.contains("Approvals"));
    assertTrue(postingRegisterText.contains("1000, 2000"));

    ByteArrayOutputStream postingRegisterCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterCsvOutput))
        .writeListPostingsResult(
            new ListPostingsResult.Listed(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.CSV);
    String postingRegisterCsv = postingRegisterCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(postingRegisterCsv.contains("document-idem-1"));
    assertTrue(postingRegisterCsv.contains("approval-idem-1"));
  }

  @Test
  void writeTrialBalanceResult_supportJsonTextAndCsvOutputModes() throws IOException {
    TrialBalanceReport trialBalanceReport = sampleTrialBalanceReport();
    ByteArrayOutputStream trialBalanceJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(trialBalanceJsonOutput))
        .writeTrialBalanceResult(
            new TrialBalanceResult.Reported(trialBalanceReport), OutputMode.JSON);
    JsonNode trialBalanceJson = readJson(trialBalanceJsonOutput);
    assertEquals("ok", trialBalanceJson.path("status").stringValue());
    assertEquals(
        "2026-04-30", trialBalanceJson.path("payload").path("effectiveDateAsOf").stringValue());
    assertEquals(
        "Acme Studio",
        trialBalanceJson
            .path("payload")
            .path("context")
            .path("bookIdentity")
            .path("entityName")
            .stringValue());
    assertEquals(
        "1000",
        trialBalanceJson.path("payload").path("rows").get(0).path("accountCode").stringValue());
    assertEquals(
        "1000",
        trialBalanceJson
            .path("payload")
            .path("rows")
            .get(0)
            .path("debitTotal")
            .path("minorUnits")
            .stringValue());
    assertFalse(trialBalanceJson.toString().contains("\"value\""));
    ByteArrayOutputStream trialBalanceTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(trialBalanceTextOutput))
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
    new CliResponseWriter(utf8PrintStream(accountLedgerTextOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult.Reported(
                accountLedgerReport),
            OutputMode.TEXT);
    String accountLedgerText = accountLedgerTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(accountLedgerText.contains("Opening balances"));
    assertTrue(accountLedgerText.contains("EUR 10.00 Debit"));
    assertTrue(accountLedgerText.contains("Running"));
    assertTrue(accountLedgerText.contains("Counterparts"));
    assertTrue(accountLedgerText.contains("posting-"));
    ByteArrayOutputStream accountLedgerJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(accountLedgerJsonOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult.Reported(
                accountLedgerReport),
            OutputMode.JSON);
    JsonNode accountLedgerJson = readJson(accountLedgerJsonOutput);
    assertEquals("1000", accountLedgerJson.path("payload").path("accountCode").stringValue());
    assertEquals(
        "2026-04-01", accountLedgerJson.path("payload").path("effectiveDateFrom").stringValue());
    assertEquals(
        "posting-1",
        accountLedgerJson.path("payload").path("entries").get(0).path("postingId").stringValue());
    assertEquals(
        "reversal",
        accountLedgerJson
            .path("payload")
            .path("entries")
            .get(0)
            .path("reversalState")
            .stringValue());
    assertEquals(
        "document-idem-1",
        accountLedgerJson
            .path("payload")
            .path("entries")
            .get(0)
            .path("evidence")
            .path("sourceDocuments")
            .get(0)
            .path("sourceDocumentId")
            .stringValue());
    assertEquals(
        "2000",
        accountLedgerJson
            .path("payload")
            .path("entries")
            .get(0)
            .path("counterpartAccounts")
            .get(0)
            .stringValue());
    assertFalse(accountLedgerJson.toString().contains("\"postingFact\""));
    ByteArrayOutputStream accountLedgerCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(accountLedgerCsvOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult.Reported(
                accountLedgerReport),
            OutputMode.CSV);
    String accountLedgerCsv = accountLedgerCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        accountLedgerCsv.startsWith(
            "exportFamily,rowId,parentRowId,relationKind,recordKind,accountCode,accountName,accountType,accountRole,normalBalance,active,effectiveDateFrom,effectiveDateTo,currencyCode,openingDebitTotal,openingCreditTotal,openingNetAmount,openingBalanceSide,closingDebitTotal,closingCreditTotal,closingNetAmount,closingBalanceSide,effectiveDate,recordedAt,postingId,postingKind,postingOriginKind,reversalState,reversalTarget,debitAmount,creditAmount,runningNetAmount,runningBalanceSide,counterpartAccountCode,sourceDocumentId,sourceDocumentType,approvalId,approvalDecision,message"));
    assertTrue(
        accountLedgerCsv.contains(
            "posting-relationships,ledger-summary:1000:EUR,,ledger-summary,summary,1000,Cash,ASSET,ORDINARY,DEBIT,true"));
    assertTrue(
        accountLedgerCsv.contains(
            "posting-relationships,ledger-entry:posting-1,,entry,entry,1000,Cash,ASSET,ORDINARY,DEBIT,true"));
    assertTrue(
        accountLedgerCsv.contains(
            "posting-relationships,ledger-counterpart:posting-1:2000,ledger-entry:posting-1,counterpart-account,counterpart-account,1000,Cash,ASSET,ORDINARY,DEBIT,true"));
    assertTrue(accountLedgerCsv.contains("document-idem-1"));
    assertTrue(
        accountLedgerCsv.contains(
            "posting-relationships,ledger-source-document:posting-1:document-idem-1,ledger-entry:posting-1,source-document,source-document,1000,Cash,ASSET,ORDINARY,DEBIT,true"));
  }

  @Test
  void writePeriodSummaryResult_supportJsonTextAndCsvOutputModes() throws IOException {
    PeriodSummaryReport periodSummaryReport = samplePeriodSummaryReport();

    ByteArrayOutputStream periodSummaryTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(periodSummaryTextOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult.Reported(
                periodSummaryReport),
            OutputMode.TEXT);
    String periodSummaryText = periodSummaryTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(periodSummaryText.contains("Posting count"));
    assertTrue(periodSummaryText.contains("Posting line count"));
    assertTrue(periodSummaryText.contains("10.00"));
    ByteArrayOutputStream periodSummaryJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(periodSummaryJsonOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult.Reported(
                periodSummaryReport),
            OutputMode.JSON);
    JsonNode periodSummaryJson = readJson(periodSummaryJsonOutput);
    assertEquals(
        "2026-04-01", periodSummaryJson.path("payload").path("effectiveDateFrom").stringValue());
    assertEquals(1, periodSummaryJson.path("payload").path("postingCount").asInt());
    assertEquals(
        "1000",
        periodSummaryJson
            .path("payload")
            .path("accountActivity")
            .get(0)
            .path("accountCode")
            .stringValue());
    assertFalse(periodSummaryJson.toString().contains("\"account\":{\"accountCode\""));
    ByteArrayOutputStream periodSummaryCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(periodSummaryCsvOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult.Reported(
                periodSummaryReport),
            OutputMode.CSV);
    String periodSummaryCsv = periodSummaryCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        periodSummaryCsv.startsWith(
            "exportFamily,rowId,parentRowId,relationKind,recordKind,subjectKind,subjectCode,subjectName,metricName,metricValue,currencyCode,metricUnit,message"));
    assertTrue(
        periodSummaryCsv.contains(
            "metrics,period-summary:account:1000:debit,period-summary:account:1000,metric,account-activity,account,1000,Cash,debitTotal,10.00,EUR,money,"));
  }

  @Test
  void writeAccountLedgerJson_marksDirectEntriesWithoutReversalTarget() throws IOException {
    AccountLedgerReport directAccountLedgerReport =
        new AccountLedgerReport(
            bookIdentity(),
            declaredCashAccount(),
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(
                new AccountLedgerEntry(
                    CliFixtureSupport.selfPostingFact(),
                    currencyBalance("EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT),
                    money("EUR", "5.00"),
                    BalanceSide.DEBIT)),
            List.of(currencyBalance("EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT)));
    ByteArrayOutputStream directAccountLedgerJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(directAccountLedgerJsonOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult.Reported(
                directAccountLedgerReport),
            OutputMode.JSON);

    JsonNode directAccountLedgerJson = readJson(directAccountLedgerJsonOutput);
    assertEquals(
        "direct",
        directAccountLedgerJson
            .path("payload")
            .path("entries")
            .get(0)
            .path("reversalState")
            .stringValue());
    assertFalse(
        directAccountLedgerJson.path("payload").path("entries").get(0).has("reversalTarget"));
  }

  @Test
  void writePrimaryStatementResults_supportJsonTextAndCsvOutputModes() throws IOException {
    ByteArrayOutputStream financialPositionJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(financialPositionJsonOutput))
        .writeFinancialPositionResult(
            new FinancialPositionResult.Reported(CliFixtureSupport.sampleFinancialPositionReport()),
            OutputMode.JSON);
    JsonNode financialPositionJson = readJson(financialPositionJsonOutput);
    assertEquals("ok", financialPositionJson.path("status").stringValue());
    assertEquals(
        "2026-04-30",
        financialPositionJson.path("payload").path("effectiveDateAsOf").stringValue());
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
    new CliResponseWriter(utf8PrintStream(financialPositionTextOutput))
        .writeFinancialPositionResult(
            new FinancialPositionResult.Reported(CliFixtureSupport.sampleFinancialPositionReport()),
            OutputMode.TEXT);
    assertTrue(
        financialPositionTextOutput
            .toString(StandardCharsets.UTF_8)
            .contains("Financial Position"));

    ByteArrayOutputStream financialPositionCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(financialPositionCsvOutput))
        .writeFinancialPositionResult(
            new FinancialPositionResult.Reported(CliFixtureSupport.sampleFinancialPositionReport()),
            OutputMode.CSV);
    assertTrue(
        financialPositionCsvOutput
            .toString(StandardCharsets.UTF_8)
            .startsWith(
                "exportFamily,rowId,parentRowId,relationKind,reportBasis,recordKind,effectiveDateAsOf,accountType,lineCode,lineName,lineRole,lineType,lineClassification,lineKind,currencyCode,debitTotal,creditTotal,netAmount,balanceSide,message"));

    ByteArrayOutputStream incomeStatementJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(incomeStatementJsonOutput))
        .writeIncomeStatementResult(
            new IncomeStatementResult.Reported(CliFixtureSupport.sampleIncomeStatementReport()),
            OutputMode.JSON);
    JsonNode incomeStatementJson = readJson(incomeStatementJsonOutput);
    assertEquals("ok", incomeStatementJson.path("status").stringValue());
    assertEquals(
        "2026-04-01", incomeStatementJson.path("payload").path("effectiveDateFrom").stringValue());
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
    new CliResponseWriter(utf8PrintStream(incomeStatementTextOutput))
        .writeIncomeStatementResult(
            new IncomeStatementResult.Reported(CliFixtureSupport.sampleIncomeStatementReport()),
            OutputMode.TEXT);
    assertTrue(
        incomeStatementTextOutput.toString(StandardCharsets.UTF_8).contains("Income Statement"));

    ByteArrayOutputStream incomeStatementCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(incomeStatementCsvOutput))
        .writeIncomeStatementResult(
            new IncomeStatementResult.Reported(CliFixtureSupport.sampleIncomeStatementReport()),
            OutputMode.CSV);
    assertTrue(
        incomeStatementCsvOutput
            .toString(StandardCharsets.UTF_8)
            .startsWith(
                "exportFamily,rowId,parentRowId,relationKind,reportBasis,recordKind,effectiveDateFrom,effectiveDateTo,accountType,lineCode,lineName,lineRole,lineType,lineClassification,lineKind,currencyCode,debitTotal,creditTotal,netAmount,balanceSide,message"));

    ByteArrayOutputStream changesInEquityJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(changesInEquityJsonOutput))
        .writeChangesInEquityResult(
            new ChangesInEquityResult.Reported(CliFixtureSupport.sampleChangesInEquityReport()),
            OutputMode.JSON);
    JsonNode changesInEquityJson = readJson(changesInEquityJsonOutput);
    assertEquals("ok", changesInEquityJson.path("status").stringValue());
    assertEquals(
        "2026-04-30", changesInEquityJson.path("payload").path("effectiveDateTo").stringValue());
    assertEquals(
        "3200",
        changesInEquityJson.path("payload").path("rows").get(0).path("lineCode").stringValue());

    ByteArrayOutputStream changesInEquityTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(changesInEquityTextOutput))
        .writeChangesInEquityResult(
            new ChangesInEquityResult.Reported(CliFixtureSupport.sampleChangesInEquityReport()),
            OutputMode.TEXT);
    assertTrue(
        changesInEquityTextOutput.toString(StandardCharsets.UTF_8).contains("Changes In Equity"));

    ByteArrayOutputStream changesInEquityCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(changesInEquityCsvOutput))
        .writeChangesInEquityResult(
            new ChangesInEquityResult.Reported(CliFixtureSupport.sampleChangesInEquityReport()),
            OutputMode.CSV);
    assertTrue(
        changesInEquityCsvOutput
            .toString(StandardCharsets.UTF_8)
            .startsWith(
                "exportFamily,rowId,parentRowId,relationKind,reportBasis,recordKind,effectiveDateFrom,effectiveDateTo,lineCode,lineName,lineRole,lineClassification,lineKind,currencyCode,openingDebitTotal,openingCreditTotal,openingNetAmount,openingBalanceSide,movementDebitTotal,movementCreditTotal,movementNetAmount,movementBalanceSide,closingDebitTotal,closingCreditTotal,closingNetAmount,closingBalanceSide,message"));
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
        List.of(currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
        List.of(
            new AccountLedgerEntry(
                postingFact(),
                currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                money("EUR", "10.00"),
                BalanceSide.DEBIT)),
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
    new CliResponseWriter(utf8PrintStream(outputStream))
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
    assertTrue(output.contains("Net income totals"));
    assertTrue(output.contains("Zero across all currencies."));
  }

  @Test
  void writePrimaryStatementRejections_supportJsonAndTextOutput() throws IOException {
    ByteArrayOutputStream financialPositionOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(financialPositionOutput))
        .writeFinancialPositionResult(
            new FinancialPositionResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            OutputMode.JSON);
    assertEquals("rejected", readJson(financialPositionOutput).path("status").stringValue());

    ByteArrayOutputStream incomeStatementOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(incomeStatementOutput))
        .writeIncomeStatementResult(
            new IncomeStatementResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            OutputMode.TEXT);
    assertTrue(incomeStatementOutput.toString(StandardCharsets.UTF_8).contains("Rejected"));

    ByteArrayOutputStream changesInEquityOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(changesInEquityOutput))
        .writeChangesInEquityResult(
            new ChangesInEquityResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            OutputMode.JSON);
    assertEquals(
        "query-book-not-initialized", readJson(changesInEquityOutput).path("code").stringValue());
  }

  @Test
  void writeInspection_includesBookIdentityForInitializedBooks() throws IOException {
    JsonNode payload =
        writeInspection(
            initializedBookInspection(1_179_079_236, 3, 3, Instant.parse("2026-04-07T10:15:30Z")));

    assertEquals("initialized", payload.path("state").stringValue());
    assertEquals(1_179_079_236, payload.path("applicationId").asInt());
    assertEquals(3, payload.path("detectedBookFormatVersion").asInt());
    assertEquals(3, payload.path("supportedBookFormatVersion").asInt());
    assertEquals(
        "hard-break-reject-older-formats",
        payload.path("migrationPolicy").path("mode").stringValue());
    assertFalse(payload.path("migrationPolicy").path("inPlaceUpgradeSupported").asBoolean());
    assertEquals("2026-04-07T10:15:30Z", payload.path("initializedAt").stringValue());
    assertEquals("Acme Studio", payload.path("bookIdentity").path("entityName").stringValue());
    assertEquals("EUR", payload.path("bookIdentity").path("functionalCurrency").stringValue());
    assertEquals("01-01", payload.path("bookIdentity").path("fiscalYearStart").stringValue());
  }

  @Test
  void writeBookInspection_writesEveryExistingBookVariant() throws IOException {
    List<BookInspection> inspections =
        List.of(
            new BookInspection.Existing(BookInspection.Status.BLANK_SQLITE, 1_179_079_236, 0, 3),
            new BookInspection.Existing(BookInspection.Status.FOREIGN_SQLITE, 1_179_079_236, 0, 3),
            new BookInspection.Existing(
                BookInspection.Status.UNSUPPORTED_FORMAT_VERSION, 1_179_079_236, 1, 3),
            new BookInspection.Existing(
                BookInspection.Status.INCOMPLETE_FINGRIND, 1_179_079_236, 2, 3));
    List<String> states =
        List.of(
            "blank-sqlite", "foreign-sqlite", "unsupported-format-version", "incomplete-fingrind");
    for (int index = 0; index < inspections.size(); index++) {
      JsonNode payload = writeInspection(inspections.get(index));
      assertEquals(states.get(index), payload.path("state").stringValue());
      assertEquals(1_179_079_236, payload.path("applicationId").asInt());
      assertEquals(3, payload.path("supportedBookFormatVersion").asInt());
      assertEquals(
          "hard-break-reject-older-formats",
          payload.path("migrationPolicy").path("mode").stringValue());
      assertFalse(payload.path("migrationPolicy").path("olderFormatsAccepted").asBoolean());
      assertFalse(payload.path("migrationPolicy").path("newerFormatsAccepted").asBoolean());
      assertFalse(payload.has("initializedAt"));
    }
  }
}
