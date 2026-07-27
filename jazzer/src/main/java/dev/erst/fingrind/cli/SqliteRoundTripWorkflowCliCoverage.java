package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
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
    SqliteFuzzAssertions.createOwnerOnlyArtifactDirectory(workflowRoot);
    SqliteFuzzAssertions.createOwnerOnlyArtifactDirectory(workflowRoot.resolve("keys"));
    Path bookPath = workflowRoot.resolve("books").resolve("entity.sqlite");
    Path keyPath = workflowRoot.resolve("keys").resolve("entity.book-key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(keyPath);
    BookAccess bookAccess = SqliteRoundTripWorkflowResources.keyFileBookAccess(bookPath, keyPath);
    CliBookLifecycleWorkflow lifecycleWorkflow =
        SqliteRoundTripWorkflowResources.sqliteLifecycleWorkflow();
    CliBookMutationWorkflow mutationWorkflow =
        SqliteRoundTripWorkflowResources.sqliteMutationWorkflow();
    CliBookReadWorkflow readWorkflow = SqliteRoundTripWorkflowResources.sqliteReadWorkflow();
    PostEntryCommand workflowCommand =
        SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(command, "workflow");
    Committed committed =
        initializeAndCommitWorkflowBook(
            lifecycleWorkflow, mutationWorkflow, bookAccess, workflowCommand);
    PostingId postingId = committed.postingId();
    AccountCode primaryAccount = CliFuzzSyntheticAccountFixtures.firstAccountCode(workflowCommand);
    LocalDate effectiveDate = CliFuzzFixtures.journalEntry(workflowCommand).effectiveDate();

    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.inspectBook(bookAccess),
        OutputMode.TEXT,
        (writers, inspection, mode) ->
            writers.query().writeBookInspection(bookPath, inspection, mode),
        "State");
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.inspectBook(bookAccess),
        OutputMode.JSON,
        (writers, inspection, mode) ->
            writers.query().writeBookInspection(bookPath, inspection, mode),
        "\"status\"");
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.listAccounts(bookAccess, new ListAccountsQuery(50, Optional.empty())),
        OutputMode.CSV,
        (writers, result, mode) -> writers.query().writeListAccountsResult(result, mode),
        primaryAccount.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.getPosting(bookAccess, postingId),
        OutputMode.JSON,
        (writers, result, mode) -> writers.query().writeGetPostingResult(result, mode),
        postingId.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.listPostings(
            bookAccess, new ListPostingsQuery(Optional.empty(), null, null, 50, Optional.empty())),
        OutputMode.CSV,
        (writers, result, mode) -> writers.query().writeListPostingsResult(result, mode),
        postingId.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.accountBalance(bookAccess, AccountBalanceQuery.unbounded(primaryAccount)),
        OutputMode.TEXT,
        (writers, result, mode) -> writers.query().writeAccountBalanceResult(result, mode),
        primaryAccount.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.trialBalance(
            bookAccess,
            new TrialBalanceQuery(
                Optional.of(effectiveDate),
                PostingCoverage.ALL_POSTING_KINDS,
                ComparativeSelection.none())),
        OutputMode.CSV,
        (writers, result, mode) -> writers.query().writeTrialBalanceResult(result, mode),
        primaryAccount.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.accountLedger(bookAccess, unboundedAccountLedgerQuery(primaryAccount)),
        OutputMode.TEXT,
        (writers, result, mode) -> writers.query().writeAccountLedgerResult(result, mode),
        primaryAccount.value());
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.periodSummary(
            bookAccess, new PeriodSummaryQuery(effectiveDate, effectiveDate)),
        OutputMode.CSV,
        (writers, result, mode) -> writers.query().writePeriodSummaryResult(result, mode),
        primaryAccount.value());

    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.getPosting(bookAccess, new PostingId("35b64143-46df-384f-898b-57d9ce1c50c1")),
        OutputMode.JSON,
        (writers, result, mode) -> writers.query().writeGetPostingResult(result, mode),
        "posting-not-found");
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.accountBalance(
            bookAccess, AccountBalanceQuery.unbounded(new AccountCode("9999"))),
        OutputMode.JSON,
        (writers, result, mode) -> writers.query().writeAccountBalanceResult(result, mode),
        "unknown-account");
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        readWorkflow.accountLedger(
            bookAccess, unboundedAccountLedgerQuery(new AccountCode("9999"))),
        OutputMode.CSV,
        (writers, result, mode) -> writers.query().writeAccountLedgerResult(result, mode),
        "unknown-account");

    PreflightEntryResult duplicatePreflight =
        mutationWorkflow.preflight(bookAccess, workflowCommand).requireAccepted();
    SqliteRoundTripWorkflowDecisionAssertions.requireDuplicateWorkflowPreflightAccepted(
        duplicatePreflight);
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(duplicatePreflight),
        OutputMode.TEXT,
        (writers, result, mode) ->
            writers.mutation().writePostEntryResult((PostEntryResult) result, mode),
        null);

    CommitEntryResult duplicateCommit =
        mutationWorkflow.commit(bookAccess, workflowCommand).requireAccepted();
    SqliteRoundTripWorkflowDecisionAssertions.requireCommittedReplay(duplicateCommit, committed);
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(duplicateCommit),
        OutputMode.JSON,
        (writers, result, mode) ->
            writers.mutation().writePostEntryResult((PostEntryResult) result, mode),
        "idempotentReplay");

    exerciseDerivedReversalScenarios(mutationWorkflow, bookAccess, workflowCommand, postingId);
  }

  private static Committed initializeAndCommitWorkflowBook(
      CliBookLifecycleWorkflow lifecycleWorkflow,
      CliBookMutationWorkflow mutationWorkflow,
      BookAccess bookAccess,
      PostEntryCommand command)
      throws IOException {
    Path bookPath = bookAccess.bookFilePath();
    SqliteRoundTripWorkflowRenderingAssertions.assertOpened(
        lifecycleWorkflow.openBook(
            bookAccess,
            CliFuzzWorkflowFixtures.openBookCommand(
                CliFuzzFixtures.journalEntry(command).currencyUnit())),
        bookPath,
        OutputMode.JSON,
        "\"initializedAt\"");
    for (DeclareAccountCommand declareAccountCommand :
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(command)) {
      SqliteRoundTripWorkflowRenderingAssertions.assertDeclared(
          mutationWorkflow.declareAccount(bookAccess, declareAccountCommand),
          OutputMode.TEXT,
          declareAccountCommand.accountCode().value());
    }
    PreflightAccepted preflightAccepted =
        SqliteRoundTripWorkflowDecisionAssertions.requirePreflightAccepted(
            mutationWorkflow.preflight(bookAccess, command));
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(preflightAccepted),
        OutputMode.TEXT,
        (writers, result, mode) -> writers.mutation().writePostEntryResult(result, mode),
        command.requestProvenance().idempotencyKey().value());
    Committed committed =
        SqliteRoundTripWorkflowDecisionAssertions.requireCommitted(
            mutationWorkflow.commit(bookAccess, command));
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(committed),
        OutputMode.JSON,
        (writers, result, mode) -> writers.mutation().writePostEntryResult(result, mode),
        committed.postingId().value());
    return committed;
  }

  private static AccountLedgerQuery unboundedAccountLedgerQuery(AccountCode accountCode) {
    return new AccountLedgerQuery(
        accountCode,
        EffectiveDateRange.unbounded(),
        PostingCoverage.ALL_POSTING_KINDS,
        ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
        Optional.empty());
  }

  private static void exerciseDerivedReversalScenarios(
      CliBookMutationWorkflow mutationWorkflow,
      BookAccess bookAccess,
      PostEntryCommand committedCommand,
      PostingId targetPostingId)
      throws IOException {
    PostEntryCommand nearMiss =
        SqliteRoundTripWorkflowCommandDerivation.derivedNearMissReversalCommand(
            committedCommand, targetPostingId, "reversal-near-miss");
    CommitRejected nearMissRejected =
        SqliteRoundTripWorkflowDecisionAssertions.requireCommitRejected(
            mutationWorkflow.commit(bookAccess, nearMiss));
    SqliteRoundTripWorkflowDecisionAssertions.assertNearMissReversalRejected(nearMissRejected);
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(nearMissRejected),
        OutputMode.JSON,
        (writers, result, mode) -> writers.mutation().writePostEntryResult(result, mode),
        "reversal-does-not-negate-target");

    PostEntryCommand validReversal =
        SqliteRoundTripWorkflowCommandDerivation.derivedExactReversalCommand(
            committedCommand, targetPostingId, "reversal-valid");
    Committed reversalCommitted =
        SqliteRoundTripWorkflowDecisionAssertions.requireCommitted(
            mutationWorkflow.commit(bookAccess, validReversal));
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(reversalCommitted),
        OutputMode.TEXT,
        (writers, result, mode) -> writers.mutation().writePostEntryResult(result, mode),
        null);

    PostEntryCommand duplicateReversal =
        SqliteRoundTripWorkflowCommandDerivation.derivedExactReversalCommand(
            committedCommand, targetPostingId, "reversal-duplicate");
    CommitRejected duplicateRejected =
        SqliteRoundTripWorkflowDecisionAssertions.requireCommitRejected(
            mutationWorkflow.commit(bookAccess, duplicateReversal));
    SqliteRoundTripWorkflowDecisionAssertions.assertDuplicateReversalRejected(duplicateRejected);
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
        ContractDecision.accepted(duplicateRejected),
        OutputMode.JSON,
        (writers, result, mode) -> writers.mutation().writePostEntryResult(result, mode),
        "reversal-already-exists");
  }
}
