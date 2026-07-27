package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import dev.erst.fingrind.cli.json.CliPlanResultJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import org.jspecify.annotations.Nullable;

/** Maps ledger-plan outcomes into top-level CLI JSON payloads. */
final class CliLedgerPlanPayloadMapper {
  private CliLedgerPlanPayloadMapper() {}

  static CliPlanResultJsonModels.LedgerPlanPayload ledgerPlanPayload(
      LedgerPlanResult result, PlanResultDetail resultDetail) {
    CliPlanResultJsonModels.LedgerPlanSummaryPayload summaryPayload =
        ledgerPlanSummaryPayload(result);
    return new CliPlanResultJsonModels.LedgerPlanPayload(
        result.planId().value(),
        result.status(),
        resultDetail,
        summaryPayload,
        attestationDisposition(result),
        attestationCommitPayload(result),
        resultDetail == PlanResultDetail.FULL
            ? ledgerExecutionJournalPayload(result.journal())
            : null);
  }

  private static @Nullable LedgerPlanAttestationDisposition attestationDisposition(
      LedgerPlanResult result) {
    return result instanceof LedgerPlanResult.Succeeded succeeded
        ? succeeded.attestationDisposition()
        : null;
  }

  private static @Nullable AttestationCommitPayload attestationCommitPayload(
      LedgerPlanResult result) {
    if (!(result instanceof LedgerPlanResult.Succeeded succeeded)) {
      return null;
    }
    @Nullable AttestationCommit attestationCommit = succeeded.attestationCommit();
    return CliAttestationCommitPresentation.payload(attestationCommit);
  }

  private static CliPlanResultJsonModels.LedgerPlanSummaryPayload ledgerPlanSummaryPayload(
      LedgerPlanResult result) {
    LedgerExecutionJournal journal = result.journal();
    LedgerJournalEntry terminalStep = journal.terminalStep();
    @Nullable LedgerStepFailure failure =
        terminalStep instanceof LedgerJournalEntry.Failed failed ? failed.requiredFailure() : null;
    return new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
        journal.startedAt().toString(),
        journal.finishedAt().toString(),
        journal.steps().size(),
        (int)
            journal.steps().stream()
                .filter(step -> step.status() == LedgerStepStatus.SUCCEEDED)
                .count(),
        failure == null ? 0 : 1,
        failure == null ? null : terminalStep.stepId().value());
  }

  private static CliPlanResultJsonModels.LedgerExecutionJournalPayload
      ledgerExecutionJournalPayload(LedgerExecutionJournal journal) {
    return new CliPlanResultJsonModels.LedgerExecutionJournalPayload(
        journal.startedAt().toString(),
        journal.finishedAt().toString(),
        journal.steps().stream()
            .map(CliLedgerPlanPayloadMapper::ledgerJournalEntryPayload)
            .toList());
  }

  private static CliPlanResultJsonModels.LedgerJournalEntryPayload ledgerJournalEntryPayload(
      LedgerJournalEntry entry) {
    CliPlanResultJsonModels.LedgerStepFailurePayload failurePayload =
        switch (entry) {
          case LedgerJournalEntry.Succeeded _ -> null;
          case LedgerJournalEntry.Failed failed ->
              CliLedgerStepDataPayloadMapper.ledgerStepFailurePayload(failed.requiredFailure());
        };
    return new CliPlanResultJsonModels.LedgerJournalEntryPayload(
        entry.stepId().value(),
        entry.kind(),
        entry.journalStep().detailKind(),
        entry.journalStep().boundaryCheckpoint(),
        entry.status(),
        entry.startedAt().toString(),
        entry.finishedAt().toString(),
        CliLedgerStepDataPayloadMapper.ledgerStepDataPayload(entry),
        failurePayload);
  }
}
