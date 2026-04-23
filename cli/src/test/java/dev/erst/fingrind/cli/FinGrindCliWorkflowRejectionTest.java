package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookMigrationPolicy;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
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
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
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
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FinGrindCli}. */
@NullUnmarked
class FinGrindCliWorkflowRejectionTest extends FinGrindCliTestSupport {
  @Test
  void run_mapsAssertionFailedPlansToExitCodeThree() throws IOException {
    Path planFile = writeNamedRequest("assertion-plan.json", validPlanJson());
    Path bookFilePath = tempDirectory.resolve("books").resolve("assertion.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Opened(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, Optional.empty())),
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
        new FinGrindCli(
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
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"status\":\"plan-assertion-failed\""));
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"assertion-failed\""));
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
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "open-book",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString()
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
                  "rekey-book",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--new-book-key-file",
                  tempDirectory.resolve("replacement.key").toString()
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
        new FinGrindCli(
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
        new FinGrindCli(
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
        new FinGrindCli(
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
            return accepted(
                new BookInspection.Initialized(
                    1_179_079_236,
                    1,
                    1,
                    BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
                    Instant.parse("2026-04-07T10:15:30Z")));
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
        new FinGrindCli(
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
        new FinGrindCli(
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
        new FinGrindCli(
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
