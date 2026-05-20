package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps ledger-plan outcomes and journal entries into CLI JSON models. */
final class CliPlanPayloadMapper {
  private CliPlanPayloadMapper() {}

  static CliPlanJsonModels.LedgerPlanPayload ledgerPlanPayload(
      LedgerPlanResult result, PlanResultDetail resultDetail) {
    CliPlanJsonModels.LedgerPlanSummaryPayload summaryPayload = ledgerPlanSummaryPayload(result);
    return new CliPlanJsonModels.LedgerPlanPayload(
        result.planId().value(),
        result.status(),
        resultDetail,
        summaryPayload,
        resultDetail == PlanResultDetail.FULL
            ? ledgerExecutionJournalPayload(result.journal())
            : null);
  }

  private static CliPlanJsonModels.LedgerPlanSummaryPayload ledgerPlanSummaryPayload(
      LedgerPlanResult result) {
    LedgerExecutionJournal journal = result.journal();
    LedgerJournalEntry terminalStep = journal.terminalStep();
    @Nullable LedgerStepFailure failure =
        terminalStep instanceof LedgerJournalEntry.Failed failed ? failed.requiredFailure() : null;
    return new CliPlanJsonModels.LedgerPlanSummaryPayload(
        journal.startedAt().toString(),
        journal.finishedAt().toString(),
        journal.steps().size(),
        (int)
            journal.steps().stream()
                .filter(step -> step.status() == LedgerStepStatus.SUCCEEDED)
                .count(),
        failure == null ? 0 : 1,
        failure == null ? null : terminalStep.stepId().value(),
        failure == null ? null : failure.code(),
        failure == null ? null : failure.message());
  }

  private static CliPlanJsonModels.LedgerExecutionJournalPayload ledgerExecutionJournalPayload(
      LedgerExecutionJournal journal) {
    return new CliPlanJsonModels.LedgerExecutionJournalPayload(
        journal.startedAt().toString(),
        journal.finishedAt().toString(),
        journal.steps().stream().map(CliPlanPayloadMapper::ledgerJournalEntryPayload).toList());
  }

  private static CliPlanJsonModels.LedgerJournalEntryPayload ledgerJournalEntryPayload(
      LedgerJournalEntry entry) {
    CliPlanJsonModels.LedgerStepFailurePayload failurePayload =
        switch (entry) {
          case LedgerJournalEntry.Succeeded _ -> null;
          case LedgerJournalEntry.Failed failed ->
              ledgerStepFailurePayload(failed.requiredFailure());
        };
    return new CliPlanJsonModels.LedgerJournalEntryPayload(
        entry.stepId().value(),
        entry.kind(),
        detailKind(entry.journalStep()),
        boundaryPhase(entry.journalStep()),
        entry.status(),
        entry.startedAt().toString(),
        entry.finishedAt().toString(),
        factPayloads(entry.facts()),
        failurePayload);
  }

  private static @Nullable LedgerAssertionKind detailKind(LedgerJournalStep journalStep) {
    return journalStep.detailKind();
  }

  private static @Nullable LedgerBoundaryPhase boundaryPhase(LedgerJournalStep journalStep) {
    return journalStep.boundaryPhase();
  }

  private static CliPlanJsonModels.LedgerStepFailurePayload ledgerStepFailurePayload(
      LedgerStepFailure failure) {
    return new CliPlanJsonModels.LedgerStepFailurePayload(
        failure.code(), failure.message(), factPayloads(failure.facts()));
  }

  private static List<CliPlanJsonModels.LedgerFactPayload> factPayloads(List<LedgerFact> facts) {
    return facts.stream().map(CliPlanPayloadMapper::ledgerFactPayload).toList();
  }

  private static CliPlanJsonModels.LedgerFactPayload ledgerFactPayload(LedgerFact fact) {
    return switch (fact) {
      case LedgerFact.Text text ->
          new CliPlanJsonModels.TextLedgerFactPayload("text", text.name(), text.value());
      case LedgerFact.Flag flag ->
          new CliPlanJsonModels.FlagLedgerFactPayload("flag", flag.name(), flag.value());
      case LedgerFact.Count count ->
          new CliPlanJsonModels.CountLedgerFactPayload("count", count.name(), count.value());
      case LedgerFact.Money money ->
          new CliPlanJsonModels.MoneyLedgerFactPayload("money", money.name(), money.value());
      case LedgerFact.Group group ->
          new CliPlanJsonModels.GroupLedgerFactPayload(
              "group", group.name(), factPayloads(group.facts()));
    };
  }
}
