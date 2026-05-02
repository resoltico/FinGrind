package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

/** Covers executed CLI workflow and rendered-output seams for SQLite round-trip fuzzing. */
final class SqliteRoundTripWorkflowCliCoverage {
  private SqliteRoundTripWorkflowCliCoverage() {}

  static void exerciseCliWorkflowCoverage(PostEntryCommand command, Path workflowRoot)
      throws IOException {
    Path bookPath = workflowRoot.resolve("books").resolve("entity.sqlite");
    Path keyPath = workflowRoot.resolve("keys").resolve("entity.book-key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(keyPath);
    BookAccess bookAccess = SqliteRoundTripWorkflowResources.keyFileBookAccess(bookPath, keyPath);
    SqliteCliBookWorkflow workflow = SqliteRoundTripWorkflowResources.sqliteWorkflow();
    PostEntryCommand workflowCommand =
        SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(command, "workflow");
    PostingId postingId = initializeAndCommitWorkflowBook(workflow, bookAccess, workflowCommand);
    AccountCode primaryAccount = CliFuzzFixtures.firstAccountCode(workflowCommand);
    LocalDate effectiveDate = workflowCommand.journalEntry().effectiveDate();

    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.inspectBook(bookAccess),
        OutputMode.HUMAN,
        (CliResponseWriter writer, BookInspection inspection) ->
            writer.writeBookInspection(bookPath, inspection, OutputMode.HUMAN),
        "State");
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.inspectBook(bookAccess),
        OutputMode.JSON,
        (CliResponseWriter writer, BookInspection inspection) ->
            writer.writeBookInspection(bookPath, inspection, OutputMode.JSON),
        "\"status\"");
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.listAccounts(bookAccess, new ListAccountsQuery(50, Optional.empty())),
        OutputMode.CSV,
        (CliResponseWriter writer, ListAccountsResult result) ->
            writer.writeListAccountsResult(result, OutputMode.CSV),
        primaryAccount.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.getPosting(bookAccess, postingId),
        OutputMode.JSON,
        (CliResponseWriter writer, GetPostingResult result) ->
            writer.writeGetPostingResult(result, OutputMode.JSON),
        postingId.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.listPostings(
            bookAccess, new ListPostingsQuery(Optional.empty(), null, null, 50, Optional.empty())),
        OutputMode.CSV,
        (CliResponseWriter writer, ListPostingsResult result) ->
            writer.writeListPostingsResult(result, OutputMode.CSV),
        postingId.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.accountBalance(bookAccess, new AccountBalanceQuery(primaryAccount, null, null)),
        OutputMode.HUMAN,
        (CliResponseWriter writer, AccountBalanceResult result) ->
            writer.writeAccountBalanceResult(result, OutputMode.HUMAN),
        primaryAccount.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.trialBalance(bookAccess, new TrialBalanceQuery(Optional.of(effectiveDate))),
        OutputMode.CSV,
        (CliResponseWriter writer, TrialBalanceResult result) ->
            writer.writeTrialBalanceResult(result, OutputMode.CSV),
        primaryAccount.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.accountLedger(bookAccess, new AccountLedgerQuery(primaryAccount, null, null)),
        OutputMode.HUMAN,
        (CliResponseWriter writer, AccountLedgerResult result) ->
            writer.writeAccountLedgerResult(result, OutputMode.HUMAN),
        primaryAccount.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.periodSummary(bookAccess, new PeriodSummaryQuery(effectiveDate, effectiveDate)),
        OutputMode.CSV,
        (CliResponseWriter writer, PeriodSummaryResult result) ->
            writer.writePeriodSummaryResult(result, OutputMode.CSV),
        primaryAccount.value());

    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.getPosting(bookAccess, new PostingId("missing-posting")),
        OutputMode.JSON,
        (CliResponseWriter writer, GetPostingResult result) ->
            writer.writeGetPostingResult(result, OutputMode.JSON),
        "posting-not-found");
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.accountBalance(
            bookAccess, new AccountBalanceQuery(new AccountCode("9999"), null, null)),
        OutputMode.JSON,
        (CliResponseWriter writer, AccountBalanceResult result) ->
            writer.writeAccountBalanceResult(result, OutputMode.JSON),
        "unknown-account");
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        workflow.accountLedger(
            bookAccess, new AccountLedgerQuery(new AccountCode("9999"), null, null)),
        OutputMode.CSV,
        (CliResponseWriter writer, AccountLedgerResult result) ->
            writer.writeAccountLedgerResult(result, OutputMode.CSV),
        "unknown-account");

    PreflightEntryResult duplicatePreflight =
        workflow.preflight(bookAccess, workflowCommand).requireAccepted();
    SqliteRoundTripWorkflowLifecycleAssertions.assertDuplicateWorkflowPreflightRejected(
        duplicatePreflight);
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(duplicatePreflight),
        OutputMode.HUMAN,
        (writer, result) -> writer.writePostEntryResult((PostEntryResult) result, OutputMode.HUMAN),
        null);

    CommitEntryResult duplicateCommit =
        workflow.commit(bookAccess, workflowCommand).requireAccepted();
    SqliteRoundTripWorkflowLifecycleAssertions.assertDuplicateWorkflowCommitRejected(
        duplicateCommit);
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(duplicateCommit),
        OutputMode.JSON,
        (writer, result) -> writer.writePostEntryResult((PostEntryResult) result, OutputMode.JSON),
        "duplicate-idempotency-key");

    exerciseDerivedReversalScenarios(workflow, bookAccess, workflowCommand, postingId);
  }

  private static PostingId initializeAndCommitWorkflowBook(
      SqliteCliBookWorkflow workflow,
      dev.erst.fingrind.contract.BookAccess bookAccess,
      PostEntryCommand command)
      throws IOException {
    Path bookPath = bookAccess.bookFilePath();
    SqliteRoundTripWorkflowRenderingAssertions.assertOpened(
        workflow.openBook(bookAccess), bookPath, OutputMode.JSON, "\"initializedAt\"");
    for (DeclareAccountCommand declareAccountCommand :
        CliFuzzFixtures.declarePostingAccountCommands(command)) {
      SqliteRoundTripWorkflowRenderingAssertions.assertDeclared(
          workflow.declareAccount(bookAccess, declareAccountCommand),
          OutputMode.HUMAN,
          declareAccountCommand.accountCode().value());
    }
    PreflightAccepted preflightAccepted =
        SqliteRoundTripWorkflowLifecycleAssertions.requirePreflightAccepted(
            workflow.preflight(bookAccess, command));
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(preflightAccepted),
        OutputMode.HUMAN,
        (CliResponseWriter writer, PostEntryResult result) ->
            writer.writePostEntryResult(result, OutputMode.HUMAN),
        command.requestProvenance().idempotencyKey().value());
    Committed committed =
        SqliteRoundTripWorkflowLifecycleAssertions.requireCommitted(
            workflow.commit(bookAccess, command));
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(committed),
        OutputMode.JSON,
        (CliResponseWriter writer, PostEntryResult result) ->
            writer.writePostEntryResult(result, OutputMode.JSON),
        committed.postingId().value());
    return committed.postingId();
  }

  private static void exerciseDerivedReversalScenarios(
      SqliteCliBookWorkflow workflow,
      dev.erst.fingrind.contract.BookAccess bookAccess,
      PostEntryCommand committedCommand,
      PostingId targetPostingId)
      throws IOException {
    PostEntryCommand nearMiss =
        SqliteRoundTripWorkflowCommandDerivation.derivedNearMissReversalCommand(
            committedCommand, targetPostingId, "reversal-near-miss");
    CommitRejected nearMissRejected =
        SqliteRoundTripWorkflowLifecycleAssertions.requireCommitRejected(
            workflow.commit(bookAccess, nearMiss));
    SqliteRoundTripWorkflowLifecycleAssertions.assertNearMissReversalRejected(nearMissRejected);
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(nearMissRejected),
        OutputMode.JSON,
        (CliResponseWriter writer, PostEntryResult result) ->
            writer.writePostEntryResult(result, OutputMode.JSON),
        "reversal-does-not-negate-target");

    PostEntryCommand validReversal =
        SqliteRoundTripWorkflowCommandDerivation.derivedExactReversalCommand(
            committedCommand, targetPostingId, "reversal-valid");
    Committed reversalCommitted =
        SqliteRoundTripWorkflowLifecycleAssertions.requireCommitted(
            workflow.commit(bookAccess, validReversal));
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(reversalCommitted),
        OutputMode.HUMAN,
        (CliResponseWriter writer, PostEntryResult result) ->
            writer.writePostEntryResult(result, OutputMode.HUMAN),
        null);

    PostEntryCommand duplicateReversal =
        SqliteRoundTripWorkflowCommandDerivation.derivedExactReversalCommand(
            committedCommand, targetPostingId, "reversal-duplicate");
    CommitRejected duplicateRejected =
        SqliteRoundTripWorkflowLifecycleAssertions.requireCommitRejected(
            workflow.commit(bookAccess, duplicateReversal));
    SqliteRoundTripWorkflowLifecycleAssertions.assertDuplicateReversalRejected(duplicateRejected);
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(duplicateRejected),
        OutputMode.JSON,
        (CliResponseWriter writer, PostEntryResult result) ->
            writer.writePostEntryResult(result, OutputMode.JSON),
        "reversal-already-exists");
  }
}
