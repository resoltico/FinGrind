package dev.erst.fingrind.jazzer.tool;

/** Stable replay details for committed SQLite round-trip seeds. */
public record SqliteBookRoundTripReplayDetails(
    String requestStatus,
    String effectiveDate,
    String idempotencyKey,
    int lineCount,
    boolean reversalPresent,
    String uninitializedCommitStatus,
    String undeclaredCommitStatus,
    String inactiveCommitStatus,
    String finalCommitStatus,
    String reloadStatus,
    String duplicateStatus,
    boolean storedFactPresent,
    String failureMessage)
    implements ReplayDetails {
  public SqliteBookRoundTripReplayDetails {
    requestStatus = ReplayModelValidation.requireText(requestStatus, "requestStatus");
    effectiveDate = ReplayModelValidation.requireText(effectiveDate, "effectiveDate");
    idempotencyKey = ReplayModelValidation.requireText(idempotencyKey, "idempotencyKey");
    uninitializedCommitStatus =
        ReplayModelValidation.requireText(uninitializedCommitStatus, "uninitializedCommitStatus");
    undeclaredCommitStatus =
        ReplayModelValidation.requireText(undeclaredCommitStatus, "undeclaredCommitStatus");
    inactiveCommitStatus =
        ReplayModelValidation.requireText(inactiveCommitStatus, "inactiveCommitStatus");
    finalCommitStatus = ReplayModelValidation.requireText(finalCommitStatus, "finalCommitStatus");
    reloadStatus = ReplayModelValidation.requireText(reloadStatus, "reloadStatus");
    duplicateStatus = ReplayModelValidation.requireText(duplicateStatus, "duplicateStatus");
    failureMessage = ReplayModelValidation.requireText(failureMessage, "failureMessage");
  }
}
