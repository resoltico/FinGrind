package dev.erst.fingrind.jazzer.tool;

/** Stable parsed-command snapshot shared by replayed CLI, posting, and SQLite workflows. */
public record ParsedPostingCommandDetails(
    String effectiveDate, String idempotencyKey, int lineCount, boolean reversalPresent) {
  public ParsedPostingCommandDetails {
    effectiveDate = ReplayModelValidation.requireText(effectiveDate, "effectiveDate");
    idempotencyKey = ReplayModelValidation.requireText(idempotencyKey, "idempotencyKey");
    lineCount = ReplayModelValidation.requireNonNegative(lineCount, "lineCount");
  }
}
