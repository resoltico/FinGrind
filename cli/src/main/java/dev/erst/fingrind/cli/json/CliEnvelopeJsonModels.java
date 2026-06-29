package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import java.util.HashSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Envelope-level JSON records emitted by the CLI transport layer. */
public interface CliEnvelopeJsonModels {
  String PLAN_OPERATION = ProtocolCatalog.operationName(OperationId.EXECUTE_PLAN);

  /** Shared marker for typed non-success detail payloads that can appear at the top level. */
  sealed interface EnvelopeDetails
      permits CliErrorJsonModels.ErrorDetails, CliRejectionJsonModels.RejectionDetails {}

  record SuccessArtifact(String format, String path) {
    public SuccessArtifact {
      format = requireText(format, "format");
      path = requireText(path, "path");
    }
  }

  record Envelope<T extends ProtocolSuccessPayload>(
      ProtocolEnvelopeStatus status,
      @Nullable T payload,
      @Nullable String code,
      @Nullable String message,
      @Nullable String hint,
      @Nullable String argument,
      @Nullable String idempotencyKey,
      @Nullable EnvelopeDetails details,
      @Nullable List<SuccessArtifact> artifacts) {
    public Envelope {
      status = requireValue(status, "status");
      code = requireOptionalText(code, "code");
      message = requireOptionalText(message, "message");
      hint = requireOptionalText(hint, "hint");
      argument = requireOptionalText(argument, "argument");
      idempotencyKey = requireOptionalText(idempotencyKey, "idempotencyKey");
      artifacts = artifacts == null ? null : java.util.List.copyOf(artifacts);
      if (artifacts != null) {
        if (artifacts.isEmpty()) {
          throw new IllegalArgumentException("artifacts must not be empty when present.");
        }
        if (new HashSet<>(artifacts).size() != artifacts.size()) {
          throw new IllegalArgumentException("artifacts must not contain duplicate entries.");
        }
      }
      if (status == ProtocolEnvelopeStatus.OK) {
        payload = requireValue(java.util.Objects.requireNonNull(payload, "payload"), "payload");
        requireAbsent(code, "code");
        requireAbsent(message, "message");
        requireAbsent(hint, "hint");
        requireAbsent(argument, "argument");
        requireAbsent(idempotencyKey, "idempotencyKey");
        requireAbsent(details, "details");
      } else if (status == ProtocolEnvelopeStatus.REJECTED) {
        validateNonSuccessPayload(status, payload);
        code = requireText(java.util.Objects.requireNonNull(code, "code"), "code");
        message = requireText(java.util.Objects.requireNonNull(message, "message"), "message");
        requireAbsent(argument, "argument");
        requireAbsent(artifacts, "artifacts");
        if (details != null && !(details instanceof CliRejectionJsonModels.RejectionDetails)) {
          throw new IllegalArgumentException("Rejected envelopes only admit rejection details.");
        }
      } else {
        validateNonSuccessPayload(status, payload);
        code = requireText(java.util.Objects.requireNonNull(code, "code"), "code");
        message = requireText(java.util.Objects.requireNonNull(message, "message"), "message");
        requireAbsent(idempotencyKey, "idempotencyKey");
        requireAbsent(artifacts, "artifacts");
        if (details != null && !(details instanceof CliErrorJsonModels.ErrorDetails)) {
          throw new IllegalArgumentException("Error envelopes only admit error details.");
        }
      }
    }
  }

  private static void validateNonSuccessPayload(
      ProtocolEnvelopeStatus status, @Nullable ProtocolSuccessPayload payload) {
    if (payload == null) {
      return;
    }
    if (!(payload instanceof CliPlanJsonModels.LedgerPlanPayload planPayload)) {
      throw new IllegalArgumentException(
          "payload must be absent unless this non-success envelope carries a "
              + PLAN_OPERATION
              + " result.");
    }
    if (status == ProtocolEnvelopeStatus.REJECTED
        && planPayload.status() != LedgerPlanStatus.REJECTED) {
      throw new IllegalArgumentException(
          "Rejected " + PLAN_OPERATION + " envelopes must carry a rejected plan payload.");
    }
    if (status == ProtocolEnvelopeStatus.ERROR
        && planPayload.status() != LedgerPlanStatus.ASSERTION_FAILED) {
      throw new IllegalArgumentException(
          "Error " + PLAN_OPERATION + " envelopes must carry an assertion-failed plan payload.");
    }
  }

  private static void requireAbsent(@Nullable Object value, String fieldName) {
    if (value != null) {
      throw new IllegalArgumentException(fieldName + " must be absent for this envelope status.");
    }
  }
}
