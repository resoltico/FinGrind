package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMutationJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps top-level CLI envelopes from already-owned payloads and failures. */
final class CliEnvelopeMapper {
  private CliEnvelopeMapper() {}

  static CliEnvelopeJsonModels.Envelope<ProtocolSuccessPayload> successEnvelope(
      ProtocolSuccessPayload payload) {
    return successEnvelope(payload, (List<CliEnvelopeJsonModels.SuccessArtifact>) null);
  }

  static CliEnvelopeJsonModels.Envelope<ProtocolSuccessPayload> successEnvelope(
      ProtocolSuccessPayload payload, @Nullable Path exportedArtifactPath) {
    return successEnvelope(
        payload,
        exportedArtifactPath == null
            ? null
            : List.of(successArtifact(ProtocolArtifactOutput.pdfFormat(), exportedArtifactPath)));
  }

  static CliEnvelopeJsonModels.Envelope<ProtocolSuccessPayload> successEnvelope(
      ProtocolSuccessPayload payload,
      @Nullable List<CliEnvelopeJsonModels.SuccessArtifact> artifacts) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.OK,
        payload,
        null,
        null,
        null,
        null,
        null,
        null,
        artifacts == null || artifacts.isEmpty() ? null : List.copyOf(artifacts));
  }

  static CliEnvelopeJsonModels.Envelope<CliPlanJsonModels.LedgerPlanPayload> ledgerPlanEnvelope(
      LedgerPlanResult result, PlanResultDetail resultDetail) {
    CliPlanJsonModels.LedgerPlanPayload payload =
        CliLedgerPlanPayloadMapper.ledgerPlanPayload(result, resultDetail);
    return switch (result) {
      case LedgerPlanResult.Succeeded _ ->
          new CliEnvelopeJsonModels.Envelope<>(
              ProtocolEnvelopeStatus.OK, payload, null, null, null, null, null, null, null);
      case LedgerPlanResult.Rejected _ ->
          nonSuccessPlanEnvelope(ProtocolEnvelopeStatus.REJECTED, payload, result);
      case LedgerPlanResult.AssertionFailed _ ->
          nonSuccessPlanEnvelope(ProtocolEnvelopeStatus.ERROR, payload, result);
    };
  }

  static CliEnvelopeJsonModels.Envelope<ProtocolSuccessPayload> failureEnvelope(
      CliFailure failure) {
    CliErrorJsonModels.@Nullable ErrorDetails details = failure.details();
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.ERROR,
        null,
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        details,
        null,
        failure.path() == null ? null : CliPublicPaths.absoluteValue(failure.path()),
        failure.path() == null
            ? null
            : failure.relatedPaths().stream().map(CliPublicPaths::absoluteValue).toList());
  }

  static CliEnvelopeJsonModels.Envelope<ProtocolSuccessPayload> preflightEnvelope(
      PostEntryResult.PreflightAccepted accepted) {
    return successEnvelope(
        new CliMutationJsonModels.PreflightAcceptedPayload(
            accepted.idempotencyKey().value(),
            accepted.effectiveDate().toString(),
            CliResolvedJournalPayloadMapper.resolvedJournalPayload(accepted.resolvedJournal())));
  }

  static CliEnvelopeJsonModels.Envelope<ProtocolSuccessPayload> committedEnvelope(
      PostEntryResult.Committed committed) {
    return successEnvelope(
        new CliMutationJsonModels.CommittedPostingPayload(
            committed.postingId().value(),
            committed.idempotencyKey().value(),
            committed.effectiveDate().toString(),
            committed.recordedAt().toString(),
            committed.idempotentReplay(),
            CliResolvedJournalPayloadMapper.resolvedJournalPayload(committed.resolvedJournal()),
            committed.attestationCommit() == null
                ? null
                : new CliAttestationJsonModels.AttestationCommitPayload(
                    committed.attestationCommit().operationOrder().toString(),
                    committed.attestationCommit().operationHeadHex())));
  }

  static CliEnvelopeJsonModels.SuccessArtifact successArtifact(String format, Path path) {
    return new CliEnvelopeJsonModels.SuccessArtifact(format, CliPublicPaths.absoluteValue(path));
  }

  static List<CliEnvelopeJsonModels.SuccessArtifact> successArtifacts(
      CliEnvelopeJsonModels.SuccessArtifact... artifacts) {
    return List.of(artifacts);
  }

  static <T extends ProtocolSuccessPayload> CliEnvelopeJsonModels.Envelope<T> withFailurePaths(
      CliEnvelopeJsonModels.Envelope<T> envelope) {
    if (envelope.status() == ProtocolEnvelopeStatus.OK || envelope.path() != null) {
      return envelope;
    }
    CliEnvelopeFailurePaths paths = CliEnvelopeFailurePaths.from(envelope.details());
    if (paths == null) {
      return envelope;
    }
    return new CliEnvelopeJsonModels.Envelope<>(
        envelope.status(),
        envelope.payload(),
        envelope.code(),
        envelope.message(),
        envelope.hint(),
        envelope.argument(),
        envelope.idempotencyKey(),
        envelope.details(),
        envelope.artifacts(),
        paths.path(),
        paths.relatedPaths());
  }

  private static CliEnvelopeJsonModels.Envelope<CliPlanJsonModels.LedgerPlanPayload>
      nonSuccessPlanEnvelope(
          ProtocolEnvelopeStatus envelopeStatus,
          CliPlanJsonModels.LedgerPlanPayload payload,
          LedgerPlanResult result) {
    LedgerStepFailure failure = result.journal().requiredFailedStep().requiredFailure();
    return new CliEnvelopeJsonModels.Envelope<>(
        envelopeStatus, payload, failure.code(), failure.message(), null, null, null, null, null);
  }
}
