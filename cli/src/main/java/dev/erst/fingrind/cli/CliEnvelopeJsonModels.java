package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requireValue;

import org.jspecify.annotations.Nullable;

/** Envelope-level JSON records emitted by the CLI transport layer. */
interface CliEnvelopeJsonModels {

  record SuccessEnvelope(String status, Object payload) {
    public SuccessEnvelope {
      status = requireText(status, "status");
      payload = requireValue(payload, "payload");
    }
  }

  record FailureEnvelope(
      String status,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument) {
    public FailureEnvelope {
      status = requireText(status, "status");
      code = requireText(code, "code");
      message = requireText(message, "message");
      hint = requireOptionalText(hint, "hint");
      argument = requireOptionalText(argument, "argument");
    }
  }

  record PreflightAcceptedEnvelope(String status, String idempotencyKey, String effectiveDate) {
    public PreflightAcceptedEnvelope {
      status = requireText(status, "status");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
    }
  }

  record CommittedEnvelope(
      String status,
      String postingId,
      String idempotencyKey,
      String effectiveDate,
      String recordedAt) {
    public CommittedEnvelope {
      status = requireText(status, "status");
      postingId = requireText(postingId, "postingId");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
    }
  }

  record RejectedEnvelope(
      String status,
      String code,
      String message,
      @Nullable String idempotencyKey,
      @Nullable Object details) {
    public RejectedEnvelope {
      status = requireText(status, "status");
      code = requireText(code, "code");
      message = requireText(message, "message");
      idempotencyKey = requireOptionalText(idempotencyKey, "idempotencyKey");
    }
  }
}
