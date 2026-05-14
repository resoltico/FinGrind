package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.protocol.ProtocolFailureStatus;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessStatus;
import org.jspecify.annotations.Nullable;

/** Envelope-level JSON records emitted by the CLI transport layer. */
public interface CliEnvelopeJsonModels {

  record SuccessEnvelope<T extends ProtocolSuccessPayload>(
      ProtocolSuccessStatus status, T payload) {
    public SuccessEnvelope {
      status = requireValue(status, "status");
      payload = requireValue(payload, "payload");
    }
  }

  record FailureEnvelope(
      ProtocolFailureStatus status,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      CliErrorJsonModels.@Nullable ErrorDetails details) {
    /** Creates one deterministic failure envelope for machine and human CLI output. */
    public FailureEnvelope(
        ProtocolFailureStatus status,
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
      ProtocolRejectionStatus status,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String idempotencyKey,
      CliRejectionJsonModels.@Nullable RejectionDetails details) {
    /** Creates one deterministic rejection envelope for machine and human CLI output. */
    public RejectedEnvelope(
        ProtocolRejectionStatus status,
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
