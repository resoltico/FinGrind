package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
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

/** Contract coverage for book-query response projections across supported output modes. */
class CliQueryResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writeQueryRejection_keepsJsonEnvelopeOutsideTextMode() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliBookReadResponseWriterFixture responseWriter =
        new CliBookReadResponseWriterFixture(utf8PrintStream(outputStream));
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
    CliBookReadResponseWriterFixture responseWriter =
        new CliBookReadResponseWriterFixture(utf8PrintStream(outputStream));
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
    CliBookReadResponseWriterFixture inspectionWriter =
        new CliBookReadResponseWriterFixture(utf8PrintStream(inspectionOutput));
    inspectionWriter.writeBookInspection(
        Path.of("book.sqlite"),
        initializedBookInspection(1_179_079_236, 3, 3, Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream missingInspectionOutput = new ByteArrayOutputStream();
    CliBookReadResponseWriterFixture missingInspectionWriter =
        new CliBookReadResponseWriterFixture(utf8PrintStream(missingInspectionOutput));
    missingInspectionWriter.writeBookInspection(
        Path.of("missing.sqlite"), new BookInspection.Missing(3));
    ByteArrayOutputStream getPostingOutput = new ByteArrayOutputStream();
    CliBookReadResponseWriterFixture getPostingWriter =
        new CliBookReadResponseWriterFixture(utf8PrintStream(getPostingOutput));
    getPostingWriter.writeGetPostingResult(foundPosting(postingFact));
    ByteArrayOutputStream getPostingRejectionOutput = new ByteArrayOutputStream();
    CliBookReadResponseWriterFixture getPostingRejectionWriter =
        new CliBookReadResponseWriterFixture(utf8PrintStream(getPostingRejectionOutput));
    getPostingRejectionWriter.writeGetPostingResult(
        new GetPostingResult.Rejected(
            new BookQueryRejection.PostingNotFound(
                new PostingId("7982b5de-2f28-355e-9911-9ca85b4f5a67"))));
    ByteArrayOutputStream listPostingsOutput = new ByteArrayOutputStream();
    CliBookReadResponseWriterFixture listPostingsWriter =
        new CliBookReadResponseWriterFixture(utf8PrintStream(listPostingsOutput));
    listPostingsWriter.writeListPostingsResult(
        listedPostings(postingPage(List.of(postingFact), 10, java.util.Optional.empty())));
    ByteArrayOutputStream listPostingsRejectionOutput = new ByteArrayOutputStream();
    CliBookReadResponseWriterFixture listPostingsRejectionWriter =
        new CliBookReadResponseWriterFixture(utf8PrintStream(listPostingsRejectionOutput));
    listPostingsRejectionWriter.writeListPostingsResult(
        new ListPostingsResult.Rejected(
            new BookQueryRejection.UnknownAccount(new AccountCode("9999"))));
    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    CliReportResponseWriterFixture balanceWriter =
        new CliReportResponseWriterFixture(utf8PrintStream(balanceOutput));
    balanceWriter.writeAccountBalanceResult(new AccountBalanceResult.Reported(balanceSnapshot));
    ByteArrayOutputStream balanceRejectionOutput = new ByteArrayOutputStream();
    CliReportResponseWriterFixture balanceRejectionWriter =
        new CliReportResponseWriterFixture(utf8PrintStream(balanceRejectionOutput));
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
    assertJsonContains(
        getPostingOutput, "\"priorPostingId\":\"e888fd00-a501-341d-9a6b-8d9059757d1b\"");
    assertJsonContains(getPostingRejectionOutput, "\"code\":\"posting-not-found\"");
    assertJsonContains(
        getPostingRejectionOutput, "\"postingId\":\"7982b5de-2f28-355e-9911-9ca85b4f5a67\"");
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
    assertEquals(0, CliBookQueryExitCodes.exitCodeFor(foundPosting(postingFact)));
    assertEquals(
        2,
        CliBookQueryExitCodes.exitCodeFor(
            new GetPostingResult.Rejected(
                new BookQueryRejection.PostingNotFound(
                    new PostingId("7982b5de-2f28-355e-9911-9ca85b4f5a67")))));
    assertEquals(
        0,
        CliBookQueryExitCodes.exitCodeFor(
            listedPostings(postingPage(List.of(postingFact), 10, java.util.Optional.empty()))));
    assertEquals(
        2,
        CliBookQueryExitCodes.exitCodeFor(
            new ListPostingsResult.Rejected(
                new BookQueryRejection.UnknownAccount(new AccountCode("9999")))));
  }

  @Test
  void nonReportReadQueries_shareTheCanonicalMachinePayloadSpine() throws IOException {
    PostingFact postingFact = postingFact();

    ByteArrayOutputStream accountOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(accountOutput))
        .writeListAccountsResult(
            listedAccounts(accountPage(List.of(declaredCashAccount()), 10, Optional.empty())),
            OutputMode.JSON);
    assertCanonicalQueryPayload(readJson(accountOutput), "list-accounts", "accounts");

    ByteArrayOutputStream postingOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(postingOutput))
        .writeListPostingsResult(
            listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.JSON);
    assertCanonicalQueryPayload(readJson(postingOutput), "list-postings", "postings");

    ByteArrayOutputStream detailsOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(detailsOutput))
        .writeGetPostingResult(foundPosting(postingFact), OutputMode.JSON);
    assertCanonicalQueryPayload(readJson(detailsOutput), "get-posting", "posting");

    ByteArrayOutputStream registrationOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(registrationOutput))
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
    new CliBookReadResponseWriterFixture(utf8PrintStream(postingRegisterTextOutput))
        .writeListPostingsResult(
            listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.TEXT);
    String postingRegisterText = postingRegisterTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(postingRegisterText.contains("Accounts"));
    assertTrue(postingRegisterText.contains("Reversal"));
    assertTrue(postingRegisterText.contains("Reversal"));
    assertTrue(postingRegisterText.contains("1000, 2000"));
    assertTrue(postingRegisterText.contains("10.00"));
    assertTrue(postingRegisterText.contains("bdc03c47-a16c-3688-a18f-2445894bbc69"));
    ByteArrayOutputStream postingRegisterCsvOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(postingRegisterCsvOutput))
        .writeListPostingsResult(
            listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.CSV);
    String postingRegisterCsv = postingRegisterCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        postingRegisterCsv.startsWith(
            "exportFamily,rowId,recordKind,effectiveDate,recordedAt,postingId,postingKind,postingOriginKind,reversalState,reversesPostingId,reversedByPostingId,attestationOperationOrder,attestationOperationHead,currencyCode,debitTotal,creditTotal,accountCodes,sourceDocumentIds,sourceDocumentTypes,approvalIds,approvalDecisions,message"));
    assertTrue(
        postingRegisterCsv.contains(
            "postings,posting:bdc03c47-a16c-3688-a18f-2445894bbc69,postings,2026-04-07,2026-04-07T10:15:30Z,bdc03c47-a16c-3688-a18f-2445894bbc69,STANDARD,REVERSAL,reversal,e888fd00-a501-341d-9a6b-8d9059757d1b,,,,EUR,10.00,10.00,1000|2000,document-idem-1,cash-receipt,,,"));
    assertTrue(postingRegisterCsv.contains("document-idem-1"));
    assertTrue(postingRegisterCsv.contains("cash-receipt"));
    assertTrue(postingRegisterCsv.contains("approval-idem-1") || postingRegisterCsv.contains(",,"));
    ByteArrayOutputStream balanceTextOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(balanceTextOutput))
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
    new CliReportResponseWriterFixture(utf8PrintStream(balanceCsvOutput))
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
    new CliBookReadResponseWriterFixture(utf8PrintStream(emptyTextOutput))
        .writeListTaxRegistrationsResult(listedTaxRegistrations(emptyPage), OutputMode.TEXT);
    String emptyText = emptyTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(emptyText.contains("No tax registrations matched the selected scope."), emptyText);
    assertTrue(emptyText.contains(emptyCursor.wireValue()), emptyText);

    ByteArrayOutputStream listedTextOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(listedTextOutput))
        .writeListTaxRegistrationsResult(listedTaxRegistrations(listedPage), OutputMode.TEXT);
    String listedText = listedTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(listedText.contains("Tax Registrations"), listedText);
    assertTrue(listedText.contains("vat-lv"), listedText);
    assertTrue(listedText.contains(listedCursor.wireValue()), listedText);

    ByteArrayOutputStream listedJsonOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(listedJsonOutput))
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
    new CliBookReadResponseWriterFixture(utf8PrintStream(listRejectionTextOutput))
        .writeListTaxRegistrationsResult(
            new ListTaxRegistrationsResult.Rejected(new TaxQueryRejection.BookNotInitialized()),
            OutputMode.TEXT);
    String listRejectionText = listRejectionTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(listRejectionText.contains("tax-query-book-not-initialized"), listRejectionText);

    TaxObligationReport emptyObligation = taxObligationReport(registrationWithNumber, List.of());
    ByteArrayOutputStream obligationCsvOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(obligationCsvOutput))
        .writeTaxObligationResult(
            new TaxObligationResult.Reported(emptyObligation), OutputMode.CSV);
    String obligationCsv = obligationCsvOutput.toString(StandardCharsets.UTF_8);
    assertEquals(
        "family,taxCode,taxCodeName,application,postings,taxableCurrencyCode,taxableMinorUnits,taxCurrencyCode,taxMinorUnits,grossCurrencyCode,grossMinorUnits,outputTaxCurrencyCode,outputTaxMinorUnits,recoverableInputTaxCurrencyCode,recoverableInputTaxMinorUnits,nonrecoverableInputTaxCurrencyCode,nonrecoverableInputTaxMinorUnits,netPayableCurrencyCode,netPayableMinorUnits,netReceivableCurrencyCode,netReceivableMinorUnits\n",
        obligationCsv);

    ByteArrayOutputStream obligationRejectionJsonOutput = new ByteArrayOutputStream();
    new CliReportResponseWriterFixture(utf8PrintStream(obligationRejectionJsonOutput))
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
    new CliBookReadResponseWriterFixture(utf8PrintStream(postingRegisterTextOutput))
        .writeListPostingsResult(
            listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.TEXT);
    String postingRegisterText = postingRegisterTextOutput.toString(StandardCharsets.UTF_8);
    assertFalse(postingRegisterText.contains("Approvals"));
    assertTrue(postingRegisterText.contains("1000, 2000"));

    ByteArrayOutputStream postingRegisterCsvOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriterFixture(utf8PrintStream(postingRegisterCsvOutput))
        .writeListPostingsResult(
            listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.CSV);
    String postingRegisterCsv = postingRegisterCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(postingRegisterCsv.contains("document-idem-1"));
    assertTrue(postingRegisterCsv.contains("approval-idem-1"));
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
        "hard-break-reject-noncurrent-formats",
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
          "hard-break-reject-noncurrent-formats",
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
        new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "3150"),
        new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "2100"),
        new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "1200"),
        new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "1050"),
        new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "0"));
  }
}
