package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.NormalBalance;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Unit tests for read and report rendering through {@link CliResponseWriter}. */
class CliReadReportResponseWriterTest extends FinGrindCliTestSupport {
  @Test
  void writeReadResults_supportsJsonHumanAndCsvVariants() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    PostingFact postingFact = reversalPostingFact();
    assertWriterOutput(
        writer ->
            writer.writeBookInspection(
                Path.of("office/report.sqlite"),
                initializedBookInspection(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"ok\"");
    assertWriterOutput(
        writer ->
            writer.writeBookInspection(
                Path.of("office/report.sqlite"),
                initializedBookInspection(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")),
                dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
        "Initialized at");
    assertWriterOutput(
        writer ->
            writer.writeListAccountsResult(
                new ListAccountsResult.Listed(
                    new AccountPage(List.of(cashAccount), 50, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"accountCode\":\"1000\"");
    assertWriterOutput(
        writer ->
            writer.writeListAccountsResult(
                new ListAccountsResult.Listed(
                    new AccountPage(List.of(cashAccount), 50, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
        "Cash");
    assertWriterOutput(
        writer ->
            writer.writeListAccountsResult(
                new ListAccountsResult.Listed(
                    new AccountPage(List.of(cashAccount), 50, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "accountCode,accountName,accountType,accountRole,normalBalance,active,declaredAt");
    assertWriterOutput(
        writer ->
            writer.writeGetPostingResult(
                new GetPostingResult.Found(postingFact),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"postingId\":\"posting-1\"");
    assertWriterOutput(
        writer ->
            writer.writeGetPostingResult(
                new GetPostingResult.Found(postingFact),
                dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
        "Posting id");
    assertWriterOutput(
        writer ->
            writer.writeListPostingsResult(
                new ListPostingsResult.Listed(
                    new PostingPage(List.of(postingFact), 10, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"postingId\":\"posting-1\"");
    assertWriterOutput(
        writer ->
            writer.writeListPostingsResult(
                new ListPostingsResult.Listed(
                    new PostingPage(List.of(postingFact), 10, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
        "Posting id");
    assertWriterOutput(
        writer ->
            writer.writeListPostingsResult(
                new ListPostingsResult.Listed(
                    new PostingPage(List.of(postingFact), 10, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "effectiveDate,recordedAt,postingId,currencyCode,debitTotal,creditTotal,accountCodes,reversalTarget");
  }

  @Test
  void writeReportResults_supportsJsonHumanAndCsvVariants() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    CurrencyBalance eurDebitBalance =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT);
    PostingFact postingFact = reversalPostingFact();
    AccountBalanceSnapshot balanceSnapshot =
        new AccountBalanceSnapshot(
            cashAccount, Optional.empty(), Optional.empty(), List.of(eurDebitBalance));
    TrialBalanceReport trialBalanceReport =
        new TrialBalanceReport(
            bookIdentity(),
            Optional.empty(),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(new TrialBalanceRow(cashAccount, eurDebitBalance)));
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            cashAccount,
            EffectiveDateRange.unbounded(),
            List.of(eurDebitBalance),
            List.of(
                new AccountLedgerEntry(
                    postingFact, eurDebitBalance, money("EUR", "6.00"), BalanceSide.DEBIT)),
            List.of(eurDebitBalance));
    PeriodSummaryReport periodSummaryReport =
        new PeriodSummaryReport(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            1,
            2,
            1,
            List.of(new PeriodCurrencySummary(eurDebitBalance)),
            List.of(new PeriodAccountActivityRow(cashAccount, eurDebitBalance)));
    assertWriterOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Reported(balanceSnapshot),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"accountCode\":\"1000\"");
    assertWriterOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Reported(balanceSnapshot),
                dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
        "Account");
    assertWriterOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Reported(balanceSnapshot),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "accountCode,accountName,accountType,accountRole,normalBalance,effectiveDateFrom,effectiveDateTo,currencyCode,debitTotal,creditTotal,netAmount,balanceSide");
    assertWriterOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Reported(trialBalanceReport),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"accountCode\":\"1000\"");
    assertWriterOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Reported(trialBalanceReport),
                dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
        "Effective date to");
    assertWriterOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Reported(trialBalanceReport),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "entityName,functionalCurrency,fiscalYearStart,effectiveDateTo");
    assertWriterOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Reported(accountLedgerReport),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"accountCode\":\"1000\"");
    assertWriterOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Reported(accountLedgerReport),
                dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
        "Counterpart accounts");
    assertWriterOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Reported(accountLedgerReport),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "accountCode,accountName,accountType,accountRole,effectiveDateFrom,effectiveDateTo,postingId,effectiveDate,recordedAt,currencyCode,debitAmount,creditAmount,runningBalance,runningBalanceSide,counterpartAccounts");
    assertWriterOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Reported(periodSummaryReport),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"postingCount\":1");
    assertWriterOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Reported(periodSummaryReport),
                dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
        "Posting count");
    assertWriterOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Reported(periodSummaryReport),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "effectiveDateFrom,effectiveDateTo,postingCount");
  }

  @Test
  void writeReadAndReportResults_rejectUnsupportedModesAndNullInputs() {
    PostingFact postingFact = reversalPostingFact();
    assertWriterOutput(
        writer ->
            writer.writeListAccountsResult(
                new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertWriterOutput(
        writer ->
            writer.writeGetPostingResult(
                new GetPostingResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertWriterOutput(
        writer ->
            writer.writeListPostingsResult(
                new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertWriterOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertWriterOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertWriterOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertWriterOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeBookInspection(
                    Path.of("office/report.sqlite"),
                    initializedBookInspection(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")),
                    dev.erst.fingrind.contract.protocol.OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeGetPostingResult(
                    new GetPostingResult.Found(postingFact),
                    dev.erst.fingrind.contract.protocol.OutputMode.CSV));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeBookInspection(
                    Path.of("office/report.sqlite"),
                    initializedBookInspection(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")),
                    nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeListAccountsResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeGetPostingResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeListPostingsResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeAccountBalanceResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeTrialBalanceResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeAccountLedgerResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writePeriodSummaryResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
  }

  private static void assertWriterOutput(
      Consumer<CliResponseWriter> writeAction, String expectedFragment) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    writeAction.accept(new CliResponseWriter(utf8PrintStream(outputStream)));
    String document = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(document.contains(expectedFragment), document);
  }
}
