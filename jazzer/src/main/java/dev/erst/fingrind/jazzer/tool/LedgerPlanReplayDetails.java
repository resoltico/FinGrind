package dev.erst.fingrind.jazzer.tool;

/** Stable replay details for committed ledger-plan request seeds. */
public record LedgerPlanReplayDetails(
    String requestStatus,
    String planId,
    int stepCount,
    String firstStepKind,
    String lastStepKind,
    int assertionStepCount,
    boolean beginsWithOpenBook,
    String executionStatus,
    int journalStepCount,
    int listQueryStepCount,
    int structuredListQueryStepCount,
    String failureMessage)
    implements ReplayDetails {
  public LedgerPlanReplayDetails {
    requestStatus = ReplayModelValidation.requireText(requestStatus, "requestStatus");
    planId = ReplayModelValidation.requireText(planId, "planId");
    firstStepKind = ReplayModelValidation.requireText(firstStepKind, "firstStepKind");
    lastStepKind = ReplayModelValidation.requireText(lastStepKind, "lastStepKind");
    executionStatus = ReplayModelValidation.requireText(executionStatus, "executionStatus");
    stepCount = ReplayModelValidation.requireNonNegative(stepCount, "stepCount");
    assertionStepCount = ReplayModelValidation.requireNonNegative(assertionStepCount, "assertionStepCount");
    journalStepCount = ReplayModelValidation.requireNonNegative(journalStepCount, "journalStepCount");
    listQueryStepCount = ReplayModelValidation.requireNonNegative(listQueryStepCount, "listQueryStepCount");
    structuredListQueryStepCount =
        ReplayModelValidation.requireNonNegative(
            structuredListQueryStepCount, "structuredListQueryStepCount");
    if (structuredListQueryStepCount > listQueryStepCount) {
      throw new IllegalArgumentException(
          "structuredListQueryStepCount must not exceed listQueryStepCount");
    }
    failureMessage = ReplayModelValidation.requireText(failureMessage, "failureMessage");
  }
}
