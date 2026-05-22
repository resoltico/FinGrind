package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.cli.LedgerPlanFuzzAssertions;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.jazzer.support.PostingLifecycleStatusMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/** Shared outcome-detail builders for Jazzer replay classification. */
final class JazzerReplayDetailsMapper {
  private JazzerReplayDetailsMapper() {}

  static CliRequestReplayDetails cliRequestDetails(PostEntryCommand command) {
    return new CliRequestReplayDetails(
        parsedPostingCommandDetails(command),
        command.requestProvenance().actorType(),
        command.sourceChannel());
  }

  static UnparsedCliRequestReplayDetails unparsedCliRequestDetails() {
    return new UnparsedCliRequestReplayDetails();
  }

  static LedgerPlanReplayDetails ledgerPlanDetails(
      LedgerPlan plan, LedgerPlanFuzzAssertions.ExecutionSnapshot executionSnapshot) {
    return new LedgerPlanReplayDetails(
        ledgerPlanShapeDetails(plan),
        new LedgerPlanExecutionDetails(
            executionSnapshot.executionStatus(),
            executionSnapshot.journalStepCount(),
            executionSnapshot.listQueryStepCount(),
            executionSnapshot.structuredListQueryStepCount()));
  }

  static ParsedLedgerPlanShapeReplayDetails parsedLedgerPlanShapeDetails(LedgerPlan plan) {
    return new ParsedLedgerPlanShapeReplayDetails(ledgerPlanShapeDetails(plan));
  }

  static UnparsedLedgerPlanReplayDetails unparsedLedgerPlanDetails() {
    return new UnparsedLedgerPlanReplayDetails();
  }

  static PostingWorkflowReplayDetails postingWorkflowDetails(
      PostEntryCommand command,
      PostingWorkflowLifecycleDetails lifecycle,
      PostingWorkflowOutcomeDetails outcome) {
    return new PostingWorkflowReplayDetails(
        parsedPostingCommandDetails(command), lifecycle, outcome);
  }

  static UnparsedPostingWorkflowReplayDetails unparsedPostingWorkflowDetails() {
    return new UnparsedPostingWorkflowReplayDetails();
  }

  static SqliteBookRoundTripReplayDetails sqliteBookRoundTripDetails(
      PostEntryCommand command,
      SqliteBookRoundTripLifecycleDetails lifecycle,
      SqliteBookRoundTripOutcomeDetails outcome) {
    return new SqliteBookRoundTripReplayDetails(
        parsedPostingCommandDetails(command), lifecycle, outcome);
  }

  static UnparsedSqliteBookRoundTripReplayDetails unparsedSqliteBookRoundTripDetails() {
    return new UnparsedSqliteBookRoundTripReplayDetails();
  }

  static ReplayOutcome unexpectedFailure(
      JazzerHarness harness, Throwable error, ReplayDetails details) {
    return new ReplayOutcome.UnexpectedFailure(
        harness.key(),
        error.getClass().getSimpleName(),
        normalizedMessage(error),
        stackTrace(error),
        details);
  }

  static PostingLifecycleStatus rejectionStatus(PostingRejection rejection) {
    return PostingLifecycleStatusMapper.forRejection(rejection);
  }

  static PreflightRejected requiredPreflightRejected(PreflightEntryResult result) {
    if (!(result instanceof PreflightRejected rejected)) {
      throw new IllegalStateException(
          "Expected deterministic preflight rejection during replay lifecycle setup.");
    }
    return rejected;
  }

  static CommitRejected requiredCommitRejected(CommitEntryResult result) {
    if (!(result instanceof CommitRejected rejected)) {
      throw new IllegalStateException(
          "Expected deterministic commit rejection during replay lifecycle setup.");
    }
    return rejected;
  }

  static String normalizedMessage(Throwable error) {
    Objects.requireNonNull(error, "error must not be null");
    return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
  }

  static ParsedPostingCommandDetails parsedPostingCommandDetails(PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return new ParsedPostingCommandDetails(
        CliFuzzFixtures.journalEntry(command).effectiveDate().toString(),
        command.requestProvenance().idempotencyKey().value(),
        CliFuzzFixtures.journalEntry(command).lines().size(),
        CliFuzzFixtures.reversalReference(command).isPresent());
  }

  private static int assertionStepCount(LedgerPlan plan) {
    return (int) plan.steps().stream().filter(LedgerStep.Assert.class::isInstance).count();
  }

  private static LedgerPlanShapeDetails ledgerPlanShapeDetails(LedgerPlan plan) {
    Objects.requireNonNull(plan, "plan must not be null");
    return new LedgerPlanShapeDetails(
        plan.planId().value(),
        plan.steps().size(),
        plan.steps().getFirst().kind(),
        plan.steps().getLast().kind(),
        assertionStepCount(plan),
        plan.beginsWithOpenBook());
  }

  private static String stackTrace(Throwable error) {
    StringWriter output = new StringWriter();
    error.printStackTrace(new PrintWriter(output, true));
    return output.toString();
  }
}
