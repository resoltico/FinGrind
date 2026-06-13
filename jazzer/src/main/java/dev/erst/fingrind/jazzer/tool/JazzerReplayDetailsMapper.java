package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.LedgerPlanFuzzAssertions;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.workflow.LedgerPlan;

/** Shared outcome-detail builders for Jazzer replay classification. */
final class JazzerReplayDetailsMapper {
  private JazzerReplayDetailsMapper() {}

  static CliRequestReplayDetails cliRequestDetails(PostEntryCommand command) {
    return new CliRequestReplayDetails(
        JazzerReplayShapeDetails.parsedPostingCommandDetails(command),
        command.requestProvenance().actorType(),
        command.sourceChannel());
  }

  static UnparsedCliRequestReplayDetails unparsedCliRequestDetails() {
    return new UnparsedCliRequestReplayDetails();
  }

  static LedgerPlanReplayDetails ledgerPlanDetails(
      LedgerPlan plan, LedgerPlanFuzzAssertions.ExecutionSnapshot executionSnapshot) {
    return new LedgerPlanReplayDetails(
        JazzerReplayShapeDetails.ledgerPlanShapeDetails(plan),
        new LedgerPlanExecutionDetails(
            executionSnapshot.executionStatus(),
            executionSnapshot.journalStepCount(),
            executionSnapshot.listQueryStepCount(),
            executionSnapshot.structuredListQueryStepCount()));
  }

  static ParsedLedgerPlanShapeReplayDetails parsedLedgerPlanShapeDetails(LedgerPlan plan) {
    return new ParsedLedgerPlanShapeReplayDetails(
        JazzerReplayShapeDetails.ledgerPlanShapeDetails(plan));
  }

  static UnparsedLedgerPlanReplayDetails unparsedLedgerPlanDetails() {
    return new UnparsedLedgerPlanReplayDetails();
  }

  static PostingWorkflowReplayDetails postingWorkflowDetails(
      PostEntryCommand command,
      PostingWorkflowLifecycleDetails lifecycle,
      PostingWorkflowOutcomeDetails outcome) {
    return new PostingWorkflowReplayDetails(
        JazzerReplayShapeDetails.parsedPostingCommandDetails(command), lifecycle, outcome);
  }

  static UnparsedPostingWorkflowReplayDetails unparsedPostingWorkflowDetails() {
    return new UnparsedPostingWorkflowReplayDetails();
  }

  static SqliteBookRoundTripReplayDetails sqliteBookRoundTripDetails(
      PostEntryCommand command,
      SqliteBookRoundTripLifecycleDetails lifecycle,
      SqliteBookRoundTripOutcomeDetails outcome) {
    return new SqliteBookRoundTripReplayDetails(
        JazzerReplayShapeDetails.parsedPostingCommandDetails(command), lifecycle, outcome);
  }

  static UnparsedSqliteBookRoundTripReplayDetails unparsedSqliteBookRoundTripDetails() {
    return new UnparsedSqliteBookRoundTripReplayDetails();
  }
}
