package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
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
  void run_executesHumanReadAndReportCommandsAgainstDefaultSqliteWorkflow() throws IOException {
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
            .stringValue();
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
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
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
