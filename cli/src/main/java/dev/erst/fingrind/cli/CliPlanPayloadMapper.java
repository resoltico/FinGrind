package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.LedgerExecutionJournal;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerJournalStep;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps ledger-plan outcomes and journal entries into CLI JSON models. */
final class CliPlanPayloadMapper {
  private CliPlanPayloadMapper() {}

  static CliPlanJsonModels.LedgerPlanPayload ledgerPlanPayload(LedgerPlanResult result) {
    return new CliPlanJsonModels.LedgerPlanPayload(
        result.planId().value(), result.status(), ledgerExecutionJournalPayload(result.journal()));
  }

  static CliEnvelopeJsonModels.RejectedEnvelope rejectedPlanEnvelope(
      LedgerPlanResult result, ProtocolRejectionStatus status) {
    CliPlanJsonModels.LedgerPlanPayload payload = ledgerPlanPayload(result);
    LedgerStepFailure failure = result.journal().requiredFailedStep().requiredFailure();
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        status,
        failure.code(),
        failure.message(),
        null,
        new CliRejectionJsonModels.PlanRejectionDetails(payload));
  }

  static ProtocolRejectionStatus planRejectionStatus(LedgerPlanStatus status) {
    return switch (status) {
      case SUCCEEDED ->
          throw new IllegalArgumentException("Succeeded plans do not have a rejection status.");
      case REJECTED -> ProtocolRejectionStatus.PLAN_REJECTED;
      case ASSERTION_FAILED -> ProtocolRejectionStatus.PLAN_ASSERTION_FAILED;
    };
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
