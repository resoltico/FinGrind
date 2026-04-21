package dev.erst.fingrind.jazzer.tool;

/** Stable replay details for committed CLI-request seeds. */
public record CliRequestReplayDetails(
    String requestStatus,
    String effectiveDate,
    String idempotencyKey,
    int lineCount,
    boolean reversalPresent,
    String actorType,
    String sourceChannel,
    String failureMessage)
    implements ReplayDetails {
  public CliRequestReplayDetails {
    requestStatus = ReplayModelValidation.requireText(requestStatus, "requestStatus");
    effectiveDate = ReplayModelValidation.requireText(effectiveDate, "effectiveDate");
    idempotencyKey = ReplayModelValidation.requireText(idempotencyKey, "idempotencyKey");
    actorType = ReplayModelValidation.requireText(actorType, "actorType");
    sourceChannel = ReplayModelValidation.requireText(sourceChannel, "sourceChannel");
    failureMessage = ReplayModelValidation.requireText(failureMessage, "failureMessage");
  }
}
