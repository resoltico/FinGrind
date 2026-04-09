package dev.erst.fingrind.jazzer.tool;

/** Stable replay details for committed posting-workflow seeds. */
public record PostingWorkflowReplayDetails(
    String requestStatus,
    String effectiveDate,
    String idempotencyKey,
    int lineCount,
    boolean correctionPresent,
    String preflightStatus,
    String firstCommitStatus,
    String duplicateStatus,
    boolean storedFactPresent,
    String failureMessage)
    implements ReplayDetails {}
