package dev.erst.fingrind.jazzer.tool;

import java.util.Objects;

/** Captures the stable replay contract expected from one committed FinGrind Jazzer seed. */
public record ReplayExpectation(
    ReplayOutcomeKind outcomeKind, String message, ReplayDetails details) {
  public ReplayExpectation {
    Objects.requireNonNull(outcomeKind, "outcomeKind must not be null");
    message = ReplayModelValidation.requireText(message, "message");
    Objects.requireNonNull(details, "details must not be null");
  }
}
