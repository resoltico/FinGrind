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
    implements ReplayDetails {}
