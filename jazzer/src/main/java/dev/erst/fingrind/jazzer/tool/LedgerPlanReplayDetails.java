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
    implements ReplayDetails {}

