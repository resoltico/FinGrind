package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Envelope-level JSON records emitted by the CLI transport layer. */
public interface CliEnvelopeJsonModels {

  record SuccessArtifact(String format, String path) {
    public SuccessArtifact {
      format = requireText(format, "format");
      path = requireText(path, "path");
    }
  }

  record SuccessEnvelope<T extends ProtocolSuccessPayload>(
      ProtocolEnvelopeStatus status, T payload, @Nullable List<SuccessArtifact> artifacts) {
    public SuccessEnvelope {
      status = requireValue(status, "status");
      payload = requireValue(payload, "payload");
      artifacts = artifacts == null ? null : java.util.List.copyOf(artifacts);
    }
  }

  record PlanEnvelope<T extends ProtocolSuccessPayload>(
      ProtocolEnvelopeStatus status, T payload, @Nullable List<SuccessArtifact> artifacts) {
    public PlanEnvelope {
      status = requireValue(status, "status");
      payload = requireValue(payload, "payload");
      artifacts = artifacts == null ? null : java.util.List.copyOf(artifacts);
    }
  }

  record FailureEnvelope(
      ProtocolEnvelopeStatus status,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      CliErrorJsonModels.@Nullable ErrorDetails details) {
    /** Creates one deterministic failure envelope for machine and text CLI output. */
    public FailureEnvelope(
        ProtocolEnvelopeStatus status,
        String code,
        String message,
        @Nullable String hint,
        @Nullable String argument,
        CliErrorJsonModels.@Nullable ErrorDetails details) {
      this.status = requireValue(status, "status");
      this.code = requireText(code, "code");
      this.message = requireText(message, "message");
      this.hint = requireOptionalText(hint, "hint");
      this.argument = requireOptionalText(argument, "argument");
      this.details = details;
    }
  }

  record RejectedEnvelope(
      ProtocolEnvelopeStatus status,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String idempotencyKey,
      CliRejectionJsonModels.@Nullable RejectionDetails details) {
    /** Creates one deterministic rejection envelope for machine and text CLI output. */
    public RejectedEnvelope(
        ProtocolEnvelopeStatus status,
        String code,
        String message,
        @Nullable String hint,
        @Nullable String idempotencyKey,
        CliRejectionJsonModels.@Nullable RejectionDetails details) {
      this.status = requireValue(status, "status");
      this.code = requireText(code, "code");
      this.message = requireText(message, "message");
      this.hint = requireOptionalText(hint, "hint");
      this.idempotencyKey = requireOptionalText(idempotencyKey, "idempotencyKey");
      this.details = details;
    }
  }
}
