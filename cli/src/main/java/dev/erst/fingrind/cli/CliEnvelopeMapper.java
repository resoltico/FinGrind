package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMutationJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps top-level CLI envelopes from already-owned payloads and failures. */
final class CliEnvelopeMapper {
  private CliEnvelopeMapper() {}

  static CliEnvelopeJsonModels.SuccessEnvelope<ProtocolSuccessPayload> successEnvelope(
      ProtocolSuccessPayload payload) {
    return successEnvelope(payload, null);
  }

  static CliEnvelopeJsonModels.SuccessEnvelope<ProtocolSuccessPayload> successEnvelope(
      ProtocolSuccessPayload payload, @Nullable Path exportedArtifactPath) {
    List<CliEnvelopeJsonModels.SuccessArtifact> artifacts =
        exportedArtifactPath == null
            ? null
            : List.of(
                new CliEnvelopeJsonModels.SuccessArtifact(
                    "pdf", CliPublicPaths.redactedValue(exportedArtifactPath)));
    return new CliEnvelopeJsonModels.SuccessEnvelope<>(
        ProtocolEnvelopeStatus.OK, payload, artifacts);
  }

  static CliEnvelopeJsonModels.PlanEnvelope<CliPlanJsonModels.LedgerPlanPayload> ledgerPlanEnvelope(
      LedgerPlanResult result, PlanResultDetail resultDetail) {
    return new CliEnvelopeJsonModels.PlanEnvelope<>(
        planEnvelopeStatus(result.status()),
        CliLedgerPlanPayloadMapper.ledgerPlanPayload(result, resultDetail),
        null);
  }

  static CliEnvelopeJsonModels.FailureEnvelope failureEnvelope(CliFailure failure) {
    CliErrorJsonModels.@Nullable ErrorDetails details = failure.details();
    return new CliEnvelopeJsonModels.FailureEnvelope(
        ProtocolEnvelopeStatus.ERROR,
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        details);
  }

  static CliEnvelopeJsonModels.SuccessEnvelope<ProtocolSuccessPayload> preflightEnvelope(
      PostEntryResult.PreflightAccepted accepted) {
    return successEnvelope(
        new CliMutationJsonModels.PreflightAcceptedPayload(
            accepted.idempotencyKey().value(), accepted.effectiveDate().toString()));
  }

  static CliEnvelopeJsonModels.SuccessEnvelope<ProtocolSuccessPayload> committedEnvelope(
      PostEntryResult.Committed committed) {
    return successEnvelope(
        new CliMutationJsonModels.CommittedPostingPayload(
            committed.postingId().value(),
            committed.idempotencyKey().value(),
            committed.effectiveDate().toString(),
            committed.recordedAt().toString()));
  }

  private static ProtocolEnvelopeStatus planEnvelopeStatus(LedgerPlanStatus status) {
    return switch (status) {
      case SUCCEEDED -> ProtocolEnvelopeStatus.OK;
      case REJECTED -> ProtocolEnvelopeStatus.REJECTED;
      case ASSERTION_FAILED -> ProtocolEnvelopeStatus.ERROR;
    };
  }
}
