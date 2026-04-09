package dev.erst.fingrind.jazzer.tool;

/** Stable replay details for committed CLI-request seeds. */
public record CliRequestReplayDetails(
    String requestStatus,
    String effectiveDate,
    String idempotencyKey,
    int lineCount,
    boolean correctionPresent,
    String actorType,
    String sourceChannel,
    String failureMessage)
    implements ReplayDetails {}
