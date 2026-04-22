package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookMigrationPolicy;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** Coverage-focused tests for CLI formatting, rendering, and report command dispatch. */
@NullUnmarked
class CliCoverageTest {
  private static final String TEST_BOOK_KEY = "cli-coverage-test-book-key";

  @TempDir Path tempDirectory;

  @Test
  void textFormat_rendersTablesCsvAndAmounts() {
    assertTrue(
        CliTextFormat.renderKeyValueBlock(List.of(List.of("State", "initialized")))
            .contains("State : initialized"));
    assertTrue(
        CliTextFormat.renderTable(List.of("Account", "Amount"), List.of(), 1).contains("(none)"));
    assertTrue(
        CliTextFormat.renderTable(
                List.of("Account", "Amount"), List.of(List.of("1000", "10.00")), 1)
            .contains("1000"));
    assertThrows(
        IllegalArgumentException.class,
        () -> CliTextFormat.renderTable(List.of("A", "B"), List.of(List.of("only-one-cell"))));
    assertEquals(
        "name,value\n\"Cash, reserve\",\"Line 1\nLine \"\"2\"\"\"",
        CliTextFormat.renderCsv(
            List.of("name", "value"), List.of(List.of("Cash, reserve", "Line 1\nLine \"2\""))));
    assertEquals(
        "name,value\nsimple,plain",
        CliTextFormat.renderCsv(List.of("name", "value"), List.of(List.of("simple", "plain"))));
    assertEquals(
        "name,value\nquote-only,\"Said \"\"hello\"\"\"",
        CliTextFormat.renderCsv(
            List.of("name", "value"), List.of(List.of("quote-only", "Said \"hello\""))));
    assertEquals(
        "name,value\nnewline-only,\"Line 1\nLine 2\"",
        CliTextFormat.renderCsv(
            List.of("name", "value"), List.of(List.of("newline-only", "Line 1\nLine 2"))));
    assertEquals("1.20", CliTextFormat.displayAmount("EUR", new BigDecimal("1.2")));
    assertEquals("1.234", CliTextFormat.displayAmount("ZZZ", new BigDecimal("1.234")));
    assertEquals("1.00", CliTextFormat.displayAmount("XAU", BigDecimal.ONE));
    assertEquals("alpha, beta", CliTextFormat.joined(List.of("alpha", "", "  ", "beta")));
  }

