package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookMigrationPolicy;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.BalanceSide;
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
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for {@link CliResponseWriter}. */
@NullUnmarked
class CliQueryResponseWriterTest extends CliResponseWriterTestSupport {

  @Test
  void writeQueryRejection_keepsJsonEnvelopeOutsideHumanMode() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
        OutputMode.JSON);

    JsonNode json = readJson(outputStream);
    assertEquals("rejected", json.path("status").asText());
    assertEquals("query-book-not-initialized", json.path("code").asText());
  }

  @Test
  void writeQueryRejection_supportsHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
        OutputMode.HUMAN);

    String text = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("Rejected"));
    assertTrue(text.contains("query-book-not-initialized"));
  }

  @Test
  void queryRejectionWriter_coversJsonAndHumanBranches() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));
    CliEnvelopeJsonModels.RejectedEnvelope envelope =
        new CliEnvelopeJsonModels.RejectedEnvelope(
            "rejected", "query-book-not-initialized", "The book is not initialized.", null, null);

    outputChannel.writeQueryRejection(OutputMode.HUMAN, envelope);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Rejected"));

    outputStream.reset();
    outputChannel.writeQueryRejection(OutputMode.JSON, envelope);
    JsonNode json = readJson(outputStream);
    assertEquals("rejected", json.path("status").asText());
    assertEquals("query-book-not-initialized", json.path("code").asText());

    outputStream.reset();
    outputChannel.writeQueryRejection(OutputMode.CSV, envelope);
    json = readJson(outputStream);
    assertEquals("rejected", json.path("status").asText());
    assertEquals("query-book-not-initialized", json.path("code").asText());
  }

  @Test
  void writeQueryResults_writeSuccessAndRejectionEnvelopes() {
    PostingFact postingFact = postingFact();
    AccountBalanceSnapshot balanceSnapshot =
        new AccountBalanceSnapshot(
            new DeclaredAccount(
                new AccountCode("1000"),
                new AccountName("Cash"),
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z")),
            java.util.Optional.of(LocalDate.parse("2026-04-01")),
            java.util.Optional.of(LocalDate.parse("2026-04-30")),
            List.of(
                new CurrencyBalance(
                    money("EUR", "10.00"),
                    money("EUR", "4.00"),
                    money("EUR", "6.00"),
                    BalanceSide.DEBIT)));

    ByteArrayOutputStream inspectionOutput = new ByteArrayOutputStream();
    CliResponseWriter inspectionWriter = new CliResponseWriter(utf8PrintStream(inspectionOutput));
    inspectionWriter.writeBookInspection(
        Path.of("book.sqlite"),
        new BookInspection.Initialized(
            1_179_079_236,
            1,
            1,
            BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
            Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream missingInspectionOutput = new ByteArrayOutputStream();
    CliResponseWriter missingInspectionWriter =
        new CliResponseWriter(utf8PrintStream(missingInspectionOutput));
    missingInspectionWriter.writeBookInspection(
        Path.of("missing.sqlite"),
        new BookInspection.Missing(1, BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
    ByteArrayOutputStream getPostingOutput = new ByteArrayOutputStream();
    CliResponseWriter getPostingWriter = new CliResponseWriter(utf8PrintStream(getPostingOutput));
    getPostingWriter.writeGetPostingResult(new GetPostingResult.Found(postingFact));
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
            new PostingPage(List.of(postingFact), 10, java.util.Optional.empty())));
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

    assertTrue(inspectionOutput.toString(StandardCharsets.UTF_8).contains("\"bookFile\""));
    assertTrue(
        inspectionOutput.toString(StandardCharsets.UTF_8).contains("\"state\":\"initialized\""));
    assertFalse(
        missingInspectionOutput.toString(StandardCharsets.UTF_8).contains("\"initializedAt\""));
    assertTrue(
        getPostingOutput.toString(StandardCharsets.UTF_8).contains("\"reason\":\"full reversal\""));
    assertTrue(
        getPostingOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"priorPostingId\":\"posting-0\""));
    assertTrue(
        getPostingRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"posting-not-found\""));
    assertTrue(
        getPostingRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"postingId\":\"posting-9\""));
    assertTrue(listPostingsOutput.toString(StandardCharsets.UTF_8).contains("\"postings\":["));
    assertTrue(
        listPostingsRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"accountCode\":\"9999\""));
    assertTrue(
        balanceOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"effectiveDateFrom\":\"2026-04-01\""));
    assertTrue(
        balanceOutput.toString(StandardCharsets.UTF_8).contains("\"balanceSide\":\"DEBIT\""));
    assertTrue(
        balanceRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"query-book-not-initialized\""));
  }

  @Test
  void writeQueryResults_supportHumanAndCsvOutputModes() {
    PostingFact postingFact = postingFact();
    AccountBalanceSnapshot balanceSnapshot =
        new AccountBalanceSnapshot(
            declaredCashAccount(),
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            List.of(currencyBalance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT)));

    ByteArrayOutputStream postingRegisterHumanOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterHumanOutput))
        .writeListPostingsResult(
            new ListPostingsResult.Listed(
                new PostingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.HUMAN);
    String postingRegisterHuman = postingRegisterHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(postingRegisterHuman.contains("Posting id"));
    assertTrue(postingRegisterHuman.contains("10.00"));
    assertTrue(postingRegisterHuman.contains("posting-1"));

    ByteArrayOutputStream postingRegisterCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterCsvOutput))
        .writeListPostingsResult(
            new ListPostingsResult.Listed(
                new PostingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.CSV);
    String postingRegisterCsv = postingRegisterCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        postingRegisterCsv.startsWith(
            "effectiveDate,recordedAt,postingId,currencyCode,totalAmount,accountCodes,reversalTarget"));
    assertTrue(postingRegisterCsv.contains("2026-04-07,2026-04-07T10:15:30Z,posting-1,EUR,10.00"));

    ByteArrayOutputStream balanceHumanOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(balanceHumanOutput))
        .writeAccountBalanceResult(
            new AccountBalanceResult.Reported(balanceSnapshot), OutputMode.HUMAN);
    String balanceHuman = balanceHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(balanceHuman.contains("Account        : 1000"));
    assertTrue(balanceHuman.contains("Debit total"));
    assertTrue(balanceHuman.contains("10.00"));
    assertTrue(balanceHuman.contains("6.00"));

    ByteArrayOutputStream balanceCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(balanceCsvOutput))
        .writeAccountBalanceResult(
            new AccountBalanceResult.Reported(balanceSnapshot), OutputMode.CSV);
    String balanceCsv = balanceCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        balanceCsv.startsWith(
            "accountCode,accountName,normalBalance,effectiveDateFrom,effectiveDateTo,currencyCode,debitTotal,creditTotal,netAmount,balanceSide"));
    assertTrue(
        balanceCsv.contains("1000,Cash,DEBIT,2026-04-01,2026-04-30,EUR,10.00,4.00,6.00,DEBIT"));
  }

  @Test
  void writeReportResults_supportJsonHumanAndCsvOutputModes() throws IOException {
    TrialBalanceReport trialBalanceReport =
        new TrialBalanceReport(
            Optional.of(LocalDate.parse("2026-04-30")),
            List.of(
                new TrialBalanceRow(
                    declaredCashAccount(),
                    currencyBalance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT))));
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            declaredCashAccount(),
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            List.of(currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
            List.of(
                new AccountLedgerEntry(
                    postingFact(),
                    currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                    money("EUR", "10.00"),
                    BalanceSide.DEBIT)),
            List.of(currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)));
    PeriodSummaryReport periodSummaryReport =
        new PeriodSummaryReport(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
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

    ByteArrayOutputStream trialBalanceJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(trialBalanceJsonOutput))
        .writeTrialBalanceResult(
            new TrialBalanceResult.Reported(trialBalanceReport), OutputMode.JSON);
    JsonNode trialBalanceJson = readJson(trialBalanceJsonOutput);
    assertEquals("ok", trialBalanceJson.path("status").asText());
    assertEquals("2026-04-30", trialBalanceJson.path("payload").path("effectiveDateTo").asText());
    assertEquals(
        "1000", trialBalanceJson.path("payload").path("rows").get(0).path("accountCode").asText());
    assertEquals(
        "10", trialBalanceJson.path("payload").path("rows").get(0).path("debitTotal").asText());
    assertFalse(trialBalanceJson.toString().contains("\"value\""));

    ByteArrayOutputStream trialBalanceHumanOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(trialBalanceHumanOutput))
        .writeTrialBalanceResult(
            new TrialBalanceResult.Reported(trialBalanceReport), OutputMode.HUMAN);
    String trialBalanceHuman = trialBalanceHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(trialBalanceHuman.contains("Effective date to : 2026-04-30"));
    assertTrue(trialBalanceHuman.contains("Account"));
    assertTrue(trialBalanceHuman.contains("6.00"));

    ByteArrayOutputStream accountLedgerHumanOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(accountLedgerHumanOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.AccountLedgerResult.Reported(accountLedgerReport),
            OutputMode.HUMAN);
    String accountLedgerHuman = accountLedgerHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(accountLedgerHuman.contains("Opening balances : EUR 10.00 DEBIT"));
    assertTrue(accountLedgerHuman.contains("Running balance"));
    assertTrue(accountLedgerHuman.contains("posting-1"));

    ByteArrayOutputStream accountLedgerJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(accountLedgerJsonOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.AccountLedgerResult.Reported(accountLedgerReport),
            OutputMode.JSON);
    JsonNode accountLedgerJson = readJson(accountLedgerJsonOutput);
    assertEquals("1000", accountLedgerJson.path("payload").path("accountCode").asText());
    assertEquals(
        "2026-04-01", accountLedgerJson.path("payload").path("effectiveDateFrom").asText());
    assertEquals(
        "posting-1",
        accountLedgerJson.path("payload").path("entries").get(0).path("postingId").asText());
    assertEquals(
        "2000",
        accountLedgerJson
            .path("payload")
            .path("entries")
            .get(0)
            .path("counterpartAccounts")
            .get(0)
            .asText());
    assertFalse(accountLedgerJson.toString().contains("\"postingFact\""));

    ByteArrayOutputStream accountLedgerCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(accountLedgerCsvOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.AccountLedgerResult.Reported(accountLedgerReport),
            OutputMode.CSV);
    String accountLedgerCsv = accountLedgerCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        accountLedgerCsv.startsWith(
            "accountCode,accountName,effectiveDateFrom,effectiveDateTo,postingId,effectiveDate,recordedAt,currencyCode,debitAmount,creditAmount,runningBalance,runningBalanceSide,counterpartAccounts"));
    assertTrue(
        accountLedgerCsv.contains(
            "1000,Cash,2026-04-01,2026-04-30,posting-1,2026-04-07,2026-04-07T10:15:30Z,EUR,10.00,0.00,10.00,DEBIT,2000"));

    ByteArrayOutputStream periodSummaryHumanOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(periodSummaryHumanOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.PeriodSummaryResult.Reported(periodSummaryReport),
            OutputMode.HUMAN);
    String periodSummaryHuman = periodSummaryHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(periodSummaryHuman.contains("Posting count"));
    assertTrue(periodSummaryHuman.contains("Posting line count"));
    assertTrue(periodSummaryHuman.contains("10.00"));

    ByteArrayOutputStream periodSummaryJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(periodSummaryJsonOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.PeriodSummaryResult.Reported(periodSummaryReport),
            OutputMode.JSON);
    JsonNode periodSummaryJson = readJson(periodSummaryJsonOutput);
    assertEquals(
        "2026-04-01", periodSummaryJson.path("payload").path("effectiveDateFrom").asText());
    assertEquals(1, periodSummaryJson.path("payload").path("postingCount").asInt());
    assertEquals(
        "1000",
        periodSummaryJson
            .path("payload")
            .path("accountActivity")
            .get(0)
            .path("accountCode")
            .asText());
    assertFalse(periodSummaryJson.toString().contains("\"account\":{\"accountCode\""));

    ByteArrayOutputStream periodSummaryCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(periodSummaryCsvOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.PeriodSummaryResult.Reported(periodSummaryReport),
            OutputMode.CSV);
    String periodSummaryCsv = periodSummaryCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        periodSummaryCsv.startsWith(
            "effectiveDateFrom,effectiveDateTo,postingCount,postingLineCount,accountsTouched,accountCode,accountName,normalBalance,currencyCode,debitTotal,creditTotal,netAmount,balanceSide"));
    assertTrue(
        periodSummaryCsv.contains(
            "2026-04-01,2026-04-30,1,2,2,1000,Cash,DEBIT,EUR,10.00,4.00,6.00,DEBIT"));
  }

  @Test
  void writeBookInspection_writesEveryExistingBookVariant() throws IOException {
    List<BookInspection> inspections =
        List.of(
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE,
                1_179_079_236,
                0,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
            new BookInspection.Existing(
                BookInspection.Status.FOREIGN_SQLITE,
                1_179_079_236,
                0,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
            new BookInspection.Existing(
                BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
                1_179_079_236,
                2,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
            new BookInspection.Existing(
                BookInspection.Status.INCOMPLETE_FINGRIND,
                1_179_079_236,
                1,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
    List<String> states =
        List.of(
            "blank-sqlite", "foreign-sqlite", "unsupported-format-version", "incomplete-fingrind");

    for (int index = 0; index < inspections.size(); index++) {
      JsonNode payload = writeInspection(inspections.get(index));

      assertEquals(states.get(index), payload.path("state").asString());
      assertEquals(1_179_079_236, payload.path("applicationId").asInt());
      assertEquals(1, payload.path("supportedBookFormatVersion").asInt());
      assertEquals("sequential-in-place", payload.path("migrationPolicy").asString());
      assertFalse(payload.has("initializedAt"));
    }
  }
}
