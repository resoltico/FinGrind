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
    String failureMessage)
    implements ReplayDetails {
  public LedgerPlanReplayDetails {
    requestStatus = ReplayModelValidation.requireText(requestStatus, "requestStatus");
    planId = ReplayModelValidation.requireText(planId, "planId");
    firstStepKind = ReplayModelValidation.requireText(firstStepKind, "firstStepKind");
    lastStepKind = ReplayModelValidation.requireText(lastStepKind, "lastStepKind");
    failureMessage = ReplayModelValidation.requireText(failureMessage, "failureMessage");
  }
}
