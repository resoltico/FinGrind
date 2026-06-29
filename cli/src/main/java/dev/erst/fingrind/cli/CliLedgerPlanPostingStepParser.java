package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredObject;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestFieldSets;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/** Parses posting-shaped ledger-plan steps and their nested payload guards. */
final class CliLedgerPlanPostingStepParser {
  private CliLedgerPlanPostingStepParser() {}

  static LedgerStep.PreflightEntry readPreflightStep(LedgerStepId stepId, ObjectNode stepNode) {
    return new LedgerStep.PreflightEntry(
        stepId, readPostingCommand(stepNode, OperationId.PREFLIGHT_ENTRY));
  }

  static LedgerStep.PostEntry readCommittedStep(
      LedgerStepId stepId, LedgerStepKind kind, ObjectNode stepNode) {
    if (!kind.commitsPosting()) {
      throw new IllegalArgumentException(
          "Ledger step kind " + kind.wireValue() + " does not own one committed posting topic.");
    }
    return new LedgerStep.PostEntry(
        stepId, readPostingCommand(stepNode, OperationId.valueOf(kind.name())));
  }

  static void rejectFlattenedPostingPayload(
      ObjectNode stepNode, LedgerStepKind kind, List<String> unexpectedFields) {
    if (stepNode.has(ProtocolLedgerPlanFields.Step.POSTING) || !kind.carriesPostingPayload()) {
      return;
    }
    List<String> flattenedFields =
        unexpectedFields.stream()
            .filter(ProtocolPostingRequestFieldSets.postEntryTopLevelFields()::contains)
            .toList();
    if (flattenedFields.isEmpty()) {
      return;
    }
    String flattenedFieldLabel =
        flattenedFields.size() == 1
            ? "Field " + flattenedFields.getFirst()
            : "Fields " + String.join(", ", flattenedFields);
    throw new IllegalArgumentException(
        flattenedFieldLabel
            + " must be nested under "
            + ProtocolLedgerPlanFields.Step.POSTING
            + " for "
            + kind.wireValue()
            + " ledger plan steps.");
  }

  private static PostEntryCommand readPostingCommand(ObjectNode stepNode, OperationId operationId) {
    return CliPostingRequestParser.readPostEntryCommand(
        requiredObject(stepNode, ProtocolLedgerPlanFields.Step.POSTING), operationId);
  }
}
