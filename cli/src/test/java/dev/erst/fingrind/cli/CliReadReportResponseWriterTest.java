package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination;
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

/** Unit tests for read and report rendering through their focused writer fixtures. */
class CliReadReportResponseWriterTest extends CliWorkflowFixtureSupport {
  @Test
  void writeReadResults_supportsJsonTextAndCsvVariants() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    PostingFact postingFact = reversalPostingFact();
    assertBookReadOutput(
        writer ->
            writer.writeBookInspection(
                Path.of("office/report.sqlite"),
                initializedBookInspection(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"ok\"");
    assertBookReadOutput(
        writer ->
            writer.writeBookInspection(
                Path.of("office/report.sqlite"),
                initializedBookInspection(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Initialized at");
    assertBookReadOutput(
        writer ->
            writer.writeListAccountsResult(
                listedAccounts(accountPage(List.of(cashAccount), 50, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"accountCode\":\"1000\"");
    assertBookReadOutput(
        writer ->
            writer.writeListAccountsResult(
                listedAccounts(accountPage(List.of(cashAccount), 50, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Cash");
    assertBookReadOutput(
        writer ->
            writer.writeListAccountsResult(
                listedAccounts(accountPage(List.of(cashAccount), 50, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "accountCode,accountName,parentAccountCode,contraOfAccountCode,accountType,unitOfMeasureToken,quantityScale,financialPositionLineClassification,cashFlowAssetClassification,profitAndLossLineClassification,normalBalance,active,declaredAt");
    assertBookReadOutput(
        writer ->
            writer.writeGetPostingResult(
                foundPosting(postingFact), dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"sourceDocumentId\":\"document-idem-1\"");
    assertBookReadOutput(
        writer ->
            writer.writeGetPostingResult(
                foundPosting(postingFact), dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Source documents");
    assertBookReadOutput(
        writer ->
            writer.writeListPostingsResult(
                listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"sourceDocumentIds\":[\"document-idem-1\"]");
    assertBookReadOutput(
        writer ->
            writer.writeListPostingsResult(
                listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Accounts");
    assertBookReadOutput(
        writer ->
            writer.writeListPostingsResult(
                listedPostings(postingPage(List.of(postingFact), 10, Optional.empty())),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "exportFamily,rowId,recordKind,effectiveDate,recordedAt,postingId,postingKind,postingOriginKind,reversalState,reversesPostingId,reversedByPostingId,attestationOperationOrder,attestationOperationHead,currencyCode,debitTotal,creditTotal,accountCodes,sourceDocumentIds,sourceDocumentTypes,approvalIds,approvalDecisions,message");
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
            AccountLedgerPagination.firstPage(50),
            List.of(eurDebitBalance),
            List.of(
                new AccountLedgerEntry(
                    postingFact, eurDebitBalance, money("EUR", "6.00"), BalanceSide.DEBIT, null)),
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
    assertReportOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Reported(balanceSnapshot),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"family\":\"account-balance\"");
    assertReportOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Reported(balanceSnapshot),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Account");
    assertReportOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Reported(balanceSnapshot),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "family,accountCode,accountName,accountType,normalBalance,active,currencyCode,debitTotalCurrencyCode");
    assertReportOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Reported(trialBalanceReport),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"family\":\"trial-balance\"");
    assertReportOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Reported(trialBalanceReport),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "As of");
    assertReportOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Reported(trialBalanceReport),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "family,reportPeriod,accountCode,accountName,accountType,normalBalance,active,currencyCode");
    assertReportOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Reported(accountLedgerReport),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"family\":\"account-ledger\"");
    assertReportOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Reported(accountLedgerReport),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Counterpart account codes");
    assertReportOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Reported(accountLedgerReport),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "family,accountCode,postingId,effectiveDate,movementCurrencyCode,debitTotalCurrencyCode");
    assertReportOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Reported(periodSummaryReport),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"family\":\"period-summary\"");
    assertReportOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Reported(periodSummaryReport),
                dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
        "Posting count");
    assertReportOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Reported(periodSummaryReport),
                dev.erst.fingrind.contract.protocol.OutputMode.CSV),
        "family,recordScope,accountCode,accountName,accountType,normalBalance,active,currencyCode");
  }

  @Test
  void writeReadAndReportResults_rejectUnsupportedModesAndNullInputs() {
    PostingFact postingFact = reversalPostingFact();
    assertBookReadOutput(
        writer ->
            writer.writeListAccountsResult(
                new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertBookReadOutput(
        writer ->
            writer.writeGetPostingResult(
                new GetPostingResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertBookReadOutput(
        writer ->
            writer.writeListPostingsResult(
                new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertReportOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertReportOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertReportOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertReportOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliBookReadResponseWriterFixture(utf8PrintStream(new ByteArrayOutputStream()))
                .writeBookInspection(
                    Path.of("office/report.sqlite"),
                    initializedBookInspection(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")),
                    dev.erst.fingrind.contract.protocol.OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliBookReadResponseWriterFixture(utf8PrintStream(new ByteArrayOutputStream()))
                .writeGetPostingResult(
                    foundPosting(postingFact), dev.erst.fingrind.contract.protocol.OutputMode.CSV));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliBookReadResponseWriterFixture(utf8PrintStream(new ByteArrayOutputStream()))
                .writeBookInspection(
                    Path.of("office/report.sqlite"),
                    initializedBookInspection(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")),
                    nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliBookReadResponseWriterFixture(utf8PrintStream(new ByteArrayOutputStream()))
                .writeListAccountsResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliBookReadResponseWriterFixture(utf8PrintStream(new ByteArrayOutputStream()))
                .writeGetPostingResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliBookReadResponseWriterFixture(utf8PrintStream(new ByteArrayOutputStream()))
                .writeListPostingsResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliReportResponseWriterFixture(utf8PrintStream(new ByteArrayOutputStream()))
                .writeAccountBalanceResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliReportResponseWriterFixture(utf8PrintStream(new ByteArrayOutputStream()))
                .writeTrialBalanceResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliReportResponseWriterFixture(utf8PrintStream(new ByteArrayOutputStream()))
                .writeAccountLedgerResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliReportResponseWriterFixture(utf8PrintStream(new ByteArrayOutputStream()))
                .writePeriodSummaryResult(
                    nullOf(), dev.erst.fingrind.contract.protocol.OutputMode.JSON));
  }

  private static void assertBookReadOutput(
      Consumer<CliBookReadResponseWriterFixture> writeAction, String expectedFragment) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    writeAction.accept(new CliBookReadResponseWriterFixture(utf8PrintStream(outputStream)));
    assertOutputContains(outputStream, expectedFragment);
  }

  private static void assertReportOutput(
      Consumer<CliReportResponseWriterFixture> writeAction, String expectedFragment) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    writeAction.accept(new CliReportResponseWriterFixture(utf8PrintStream(outputStream)));
    assertOutputContains(outputStream, expectedFragment);
  }

  private static void assertOutputContains(
      ByteArrayOutputStream outputStream, String expectedFragment) {
    String document = outputStream.toString(StandardCharsets.UTF_8);
    String comparable =
        document.stripLeading().startsWith("{") ? canonicalJsonText(document) : document;
    assertTrue(comparable.contains(expectedFragment), comparable);
  }
}
