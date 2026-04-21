package dev.erst.fingrind.jazzer.tool;

/** Stable replay details for committed posting-workflow seeds. */
public record PostingWorkflowReplayDetails(
    String requestStatus,
    String effectiveDate,
    String idempotencyKey,
    int lineCount,
    boolean reversalPresent,
    String uninitializedPreflightStatus,
    String uninitializedCommitStatus,
    String undeclaredPreflightStatus,
    String undeclaredCommitStatus,
    String inactivePreflightStatus,
    String inactiveCommitStatus,
    String finalPreflightStatus,
    String finalCommitStatus,
    String duplicateStatus,
    boolean storedFactPresent,
    String failureMessage)
    implements ReplayDetails {
  public PostingWorkflowReplayDetails {
    requestStatus = ReplayModelValidation.requireText(requestStatus, "requestStatus");
    effectiveDate = ReplayModelValidation.requireText(effectiveDate, "effectiveDate");
    idempotencyKey = ReplayModelValidation.requireText(idempotencyKey, "idempotencyKey");
    uninitializedPreflightStatus =
        ReplayModelValidation.requireText(
            uninitializedPreflightStatus, "uninitializedPreflightStatus");
    uninitializedCommitStatus =
        ReplayModelValidation.requireText(uninitializedCommitStatus, "uninitializedCommitStatus");
    undeclaredPreflightStatus =
        ReplayModelValidation.requireText(undeclaredPreflightStatus, "undeclaredPreflightStatus");
    undeclaredCommitStatus =
        ReplayModelValidation.requireText(undeclaredCommitStatus, "undeclaredCommitStatus");
    inactivePreflightStatus =
        ReplayModelValidation.requireText(inactivePreflightStatus, "inactivePreflightStatus");
    inactiveCommitStatus =
        ReplayModelValidation.requireText(inactiveCommitStatus, "inactiveCommitStatus");
    finalPreflightStatus =
        ReplayModelValidation.requireText(finalPreflightStatus, "finalPreflightStatus");
    finalCommitStatus = ReplayModelValidation.requireText(finalCommitStatus, "finalCommitStatus");
    duplicateStatus = ReplayModelValidation.requireText(duplicateStatus, "duplicateStatus");
    failureMessage = ReplayModelValidation.requireText(failureMessage, "failureMessage");
  }
}