  @Test
  void queryOutputRenderer_rendersInspectionAccountAndPostingViews() {
    PostingFact postingFact = reversalPostingFact();
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash, reserve", NormalBalance.DEBIT);
    String missingInspection =
        CliQueryOutputRenderer.renderBookInspectionHuman(
            Path.of("office/report.sqlite"),
            new BookInspection.Missing(1, BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
    String existingInspection =
        CliQueryOutputRenderer.renderBookInspectionHuman(
            Path.of("office/report.sqlite"),
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE,
                123,
                0,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
    String initializedInspection =
        CliQueryOutputRenderer.renderBookInspectionHuman(
            Path.of("office/report.sqlite"),
            new BookInspection.Initialized(
                123,
                1,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
                Instant.parse("2026-04-07T10:15:30Z")));
    String accountsHuman =
        CliQueryOutputRenderer.renderAccountsHuman(
            new AccountPage(List.of(cashAccount), 50, Optional.empty()));
    String accountsCsv =
        CliQueryOutputRenderer.renderAccountsCsv(
            new AccountPage(List.of(cashAccount), 50, Optional.empty()));
    String postingHuman = CliQueryOutputRenderer.renderPostingHuman(postingFact);
    PostingPageCursor nextCursor =
        new PostingPageCursor(
            LocalDate.parse("2026-04-30"),
            Instant.parse("2026-04-07T10:15:30Z"),
            new PostingId("posting-1"));
    String postingRegisterHuman =
        CliQueryOutputRenderer.renderPostingRegisterHuman(
            new PostingPage(List.of(postingFact), 10, Optional.of(nextCursor)));
    String postingRegisterCsv =
        CliQueryOutputRenderer.renderPostingRegisterCsv(
            new PostingPage(List.of(postingFact), 10, Optional.empty()));

    assertTrue(missingInspection.contains("missing"));
    assertTrue(missingInspection.contains("Migration policy"));
    assertTrue(missingInspection.contains("Supported book format version"));
    assertTrue(existingInspection.contains("SQLite applicationId"));
    assertTrue(existingInspection.contains("State"));
    assertTrue(existingInspection.contains("blank-sqlite"));
    assertTrue(initializedInspection.contains("Initialized at"));
    assertTrue(accountsHuman.contains("Cash, reserve"));
    assertTrue(accountsCsv.contains("\"Cash, reserve\""));
    assertTrue(postingHuman.contains("Correlation id"));
    assertTrue(postingHuman.contains("posting-0"));
    assertTrue(postingHuman.contains("Correction"));
    assertTrue(postingRegisterHuman.contains("Next cursor"));
    assertTrue(postingRegisterHuman.contains(nextCursor.wireValue()));
    assertTrue(postingRegisterCsv.contains("posting-1"));
  }

  @Test
  void queryOutputRenderer_rendersBalancesReportsAndMutationViews() {
    PostingFact postingFact = reversalPostingFact();
    PostingFact selfPostingFact = selfPostingFact();
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash, reserve", NormalBalance.DEBIT);
    DeclaredAccount revenueAccount = declaredAccount("2000", "Revenue", NormalBalance.CREDIT);
    CurrencyBalance eurDebitBalance = eurDebitBalance();
    AccountBalanceSnapshot balanceSnapshot = accountBalanceSnapshot(cashAccount, eurDebitBalance);
    TrialBalanceReport trialBalanceReport = trialBalanceReport(cashAccount, eurDebitBalance);
    AccountLedgerReport accountLedgerReport =
        accountLedgerReport(cashAccount, postingFact, eurDebitBalance);
    AccountLedgerReport selfLedgerReport = selfLedgerReport(cashAccount, selfPostingFact);
    PeriodSummaryReport periodSummaryReport = periodSummaryReport(revenueAccount, eurDebitBalance);

    String accountBalanceHuman = CliQueryOutputRenderer.renderAccountBalanceHuman(balanceSnapshot);
    String accountBalanceCsv = CliQueryOutputRenderer.renderAccountBalanceCsv(balanceSnapshot);
    String trialBalanceHuman = CliQueryOutputRenderer.renderTrialBalanceHuman(trialBalanceReport);
    String trialBalanceCsv = CliQueryOutputRenderer.renderTrialBalanceCsv(trialBalanceReport);
    String accountLedgerHuman =
        CliQueryOutputRenderer.renderAccountLedgerHuman(accountLedgerReport);
    String accountLedgerCsv = CliQueryOutputRenderer.renderAccountLedgerCsv(accountLedgerReport);
    String selfLedgerHuman = CliQueryOutputRenderer.renderAccountLedgerHuman(selfLedgerReport);
    String periodSummaryHuman =
        CliQueryOutputRenderer.renderPeriodSummaryHuman(periodSummaryReport);
    String periodSummaryCsv = CliQueryOutputRenderer.renderPeriodSummaryCsv(periodSummaryReport);
    String generatedKeyHuman =
        CliMutationOutputRenderer.renderGeneratedBookKeyFileHuman(
            new SqliteBookKeyFileGenerator.GeneratedKeyFile(
                Path.of("office/keys/book.key"), "base64url-no-padding", 256, "0600"));
    String openBookHuman =
        CliMutationOutputRenderer.renderOpenBookHuman(
            Path.of("office/report.sqlite"),
            new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")));
    String rekeyBookHuman =
        CliMutationOutputRenderer.renderRekeyBookHuman(
            new RekeyBookResult.Rekeyed(Path.of("office/report.sqlite")));
    String declaredAccountHuman = CliMutationOutputRenderer.renderDeclaredAccountHuman(cashAccount);
    String preflightHuman =
        CliMutationOutputRenderer.renderPreflightAcceptedHuman(
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("coverage-idem"), LocalDate.parse("2026-04-07")));
    String committedHuman =
        CliMutationOutputRenderer.renderCommittedHuman(
            new PostEntryResult.Committed(
                new PostingId("posting-committed"),
                new IdempotencyKey("coverage-idem"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));

    assertTrue(accountBalanceHuman.contains("Account Balance"));
    assertTrue(accountBalanceHuman.contains("Range"));
    assertTrue(accountBalanceCsv.contains("effectiveDateFrom,effectiveDateTo"));
    assertTrue(trialBalanceHuman.contains("Trial Balance"));
    assertTrue(trialBalanceHuman.contains("Effective date to"));
    assertTrue(trialBalanceCsv.contains("effectiveDateTo,accountCode"));
    assertTrue(accountLedgerHuman.contains("Account Ledger"));
    assertTrue(accountLedgerHuman.contains("Opening balances"));
    assertTrue(accountLedgerHuman.contains("2000"));
    assertTrue(accountLedgerCsv.contains("counterpartAccounts"));
    assertTrue(selfLedgerHuman.contains("(self)"));
    assertTrue(selfLedgerHuman.contains("(none)"));
    assertTrue(periodSummaryHuman.contains("Period Summary"));
    assertTrue(periodSummaryHuman.contains("Posting line count"));
    assertTrue(periodSummaryCsv.contains("effectiveDateFrom,effectiveDateTo,postingCount"));
    assertTrue(generatedKeyHuman.contains("Book Key File Generated"));
    assertTrue(openBookHuman.contains("Book Initialized"));
    assertTrue(rekeyBookHuman.contains("Book Rekeyed"));
    assertTrue(declaredAccountHuman.contains("Account Declared"));
    assertTrue(preflightHuman.contains("Entry Preflight Accepted"));
    assertTrue(committedHuman.contains("Entry Committed"));
  }

  @Test
  void cliArguments_rejectUnsupportedOutputModesAndMissingReportArguments() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");

    CliCommand.InspectBook defaultInspectBook =
        assertInstanceOf(
            CliCommand.InspectBook.class,
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString()
                }));
    CliCommand.InspectBook inspectBook =
        assertInstanceOf(
            CliCommand.InspectBook.class,
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "human"
                }));
    CliCommand.TrialBalance defaultTrialBalance =
        assertInstanceOf(
            CliCommand.TrialBalance.class,
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString()
                }));
    CliCommand.TrialBalance trialBalance =
        assertInstanceOf(
            CliCommand.TrialBalance.class,
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "csv"
                }));
    CliCommand.AccountLedger defaultAccountLedger =
        assertInstanceOf(
            CliCommand.AccountLedger.class,
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000"
                }));
    CliCommand.AccountLedger accountLedger =
        assertInstanceOf(
            CliCommand.AccountLedger.class,
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "human"
                }));
    CliCommand.PeriodSummary periodSummary =
        assertInstanceOf(
            CliCommand.PeriodSummary.class,
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "csv"
                }));
    CliCommand.PeriodSummary defaultPeriodSummary =
        assertInstanceOf(
            CliCommand.PeriodSummary.class,
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30"
                }));

    assertEquals(
        dev.erst.fingrind.contract.protocol.OutputMode.JSON, defaultInspectBook.outputMode());
    assertEquals(dev.erst.fingrind.contract.protocol.OutputMode.HUMAN, inspectBook.outputMode());
    assertEquals(
        dev.erst.fingrind.contract.protocol.OutputMode.JSON,
        defaultTrialBalance.output().outputMode());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-30")), trialBalance.query().effectiveDateTo());
    assertEquals(
        dev.erst.fingrind.contract.protocol.OutputMode.JSON,
        defaultAccountLedger.output().outputMode());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-01")), accountLedger.query().effectiveDateFrom());
    assertEquals(
        dev.erst.fingrind.contract.protocol.OutputMode.JSON,
        defaultPeriodSummary.output().outputMode());
    assertEquals(LocalDate.parse("2026-04-30"), periodSummary.query().effectiveDateTo());

    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "csv"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "human",
                  "--output",
                  "json"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--limit",
                  "10"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "pdf"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-to",
                  "2026-04-30",
                  "--effective-date-to",
                  "2026-05-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--account-code",
                  "2000"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-from",
                  "2026-04-02"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--effective-date-to",
                  "2026-04-30",
                  "--effective-date-to",
                  "2026-05-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--output",
                  "human",
                  "--output",
                  "json"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--limit",
                  "10"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString()
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--account-code",
                  "2000"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-from",
                  "2026-04-02",
                  "--effective-date-to",
                  "2026-04-30"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--effective-date-to",
                  "2026-05-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "human",
                  "--output",
                  "json"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--limit",
                  "10"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-to",
                  "2026-04-30"
                }));
  }

  @Test
  void responseWriter_writesHumanCsvAndRejectedReadAndReportResults() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    CurrencyBalance eurDebitBalance =
        new CurrencyBalance(
            money("EUR", "10.00"), money("EUR", "4.00"), money("EUR", "6.00"), BalanceSide.DEBIT);
    PostingFact postingFact = reversalPostingFact();
    AccountBalanceSnapshot balanceSnapshot =
        new AccountBalanceSnapshot(
            cashAccount, Optional.empty(), Optional.empty(), List.of(eurDebitBalance));
    TrialBalanceReport trialBalanceReport =
        new TrialBalanceReport(
            Optional.empty(), List.of(new TrialBalanceRow(cashAccount, eurDebitBalance)));
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
            writer.writeBookInspection(
                Path.of("office/report.sqlite"),
                new BookInspection.Initialized(
                    123,
                    1,
                    1,
                    BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
                    Instant.parse("2026-04-07T10:15:30Z")),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"ok\"");
    assertWriterOutput(
        writer ->
            writer.writeBookInspection(
                Path.of("office/report.sqlite"),
                new BookInspection.Initialized(
                    123,
                    1,
                    1,
                    BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
                    Instant.parse("2026-04-07T10:15:30Z")),
                dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
        "Initialized at");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeBookInspection(
                    Path.of("office/report.sqlite"),
                    new BookInspection.Initialized(
                        123,
                        1,
                        1,
                        BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
                        Instant.parse("2026-04-07T10:15:30Z")),
                    dev.erst.fingrind.contract.protocol.OutputMode.CSV));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeBookInspection(
                    Path.of("office/report.sqlite"),
                    new BookInspection.Initialized(
                        123,
                        1,
                        1,
                        BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
                        Instant.parse("2026-04-07T10:15:30Z")),
                    null));
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
        "accountCode,accountName,normalBalance,active,declaredAt");
    assertWriterOutput(
        writer ->
            writer.writeListAccountsResult(
                new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeListAccountsResult(
                    null, dev.erst.fingrind.contract.protocol.OutputMode.JSON));
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
            writer.writeGetPostingResult(
                new GetPostingResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
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
                .writeGetPostingResult(null, dev.erst.fingrind.contract.protocol.OutputMode.JSON));
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
        "effectiveDate,recordedAt,postingId,currencyCode,totalAmount,accountCodes,reversalTarget");
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
        "accountCode,accountName,normalBalance,effectiveDateFrom,effectiveDateTo,currencyCode,debitTotal,creditTotal,netAmount,balanceSide");
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
        "effectiveDateTo,accountCode");
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
        "accountCode,accountName,effectiveDateFrom,effectiveDateTo,postingId,effectiveDate,recordedAt,currencyCode,debitAmount,creditAmount,runningBalance,runningBalanceSide,counterpartAccounts");
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
    assertWriterOutput(
        writer ->
            writer.writeListPostingsResult(
                new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeListPostingsResult(
                    null, dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertWriterOutput(
        writer ->
            writer.writeAccountBalanceResult(
                new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeAccountBalanceResult(
                    null, dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertWriterOutput(
        writer ->
            writer.writeTrialBalanceResult(
                new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeTrialBalanceResult(
                    null, dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertWriterOutput(
        writer ->
            writer.writeAccountLedgerResult(
                new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeAccountLedgerResult(
                    null, dev.erst.fingrind.contract.protocol.OutputMode.JSON));
    assertWriterOutput(
        writer ->
            writer.writePeriodSummaryResult(
                new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized()),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON),
        "\"status\":\"rejected\"");
    assertThrows(
        NullPointerException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writePeriodSummaryResult(
                    null, dev.erst.fingrind.contract.protocol.OutputMode.JSON));
  }

  @Test
  void finGrindCli_runsReportAndHumanReadCommandsThroughDefaultSqliteWorkflow() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path declareCashFile =
        writeNamedRequest(
            "coverage-declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareRevenueFile =
        writeNamedRequest(
            "coverage-declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path bookFilePath = tempDirectory.resolve("coverage-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);

    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "open-book",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString()
                }));
    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareCashFile.toString()
                }));
    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareRevenueFile.toString()
                }));

    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock())
            .run(
                new String[] {
                  "post-entry",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  requestFile.toString()
                }));
    String postingId =
        new ObjectMapper()
            .readTree(commitOutput.toString(StandardCharsets.UTF_8))
            .path("postingId")
            .asText();

    assertCommandOutputContains(
        new String[] {
          "inspect-book",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--output",
          "human"
        },
        "State");
    assertCommandOutputContains(
        new String[] {
          "list-accounts",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--output",
          "csv"
        },
        "accountCode,accountName,normalBalance,active,declaredAt");
    assertCommandOutputContains(
        new String[] {
          "get-posting",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--posting-id",
          postingId,
          "--output",
          "human"
        },
        "Posting id");
    assertCommandOutputContains(
        new String[] {
          "list-postings",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--limit",
          "10",
          "--output",
          "csv"
        },
        "effectiveDate,recordedAt,postingId,currencyCode,totalAmount,accountCodes,reversalTarget");
    assertCommandOutputContains(
        new String[] {
          "account-balance",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--account-code",
          "1000",
          "--output",
          "human"
        },
        "Account");
    assertCommandOutputContains(
        new String[] {
          "trial-balance",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--output",
          "csv"
        },
        "effectiveDateTo,accountCode,accountName,normalBalance,active,currencyCode,debitTotal,creditTotal,netAmount,balanceSide");
    assertCommandOutputContains(
        new String[] {
          "account-ledger",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--account-code",
          "1000",
          "--output",
          "human"
        },
        "Counterpart accounts");
    assertCommandOutputContains(
        new String[] {
          "period-summary",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--effective-date-from",
          "2026-04-01",
          "--effective-date-to",
          "2026-04-30",
          "--output",
          "csv"
        },
        "effectiveDateFrom,effectiveDateTo,postingCount,postingLineCount,accountsTouched,accountCode");
  }

  @Test
  void finGrindCli_returnsExitCodeTwoForRejectedReportCommands() {
    RecordingReportWorkflow workflow = new RecordingReportWorkflow();

    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "trial-balance", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                }));
    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--account-code",
                  "1000"
                }));
    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "period-summary",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30"
                }));
  }

  private void assertCommandOutputContains(String[] args, String expectedFragment) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    int exitCode =
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock())
            .run(args);
    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains(expectedFragment));
  }

  private static void assertWriterOutput(
      java.util.function.Consumer<CliResponseWriter> writeAction, String expectedFragment) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    writeAction.accept(new CliResponseWriter(utf8PrintStream(outputStream)));
    String document = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(document.contains(expectedFragment), document);
  }

  private Path writeRequest(String payload) throws IOException {
    return writeNamedRequest("coverage-request.json", payload);
  }

  private Path writeNamedRequest(String fileName, String payload) throws IOException {
    Path requestFile = tempDirectory.resolve(fileName);
    Files.writeString(requestFile, payload, StandardCharsets.UTF_8);
    return requestFile;
  }

  private Path writeBookKey(Path bookFilePath) throws IOException {
    Path bookKeyFilePath = bookFilePath.resolveSibling(bookFilePath.getFileName() + ".key");
    if (bookKeyFilePath.getParent() != null) {
      Files.createDirectories(bookKeyFilePath.getParent());
    }
    if (Files.notExists(bookKeyFilePath)) {
      SqliteBookKeyFileGenerator.generate(bookKeyFilePath);
    }
    Files.writeString(bookKeyFilePath, TEST_BOOK_KEY, StandardCharsets.UTF_8);
    return bookKeyFilePath;
  }

  private static DeclaredAccount declaredAccount(
      String accountCode, String accountName, NormalBalance normalBalance) {
    return new DeclaredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        normalBalance,
        true,
        Instant.parse("2026-04-07T10:15:30Z"));
  }

  private static Money money(String currencyCode, String amount) {
    return new Money(new CurrencyCode(currencyCode), new BigDecimal(amount));
  }

  private static PostingFact reversalPostingFact() {
    return new PostingFact(
        new PostingId("posting-1"),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("EUR", "10.00")))),
        PostingLineage.reversal(
            new ReversalReference(new PostingId("posting-0")), new ReversalReason("Correction")),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static PostingFact selfPostingFact() {
    return new PostingFact(
        new PostingId("posting-self"),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("EUR", "5.00")),
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.CREDIT, money("EUR", "5.00")))),
        PostingLineage.direct(),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-2"),
                ActorType.HUMAN,
                new CommandId("command-2"),
                new IdempotencyKey("idem-2"),
                new CausationId("cause-2"),
                Optional.empty()),
            Instant.parse("2026-04-07T10:20:30Z"),
            SourceChannel.CLI));
  }

  private static CurrencyBalance eurDebitBalance() {
    return new CurrencyBalance(
        money("EUR", "10.00"), money("EUR", "4.00"), money("EUR", "6.00"), BalanceSide.DEBIT);
  }

  private static AccountBalanceSnapshot accountBalanceSnapshot(
      DeclaredAccount account, CurrencyBalance balance) {
    return new AccountBalanceSnapshot(
        account,
        Optional.of(LocalDate.parse("2026-04-01")),
        Optional.of(LocalDate.parse("2026-04-30")),
        List.of(balance));
  }

  private static TrialBalanceReport trialBalanceReport(
      DeclaredAccount account, CurrencyBalance balance) {
    return new TrialBalanceReport(
        Optional.of(LocalDate.parse("2026-04-30")), List.of(new TrialBalanceRow(account, balance)));
  }

  private static AccountLedgerReport accountLedgerReport(
      DeclaredAccount account, PostingFact postingFact, CurrencyBalance balance) {
    return new AccountLedgerReport(
        account,
        EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        List.of(balance),
        List.of(
            new AccountLedgerEntry(postingFact, balance, money("EUR", "6.00"), BalanceSide.DEBIT)),
        List.of(balance));
  }

  private static AccountLedgerReport selfLedgerReport(
      DeclaredAccount account, PostingFact postingFact) {
    return new AccountLedgerReport(
        account,
        EffectiveDateRange.unbounded(),
        List.of(),
        List.of(
            new AccountLedgerEntry(
                postingFact,
                new CurrencyBalance(
                    money("EUR", "5.00"),
                    money("EUR", "5.00"),
                    money("EUR", "0.00"),
                    BalanceSide.ZERO),
                money("EUR", "0.00"),
                BalanceSide.ZERO)),
        List.of());
  }

  private static PeriodSummaryReport periodSummaryReport(
      DeclaredAccount revenueAccount, CurrencyBalance balance) {
    return new PeriodSummaryReport(
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        1,
        2,
        2,
        List.of(new PeriodCurrencySummary(balance)),
        List.of(new PeriodAccountActivityRow(revenueAccount, balance)));
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);
  }

  private static String validRequestJson() {
    return """
            {
              "effectiveDate": "2026-04-07",
              "lines": [
                {
                  "accountCode": "1000",
                  "side": "DEBIT",
                  "currencyCode": "EUR",
                  "amount": "10.00"
                },
                {
                  "accountCode": "2000",
                  "side": "CREDIT",
                  "currencyCode": "EUR",
                  "amount": "10.00"
                }
              ],
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-1",
                "causationId": "cause-1"
              }
            }
            """;
  }

  private static String declareAccountJson(
      String accountCode, String accountName, String normalBalance) {
    return """
            {
              "accountCode": "%s",
              "accountName": "%s",
              "normalBalance": "%s"
            }
            """
        .formatted(accountCode, accountName, normalBalance);
  }

  private static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }

  /** Minimal workflow stub that only serves deterministic report rejections. */
  private static final class RecordingReportWorkflow implements CliBookWorkflow {
    @Override
    public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess) {
      throw new AssertionError("openBook should not be called in this test");
    }

    @Override
    public ContractDecision<RekeyBookResult> rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
      throw new AssertionError("rekeyBook should not be called in this test");
    }

    @Override
    public ContractDecision<DeclareAccountResult> declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      throw new AssertionError("declareAccount should not be called in this test");
    }

    @Override
    public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
      throw new AssertionError("inspectBook should not be called in this test");
    }

    @Override
    public ContractDecision<ListAccountsResult> listAccounts(
        BookAccess bookAccess, ListAccountsQuery query) {
      throw new AssertionError("listAccounts should not be called in this test");
    }

    @Override
    public ContractDecision<GetPostingResult> getPosting(
        BookAccess bookAccess, PostingId postingId) {
      throw new AssertionError("getPosting should not be called in this test");
    }

    @Override
    public ContractDecision<ListPostingsResult> listPostings(
        BookAccess bookAccess, ListPostingsQuery query) {
      throw new AssertionError("listPostings should not be called in this test");
    }

    @Override
    public ContractDecision<AccountBalanceResult> accountBalance(
        BookAccess bookAccess, AccountBalanceQuery query) {
      throw new AssertionError("accountBalance should not be called in this test");
    }

    @Override
    public ContractDecision<TrialBalanceResult> trialBalance(
        BookAccess bookAccess, TrialBalanceQuery query) {
      return accepted(new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()));
    }

    @Override
    public ContractDecision<AccountLedgerResult> accountLedger(
        BookAccess bookAccess, AccountLedgerQuery query) {
      return accepted(
          new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized()));
    }

    @Override
    public ContractDecision<PeriodSummaryResult> periodSummary(
        BookAccess bookAccess, PeriodSummaryQuery query) {
      return accepted(
          new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized()));
    }

    @Override
    public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
      throw new AssertionError("executePlan should not be called in this test");
    }

    @Override
    public ContractDecision<PreflightEntryResult> preflight(
        BookAccess bookAccess, PostEntryCommand command) {
      throw new AssertionError("preflight should not be called in this test");
    }

    @Override
    public ContractDecision<CommitEntryResult> commit(
        BookAccess bookAccess, PostEntryCommand command) {
      throw new AssertionError("commit should not be called in this test");
    }
  }

  private static <T> ContractDecision<T> accepted(T value) {
    return ContractDecision.accepted(value);
  }
}
