package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination;
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
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationCodeSummary;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import dev.erst.fingrind.contract.tax.TaxQueryRejection;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.contract.tax.TaxRegistrationPageCursor;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
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
  void writeQueryRejection_rendersTextWhenTextModeIsSelected() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
        OutputMode.TEXT);
    String rendered = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Rejected"), rendered);
    assertTrue(rendered.contains("query-book-not-initialized"), rendered);
  }

  @Test
  void queryRejectionWriter_emitsOneJsonEnvelopeAcrossTextJsonAndCsvModes() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));
    CliEnvelopeJsonModels.Envelope<?> envelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED,
            null,
            "query-book-not-initialized",
            "The book is not initialized.",
            null,
            null,
            null,
            null,
            null);
    outputChannel.writeQueryRejection(envelope);
    JsonNode json = readJson(outputStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("query-book-not-initialized", json.path("code").stringValue());
    outputStream.reset();
    outputChannel.writeQueryRejection(envelope);
    json = readJson(outputStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("query-book-not-initialized", json.path("code").stringValue());
    outputStream.reset();
    outputChannel.writeQueryRejection(envelope);
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
    CliEnvelopeJsonModels.Envelope<?> envelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED,
            null,
            "query-book-not-initialized",
            "The book is not initialized.",
            null,
            null,
            null,
            null,
            null);

    outputChannel.writeQueryRejection(envelope);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode json = readJson(diagnosticsStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("query-book-not-initialized", json.path("code").stringValue());

    diagnosticsStream.reset();
    outputChannel.writeQueryRejection(envelope);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    json = readJson(diagnosticsStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("query-book-not-initialized", json.path("code").stringValue());
  }

  @Test
  void writeQueryResults_writeSuccessAndRejectionEnvelopes() throws IOException {
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
            new BookQueryRejection.PostingNotFound(new PostingId("7982b5de-2f28-355e-9911-9ca85b4f5a67"))));
    ByteArrayOutputStream listPostingsOutput = new ByteArrayOutputStream();
    CliResponseWriter listPostingsWriter =
        new CliResponseWriter(utf8PrintStream(listPostingsOutput));
    listPostingsWriter.writeListPostingsResult(
        listedPostings(postingPage(List.of(postingFact), 10, java.util.Optional.empty())));
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
    assertJsonContains(inspectionOutput, "\"bookStartEffectiveDate\":\"2026-01-01\"");
    assertJsonContains(missingInspectionOutput, "\"canInitializeWithOpenBook\":true");
    assertFalse(
        missingInspectionOutput.toString(StandardCharsets.UTF_8).contains("\"initializedAt\""));
    assertJsonContains(getPostingOutput, "\"reason\":\"full reversal\"");
    assertJsonContains(getPostingOutput, "\"priorPostingId\":\"posting-0\"");
    assertJsonContains(getPostingRejectionOutput, "\"code\":\"posting-not-found\"");
    assertJsonContains(getPostingRejectionOutput, "\"postingId\":\"posting-9\"");
    assertJsonContains(listPostingsOutput, "\"postings\":[");
    assertJsonContains(listPostingsRejectionOutput, "\"accountCode\":\"9999\"");
    JsonNode balanceJson = readJson(balanceOutput);
    assertEquals("account-balance", balanceJson.path("payload").path("family").stringValue());
    assertEquals(
        "2026-04-01",
        balanceJson.path("payload").path("resolvedQuery").path("effectiveDateFrom").stringValue());
    assertEquals(
        "DEBIT",
        balanceJson.path("payload").path("balances").get(0).path("balanceSide").stringValue());
    assertJsonContains(balanceRejectionOutput, "\"code\":\"query-book-not-initialized\"");
  }

  @Test
  void nonReportReadQueries_shareTheCanonicalMachinePayloadSpine() throws IOException {
    PostingFact postingFact = postingFact();

    ByteArrayOutputStream accountOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(accountOutput))
        .writeListAccountsResult(
            listedAccounts(accountPage(List.of(declaredCashAccount()), 10, Optional.empty())),
            OutputMode.JSON);
    assertCanonicalQueryPayload(readJson(accountOutput), "list-accounts", "accounts");

    ByteArrayOutputStream postingOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingOutput))
        .writeListPostingsResult(
            listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.JSON);
    assertCanonicalQueryPayload(readJson(postingOutput), "list-postings", "postings");

    ByteArrayOutputStream detailsOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(detailsOutput))
        .writeGetPostingResult(foundPosting(postingFact), OutputMode.JSON);
    assertCanonicalQueryPayload(readJson(detailsOutput), "get-posting", "posting");

    ByteArrayOutputStream registrationOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(registrationOutput))
        .writeListTaxRegistrationsResult(
            listedTaxRegistrations(
                new TaxRegistrationPage(
                    bookIdentity(),
                    List.of(declaredTaxRegistration("LV40001234567")),
                    10,
                    Optional.empty())),
            OutputMode.JSON);
    assertCanonicalQueryPayload(
        readJson(registrationOutput), "list-tax-registrations", "registrations");
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
            listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.TEXT);
    String postingRegisterText = postingRegisterTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(postingRegisterText.contains("Accounts"));
    assertTrue(postingRegisterText.contains("Reversal"));
    assertTrue(postingRegisterText.contains("Reversal"));
    assertTrue(postingRegisterText.contains("1000, 2000"));
    assertTrue(postingRegisterText.contains("10.00"));
    assertTrue(postingRegisterText.contains("posting-"));
    ByteArrayOutputStream postingRegisterCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterCsvOutput))
        .writeListPostingsResult(
            listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.CSV);
    String postingRegisterCsv = postingRegisterCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        postingRegisterCsv.startsWith(
            "exportFamily,rowId,recordKind,effectiveDate,recordedAt,postingId,postingKind,postingOriginKind,reversalState,reversesPostingId,reversedByPostingId,currencyCode,debitTotal,creditTotal,accountCodes,sourceDocumentIds,sourceDocumentTypes,approvalIds,approvalDecisions,message"));
    assertTrue(
        postingRegisterCsv.contains(
            "postings,posting:posting-1,postings,2026-04-07,2026-04-07T10:15:30Z,posting-1,STANDARD,REVERSAL,reversal,posting-0,,EUR,10.00,10.00,1000|2000,document-idem-1,cash-receipt,,,"));
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
            "family,accountCode,accountName,accountType,normalBalance,active,currencyCode,debitTotalCurrencyCode,debitTotalMinorUnits"));
    assertTrue(balanceCsv.contains("account-balance,1000,Cash,ASSET,DEBIT,true,EUR,EUR,1000"));
  }

  private static void assertCanonicalQueryPayload(
      JsonNode envelope, String family, String recordsField) {
    JsonNode payload = envelope.path("payload");
    assertEquals("ok", envelope.path("status").stringValue());
    assertEquals(family, payload.path("family").stringValue());
    assertTrue(payload.has("bookIdentity"), payload.toString());
    assertTrue(payload.has("resolvedQuery"), payload.toString());
    assertTrue(payload.has("generatedAt"), payload.toString());
    assertTrue(payload.has(recordsField), payload.toString());
    assertFalse(payload.has("context"), payload.toString());
    assertFalse(payload.has("limit"), payload.toString());
    assertFalse(payload.has("nextCursor"), payload.toString());
  }

  @Test
  void writeTaxQueryResults_supportSuccessAndRejectionOutputModes() throws IOException {
    DeclaredTaxRegistration registrationWithoutNumber = declaredTaxRegistration(null);
    DeclaredTaxRegistration registrationWithNumber = declaredTaxRegistration("LV40001234567");
    TaxRegistrationPageCursor emptyCursor =
        new TaxRegistrationPageCursor(new TaxRegistrationId("vat-empty-next"));
    TaxRegistrationPageCursor listedCursor =
        new TaxRegistrationPageCursor(new TaxRegistrationId("vat-listed-next"));
    TaxRegistrationPage emptyPage =
        new TaxRegistrationPage(bookIdentity(), List.of(), 1, Optional.of(emptyCursor));
    TaxRegistrationPage listedPage =
        new TaxRegistrationPage(
            bookIdentity(), List.of(registrationWithNumber), 1, Optional.of(listedCursor));

    ByteArrayOutputStream emptyTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(emptyTextOutput))
        .writeListTaxRegistrationsResult(listedTaxRegistrations(emptyPage), OutputMode.TEXT);
    String emptyText = emptyTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(emptyText.contains("No tax registrations matched the selected scope."), emptyText);
    assertTrue(emptyText.contains(emptyCursor.wireValue()), emptyText);

    ByteArrayOutputStream listedTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(listedTextOutput))
        .writeListTaxRegistrationsResult(listedTaxRegistrations(listedPage), OutputMode.TEXT);
    String listedText = listedTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(listedText.contains("Tax Registrations"), listedText);
    assertTrue(listedText.contains("vat-lv"), listedText);
    assertTrue(listedText.contains(listedCursor.wireValue()), listedText);

    ByteArrayOutputStream listedJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(listedJsonOutput))
        .writeListTaxRegistrationsResult(
            listedTaxRegistrations(
                new TaxRegistrationPage(
                    bookIdentity(), List.of(registrationWithoutNumber), 10, Optional.empty())),
            OutputMode.JSON);
    JsonNode listedJson = readJson(listedJsonOutput);
    assertEquals("ok", listedJson.path("status").stringValue());
    assertTrue(
        listedJson
                .path("payload")
                .path("registrations")
                .get(0)
                .path("registrationNumber")
                .isMissingNode()
            || listedJson
                .path("payload")
                .path("registrations")
                .get(0)
                .path("registrationNumber")
                .isNull());

    ByteArrayOutputStream listRejectionTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(listRejectionTextOutput))
        .writeListTaxRegistrationsResult(
            new ListTaxRegistrationsResult.Rejected(new TaxQueryRejection.BookNotInitialized()),
            OutputMode.TEXT);
    String listRejectionText = listRejectionTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(listRejectionText.contains("tax-query-book-not-initialized"), listRejectionText);

    TaxObligationReport emptyObligation = taxObligationReport(registrationWithNumber, List.of());
    ByteArrayOutputStream obligationCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(obligationCsvOutput))
        .writeTaxObligationResult(
            new TaxObligationResult.Reported(emptyObligation), OutputMode.CSV);
    String obligationCsv = obligationCsvOutput.toString(StandardCharsets.UTF_8);
    assertEquals(
        "family,taxCode,taxCodeName,application,postings,taxableCurrencyCode,taxableMinorUnits,taxCurrencyCode,taxMinorUnits,grossCurrencyCode,grossMinorUnits,outputTaxCurrencyCode,outputTaxMinorUnits,recoverableInputTaxCurrencyCode,recoverableInputTaxMinorUnits,nonrecoverableInputTaxCurrencyCode,nonrecoverableInputTaxMinorUnits,netPayableCurrencyCode,netPayableMinorUnits,netReceivableCurrencyCode,netReceivableMinorUnits\n",
        obligationCsv);

    ByteArrayOutputStream obligationRejectionJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(obligationRejectionJsonOutput))
        .writeTaxObligationResult(
            new TaxObligationResult.Rejected(
                new TaxQueryRejection.UnknownTaxRegistration(new TaxRegistrationId("vat-missing"))),
            OutputMode.JSON);
    JsonNode obligationRejection = readJson(obligationRejectionJsonOutput);
    assertEquals("rejected", obligationRejection.path("status").stringValue());
    assertEquals("unknown-tax-registration", obligationRejection.path("code").stringValue());
    assertEquals(
        "vat-missing", obligationRejection.path("details").path("taxRegistrationId").stringValue());

    assertEquals(
        2,
        CliBookQueryExitCodes.exitCodeFor(
            new ListTaxRegistrationsResult.Rejected(new TaxQueryRejection.BookNotInitialized())));
    assertEquals(
        2,
        CliBookQueryExitCodes.exitCodeFor(
            new TaxObligationResult.Rejected(
                new TaxQueryRejection.ObligationPeriodMismatch(
                    TaxObligationFrequency.MONTHLY,
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-15")))));
  }

  @Test
  void writeQueryResults_preserveApprovalEvidenceInCsvOutputWhenPresent() {
    PostingFact postingFact = postingFactWithApproval();

    ByteArrayOutputStream postingRegisterTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterTextOutput))
        .writeListPostingsResult(
            listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.TEXT);
    String postingRegisterText = postingRegisterTextOutput.toString(StandardCharsets.UTF_8);
    assertFalse(postingRegisterText.contains("Approvals"));
    assertTrue(postingRegisterText.contains("1000, 2000"));

    ByteArrayOutputStream postingRegisterCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterCsvOutput))
        .writeListPostingsResult(
            listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
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
    assertTrue(accountLedgerText.contains("Opening Balances"));
    assertTrue(accountLedgerText.contains("EUR 10.00 Debit"));
    assertTrue(accountLedgerText.contains("Ledger Entries"));
    assertTrue(accountLedgerText.contains("Counterpart account codes"));
    assertTrue(accountLedgerText.contains("posting-"));
    ByteArrayOutputStream accountLedgerJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(accountLedgerJsonOutput))
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
    assertEquals("posting-1", ledgerRow.path("postingId").stringValue());
    assertEquals("2026-04-07", ledgerRow.path("effectiveDate").stringValue());
    assertEquals(
        "1000", ledgerRow.path("movement").path("debitTotal").path("minorUnits").stringValue());
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
            "family,accountCode,postingId,effectiveDate,movementCurrencyCode,debitTotalCurrencyCode,debitTotalMinorUnits"));
    assertTrue(accountLedgerCsv.contains("account-ledger,1000,posting-1,2026-04-07,EUR,EUR,1000"));
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
    assertEquals("period-summary", periodSummaryJson.path("payload").path("family").stringValue());
    assertEquals(
        "2026-04-01",
        periodSummaryJson.path("payload").path("resolvedQuery").path("periodStart").stringValue());
    assertEquals(1, periodSummaryJson.path("payload").path("postingCount").intValue());
    JsonNode accountActivity = periodSummaryJson.path("payload").path("accountActivity").get(0);
    assertEquals("1000", accountActivity.path("accountCode").stringValue());
    assertTrue(accountActivity.path("active").booleanValue());
    ByteArrayOutputStream periodSummaryCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(periodSummaryCsvOutput))
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
    new CliResponseWriter(utf8PrintStream(financialPositionJsonOutput))
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
            .startsWith("family,reportPeriod,sectionKind,lineCode,lineName,lineType"));

    ByteArrayOutputStream incomeStatementJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(incomeStatementJsonOutput))
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
            .startsWith("family,reportPeriod,sectionKind,lineCode,lineName,lineType"));

    ByteArrayOutputStream changesInEquityJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(changesInEquityJsonOutput))
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
    assertTrue(output.contains("Net Income Totals"));
    assertTrue(output.contains("Zero across all currencies."));
  }

  @Test
  void writePrimaryStatementRejections_emitJsonEnvelopesAcrossOutputModes() throws IOException {
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
    String rendered = incomeStatementOutput.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Rejected"), rendered);
    assertTrue(rendered.contains("query-book-not-initialized"), rendered);

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
    assertEquals("CASH", payload.path("bookIdentity").path("accountingBasis").stringValue());
    assertEquals("EUR", payload.path("bookIdentity").path("functionalCurrency").stringValue());
    assertEquals("01-01", payload.path("bookIdentity").path("fiscalYearStart").stringValue());
    assertEquals(
        "2026-01-01", payload.path("bookIdentity").path("bookStartEffectiveDate").stringValue());
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

  private static DeclaredTaxRegistration declaredTaxRegistration(
      @Nullable String registrationNumber) {
    return new DeclaredTaxRegistration(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        registrationNumber == null ? null : new TaxRegistrationNumber(registrationNumber),
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)),
        Instant.parse("2026-04-17T10:20:30Z"));
  }

  private static TaxObligationReport taxObligationReport(
      DeclaredTaxRegistration registration, List<TaxObligationCodeSummary> codeSummaries) {
    return new TaxObligationReport(
        bookIdentity(),
        registration,
        new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        LocalDate.parse("2026-05-20"),
        codeSummaries,
        new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "3150"),
        new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "2100"),
        new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "1200"),
        new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "1050"),
        new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "0"));
  }
}
