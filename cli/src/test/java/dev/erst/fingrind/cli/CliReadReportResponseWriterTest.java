package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
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
  void writeReadResults_supportsJsonTextAndCsvVariants() {
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
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Initialized at");
    assertWriterOutput(
        writer ->
            writer.writeListAccountsResult(
                new ListAccountsResult.Listed(
                    accountPage(List.of(cashAccount), 50, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"accountCode\":\"1000\"");
    assertWriterOutput(
        writer ->
            writer.writeListAccountsResult(
                new ListAccountsResult.Listed(
                    accountPage(List.of(cashAccount), 50, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Cash");
    assertWriterOutput(
        writer ->
            writer.writeListAccountsResult(
                new ListAccountsResult.Listed(
                    accountPage(List.of(cashAccount), 50, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "accountCode,accountName,parentAccountCode,accountType,unitOfMeasureToken,quantityScale,financialPositionLineClassification,cashFlowAssetClassification,profitAndLossLineClassification,normalBalance,active,declaredAt");
    assertWriterOutput(
        writer ->
            writer.writeGetPostingResult(
                foundPosting(postingFact), dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"sourceDocumentId\":\"document-idem-1\"");
    assertWriterOutput(
        writer ->
            writer.writeGetPostingResult(
                foundPosting(postingFact), dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Source documents");
    assertWriterOutput(
        writer ->
            writer.writeListPostingsResult(
                new ListPostingsResult.Listed(
                    postingPage(List.of(postingFact), 10, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"sourceDocumentIds\":[\"document-idem-1\"]");
    assertWriterOutput(
        writer ->
            writer.writeListPostingsResult(
                new ListPostingsResult.Listed(
                    postingPage(List.of(postingFact), 10, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Accounts");
    assertWriterOutput(
        writer ->
            writer.writeListPostingsResult(
                new ListPostingsResult.Listed(
                    postingPage(List.of(postingFact), 10, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "exportFamily,rowId,recordKind,effectiveDate,recordedAt,postingId,postingKind,postingOriginKind,reversalState,reversesPostingId,reversedByPostingId,currencyCode,debitTotal,creditTotal,accountCodes,sourceDocumentIds,sourceDocumentTypes,approvalIds,approvalDecisions,message");
  }

  @Test
  void writeReportResults_supportsJsonTextAndCsvVariants() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    CurrencyBalance eurDebitBalance =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT);
    PostingFact postingFact = reversalPostingFact();
    AccountBalanceSnapshot balanceSnapshot =
        new AccountBalanceSnapshot(
            bookIdentity(),
            cashAccount,
            Optional.empty(),
            Optional.empty(),
            allPostingKinds(),
            List.of(eurDebitBalance));
    TrialBalanceReport trialBalanceReport =
        trialBalanceReport(
            Optional.empty(),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(new TrialBalanceRow(cashAccount, eurDebitBalance)),
            List.of());
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            bookIdentity(),
            cashAccount,
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(eurDebitBalance),
            List.of(
                new AccountLedgerEntry(
                    postingFact, eurDebitBalance, money("EUR", "6.00"), BalanceSide.DEBIT)),
            List.of(eurDebitBalance));
    PeriodSummaryReport periodSummaryReport =
        new PeriodSummaryReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            allPostingKinds(),
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
        "\"family\":\"account-balance\"");
    assertWriterOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Reported(balanceSnapshot),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Account");
    assertWriterOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Reported(balanceSnapshot),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "exportFamily,rowId,parentRowId,relationKind");
    assertWriterOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Reported(trialBalanceReport),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"family\":\"trial-balance\"");
    assertWriterOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Reported(trialBalanceReport),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "As of");
    assertWriterOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Reported(trialBalanceReport),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "exportFamily,rowId,parentRowId,relationKind");
    assertWriterOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Reported(accountLedgerReport),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"family\":\"account-ledger\"");
    assertWriterOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Reported(accountLedgerReport),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Counterpart account codes");
    assertWriterOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Reported(accountLedgerReport),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "exportFamily,rowId,parentRowId,relationKind");
    assertWriterOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Reported(periodSummaryReport),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"family\":\"period-summary\"");
    assertWriterOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Reported(periodSummaryReport),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Posting count");
    assertWriterOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Reported(periodSummaryReport),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "exportFamily,rowId,parentRowId,relationKind");
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
                    foundPosting(postingFact), dev.erst.fingrind.contract.protocol.OutputMode.CSV));
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
    String comparable =
        document.stripLeading().startsWith("{") ? canonicalJsonText(document) : document;
    assertTrue(comparable.contains(expectedFragment), comparable);
  }
}
