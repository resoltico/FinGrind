package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodCommand;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodResult;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
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
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliWorkflowRejectionTest extends FinGrindCliTestSupport {
  @Test
  void run_mapsAssertionFailedPlansToExitCodeThree() throws IOException {
    Path planFile = writeNamedRequest("assertion-plan.json", validPlanJson());
    Path bookFilePath = tempDirectory.resolve("books").resolve("assertion.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(accountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    workflow.setExecutePlanResult(assertionFailedPlanResult("plan-1"));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    int exitCode =
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(outputStream),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "execute-plan",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  planFile.toString()
                });
    assertEquals(3, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"failureCode\":\"assertion-failed\""));
  }

  @Test
  void run_mapsBookWorkflowRejectionsToExitCodeTwo() throws IOException {
    Path declareAccountFile =
        writeNamedRequest("declare.json", declareAccountJson("1000", "Cash", "DEBIT"));
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized()),
            new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
            new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
            new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new PostEntryResult.PreflightRejected(
                new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()),
            new PostEntryResult.CommitRejected(
                new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()));
    Path bookFilePath = tempDirectory.resolve("reject.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path requestFile = writeRequest(validRequestJson());
    assertEquals(
        2,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));
    assertEquals(
        2,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "rekey-book",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--replacement-book-key-file",
                  tempDirectory.resolve("replacement.key").toString()
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
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareAccountFile.toString()
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
                  "list-accounts",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString()
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
                  "preflight-entry",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  requestFile.toString()
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
                  "post-entry",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  requestFile.toString()
                }));
  }

  @Test
  void run_mapsQueryWorkflowRejectionsToExitCodeTwo() throws IOException {
    Path bookFilePath = tempDirectory.resolve("query-reject.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    CliBookWorkflow workflow =
        new CliBookWorkflow() {
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
              Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
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
          public ContractDecision<ClosePeriodResult> closePeriod(
              BookAccess bookAccess, ClosePeriodCommand command) {
            throw new AssertionError("closePeriod should not be called in this test");
          }

          @Override
          public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
            return accepted(
                initializedBookInspection(
                    1_179_079_236, 1, 1, Instant.parse("2026-04-07T10:15:30Z")));
          }

          @Override
          public ContractDecision<ListAccountsResult> listAccounts(
              BookAccess bookAccess, ListAccountsQuery query) {
            throw new AssertionError("listAccounts should not be called in this test");
          }

          @Override
          public ContractDecision<GetPostingResult> getPosting(
              BookAccess bookAccess, PostingId postingId) {
            return accepted(
                new GetPostingResult.Rejected(new BookQueryRejection.PostingNotFound(postingId)));
          }

          @Override
          public ContractDecision<ListPostingsResult> listPostings(
              BookAccess bookAccess, ListPostingsQuery query) {
            return accepted(
                new ListPostingsResult.Rejected(
                    new BookQueryRejection.UnknownAccount(new AccountCode("9999"))));
          }

          @Override
          public ContractDecision<AccountBalanceResult> accountBalance(
              BookAccess bookAccess, AccountBalanceQuery query) {
            return accepted(
                new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()));
          }

          @Override
          public ContractDecision<TrialBalanceResult> trialBalance(
              BookAccess bookAccess, TrialBalanceQuery query) {
            throw new AssertionError("trialBalance should not be called in this test");
          }

          @Override
          public ContractDecision<AccountLedgerResult> accountLedger(
              BookAccess bookAccess, AccountLedgerQuery query) {
            throw new AssertionError("accountLedger should not be called in this test");
          }

          @Override
          public ContractDecision<PeriodSummaryResult> periodSummary(
              BookAccess bookAccess, PeriodSummaryQuery query) {
            throw new AssertionError("periodSummary should not be called in this test");
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
          public ContractDecision<LedgerPlanResult> executePlan(
              BookAccess bookAccess, LedgerPlan plan) {
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
        };
    ByteArrayOutputStream getPostingOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(getPostingOutput),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "get-posting",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--posting-id",
                  "posting-missing"
                }));
    assertTrue(
        getPostingOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"posting-not-found\""));
    ByteArrayOutputStream listPostingsOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(listPostingsOutput),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "list-postings",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString()
                }));
    assertTrue(
        listPostingsOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"unknown-account\""));
    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(balanceOutput),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "account-balance",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--account-code",
                  "1000"
                }));
    assertTrue(
        balanceOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"query-book-not-initialized\""));
  }
}
