package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.protocol.ProtocolFailureStatus;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessStatus;
import org.jspecify.annotations.Nullable;

/** Envelope-level JSON records emitted by the CLI transport layer. */
interface CliEnvelopeJsonModels {

  record SuccessEnvelope(ProtocolSuccessStatus status, Object payload) {
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
      @Nullable String argument) {
    public FailureEnvelope {
      status = requireValue(status, "status");
      code = requireText(code, "code");
      message = requireText(message, "message");
      hint = requireOptionalText(hint, "hint");
      argument = requireOptionalText(argument, "argument");
    }
  }

  record PreflightAcceptedEnvelope(
      ProtocolSuccessStatus status, String idempotencyKey, String effectiveDate) {
    public PreflightAcceptedEnvelope {
      status = requireValue(status, "status");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
    }
  }

  record CommittedEnvelope(
      ProtocolSuccessStatus status,
      String postingId,
      String idempotencyKey,
      String effectiveDate,
      String recordedAt) {
    public CommittedEnvelope {
      status = requireValue(status, "status");
      postingId = requireText(postingId, "postingId");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
    }
  }

  record RejectedEnvelope(
      ProtocolRejectionStatus status,
      String code,
      String message,
      @Nullable String idempotencyKey,
      @Nullable Object details) {
    public RejectedEnvelope {
      status = requireValue(status, "status");
      code = requireText(code, "code");
      message = requireText(message, "message");
      idempotencyKey = requireOptionalText(idempotencyKey, "idempotencyKey");
    }
  }
}
