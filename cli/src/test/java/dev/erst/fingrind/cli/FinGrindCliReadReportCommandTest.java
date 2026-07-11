package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** End-to-end CLI tests for read and report commands. */
class FinGrindCliReadReportCommandTest extends FinGrindCliTestSupport {
  @Test
  void run_executesTextReadAndReportCommandsAgainstDefaultSqliteWorkflow() throws IOException {
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
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));
    assertEquals(
        0,
        cli(
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
        cli(
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
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock())
            .run(
                jsonArguments(
                    "record-sale-settled",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    requestFile.toString())));
    String postingId =
        new ObjectMapper()
            .readTree(commitOutput.toString(StandardCharsets.UTF_8))
            .path("payload")
            .path("postingId")
            .stringValue();
    assertCommandOutputContains(
        new String[] {
          "inspect-book",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--output",
          "text"
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
        "exportFamily,rowId,parentRowId,relationKind,recordKind,accountCode,accountName,parentAccountCode,accountType,unitOfMeasureToken,quantityScale,financialPositionLineClassification,cashFlowAssetClassification,profitAndLossLineClassification,normalBalance,active,declaredAt,message");
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
          "text"
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
        "exportFamily,rowId,recordKind,effectiveDate,recordedAt,postingId,postingKind,postingOriginKind,reversalState,reversesPostingId,reversedByPostingId,currencyCode,debitTotal,creditTotal,accountCodes,sourceDocumentIds,sourceDocumentTypes,approvalIds,approvalDecisions,message");
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
          "text"
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
        "exportFamily,rowId,parentRowId,relationKind");
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
          "text"
        },
        "Counterpart account codes");
    assertCommandOutputContains(
        new String[] {
          "period-summary",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--period-start",
          "2026-04-01",
          "--period-end",
          "2026-04-30",
          "--output",
          "csv"
        },
        "exportFamily,rowId,parentRowId,relationKind");
    assertCommandOutputContains(
        new String[] {
          "inventory-valuation",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--as-of",
          "2026-04-30",
          "--movements",
          "--output",
          "csv"
        },
        "inventory-valuation");
  }

  @Test
  void run_executesInterimResultSweepAndPrimaryStatementCommandsAgainstDefaultSqliteWorkflow()
      throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path declareCashFile =
        writeNamedRequest("close-declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareRevenueFile =
        writeNamedRequest(
            "close-declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path bookFilePath = tempDirectory.resolve("statement-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);

    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));
    assertEquals(
        0,
        cli(
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
        cli(
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
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "record-sale-settled",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  requestFile.toString()
                }));

    ByteArrayOutputStream closeOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(closeOutput), fixedClock())
            .run(
                new String[] {
                  "interim-result-sweep",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--through",
                  "2026-04-07",
                  "--output",
                  "text"
                }));
    assertTrue(closeOutput.toString(StandardCharsets.UTF_8).contains("Interim Result Swept"));

    assertCommandOutputContains(
        new String[] {
          "financial-position",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--effective-date-as-of",
          "2026-04-07",
          "--output",
          "text"
        },
        "Financial Position");
    assertCommandOutputContains(
        new String[] {
          "income-statement",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--period-start",
          "2026-04-07",
          "--period-end",
          "2026-04-07",
          "--output",
          "csv"
        },
        "exportFamily,rowId,parentRowId,relationKind");
    assertCommandOutputContains(
        new String[] {
          "changes-in-equity",
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--period-start",
          "2026-04-07",
          "--period-end",
          "2026-04-07",
          "--output",
          "json"
        },
        "\"status\":\"ok\"");
  }

  @Test
  void run_executesFiscalYearCloseAgainstDefaultSqliteWorkflow() throws IOException {
    Path bookFilePath = tempDirectory.resolve("close-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);

    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    assertEquals(
        2,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock())
            .run(
                jsonArguments(
                    "fiscal-year-close",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--year",
                    "2026")));

    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"fiscal-year-close-future-date\""));
  }

  @Test
  void run_returnsExitCodeTwoForRejectedReportCommands() {
    RecordingReportWorkflow workflow = new RecordingReportWorkflow();
    assertEquals(
        2,
        cli(
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
        cli(
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
        cli(
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
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30"
                }));
  }

  private void assertCommandOutputContains(String[] args, String expectedFragment) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock())
            .run(args);
    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains(expectedFragment));
  }

  /** Minimal workflow stub that only serves deterministic report rejections. */
  private static final class RecordingReportWorkflow extends CliBookWorkflowAdapter {
    @Override
    public ContractDecision<OpenBookResult> openBook(
        BookAccess bookAccess, OpenBookCommand command) {
      throw new AssertionError("openBook should not be called in this test");
    }

    @Override
    public ContractDecision<RekeyBookResult> rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
      throw new AssertionError("rekeyBook should not be called in this test");
    }

    @Override
    public ContractDecision<BackupBookResult> backupBook(
        BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
      throw new AssertionError("backupBook should not be called in this test");
    }

    @Override
    public ContractDecision<RestoreBookResult> restoreBook(
        Path bookFilePath, Path bookKeyFilePath, Path backupFilePath, Path backupKeyFilePath) {
      throw new AssertionError("restoreBook should not be called in this test");
    }

    @Override
    public ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath) {
      throw new AssertionError("inspectRekeyRollback should not be called in this test");
    }

    @Override
    public ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
        BookAccess bookAccess, @org.jspecify.annotations.Nullable Path rollbackArtifactPath) {
      throw new AssertionError("deleteRekeyRollback should not be called in this test");
    }

    @Override
    public ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
        Path bookFilePath,
        @org.jspecify.annotations.Nullable Path rollbackArtifactPath,
        BookAccess.PassphraseSource expectedPassphraseSource) {
      throw new AssertionError("restoreRekeyRollback should not be called in this test");
    }

    @Override
    public ContractDecision<DeclareAccountResult> declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      throw new AssertionError("declareAccount should not be called in this test");
    }

    @Override
    public ContractDecision<InterimResultSweepResult> interimResultSweep(
        BookAccess bookAccess, InterimResultSweepCommand command) {
      throw new AssertionError("interimResultSweep should not be called in this test");
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
        BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId) {
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
    public ContractDecision<FinancialPositionResult> financialPosition(
        BookAccess bookAccess, FinancialPositionQuery query) {
      throw new AssertionError("financialPosition should not be called in this test");
    }

    @Override
    public ContractDecision<IncomeStatementResult> incomeStatement(
        BookAccess bookAccess, IncomeStatementQuery query) {
      throw new AssertionError("incomeStatement should not be called in this test");
    }

    @Override
    public ContractDecision<ChangesInEquityResult> changesInEquity(
        BookAccess bookAccess, ChangesInEquityQuery query) {
      throw new AssertionError("changesInEquity should not be called in this test");
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
}
