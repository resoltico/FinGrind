package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.LedgerExecutionJournal;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.protocol.ProtocolStatuses;
import java.util.List;

/** Maps ledger-plan outcomes and journal entries into CLI JSON models. */
final class CliPlanPayloadMapper {
  private CliPlanPayloadMapper() {}

  static CliResponseJsonModels.LedgerPlanPayload ledgerPlanPayload(LedgerPlanResult result) {
    return new CliResponseJsonModels.LedgerPlanPayload(
        result.planId().value(),
        result.status().wireValue(),
        ledgerExecutionJournalPayload(result.journal()));
  }

  static CliResponseJsonModels.RejectedEnvelope rejectedPlanEnvelope(
      LedgerPlanResult result, String status) {
    CliResponseJsonModels.LedgerPlanPayload payload = ledgerPlanPayload(result);
    LedgerStepFailure failure = result.journal().requiredFailedStep().requiredFailure();
    return new CliResponseJsonModels.RejectedEnvelope(
        status,
        failure.code(),
        failure.message(),
        null,
        new CliResponseJsonModels.PlanRejectionDetails(payload));
  }

  static String planRejectionStatus(LedgerPlanStatus status) {
    return switch (status) {
      case SUCCEEDED ->
          throw new IllegalArgumentException("Succeeded plans do not have a rejection status.");
      case REJECTED -> ProtocolStatuses.PLAN_REJECTED;
      case ASSERTION_FAILED -> ProtocolStatuses.PLAN_ASSERTION_FAILED;
    };
  }

  private static CliResponseJsonModels.LedgerExecutionJournalPayload ledgerExecutionJournalPayload(
      LedgerExecutionJournal journal) {
    return new CliResponseJsonModels.LedgerExecutionJournalPayload(
        journal.startedAt().toString(),
        journal.finishedAt().toString(),
        journal.steps().stream().map(CliPlanPayloadMapper::ledgerJournalEntryPayload).toList());
  }

  private static CliResponseJsonModels.LedgerJournalEntryPayload ledgerJournalEntryPayload(
      LedgerJournalEntry entry) {
    return new CliResponseJsonModels.LedgerJournalEntryPayload(
        entry.stepId().value(),
        entry.kind().wireValue(),
        entry.detailKind() == null ? null : entry.detailKind().wireValue(),
        entry.status().wireValue(),
        entry.startedAt().toString(),
        entry.finishedAt().toString(),
        factPayloads(entry.facts()),
        entry instanceof LedgerJournalEntry.Failed
            ? ledgerStepFailurePayload(entry.requiredFailure())
            : null);
  }

  private static CliResponseJsonModels.LedgerStepFailurePayload ledgerStepFailurePayload(
      LedgerStepFailure failure) {
    return new CliResponseJsonModels.LedgerStepFailurePayload(
        failure.code(), failure.message(), factPayloads(failure.facts()));
  }

  private static List<CliResponseJsonModels.LedgerFactPayload> factPayloads(
      List<LedgerFact> facts) {
    return facts.stream().map(CliPlanPayloadMapper::ledgerFactPayload).toList();
  }

  private static CliResponseJsonModels.LedgerFactPayload ledgerFactPayload(LedgerFact fact) {
    return switch (fact) {
      case LedgerFact.Text text ->
          new CliResponseJsonModels.TextLedgerFactPayload(text.name(), text.value());
      case LedgerFact.Flag flag ->
          new CliResponseJsonModels.FlagLedgerFactPayload(flag.name(), flag.value());
      case LedgerFact.Count count ->
          new CliResponseJsonModels.CountLedgerFactPayload(count.name(), count.value());
      case LedgerFact.Group group ->
          new CliResponseJsonModels.GroupLedgerFactPayload(
              group.name(), factPayloads(group.facts()));
    };
  }
}
