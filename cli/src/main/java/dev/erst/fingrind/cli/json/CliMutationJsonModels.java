package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

/** Write-side JSON payloads emitted by the CLI transport layer. */
public interface CliMutationJsonModels {

  record PreflightAcceptedPayload(String idempotencyKey, String effectiveDate)
      implements CliSuccessPayload {
    public PreflightAcceptedPayload {
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
    }
  }

  record CommittedPostingPayload(
      String postingId,
      String idempotencyKey,
      String effectiveDate,
      String recordedAt,
      boolean idempotentReplay)
      implements CliSuccessPayload {
    public CommittedPostingPayload {
      postingId = requireText(postingId, "postingId");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
    }
  }
}
