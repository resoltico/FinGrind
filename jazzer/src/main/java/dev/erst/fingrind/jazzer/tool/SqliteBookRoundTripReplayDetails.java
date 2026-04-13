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
    implements ReplayDetails {}
