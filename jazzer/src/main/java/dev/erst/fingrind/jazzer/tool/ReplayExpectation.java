package dev.erst.fingrind.jazzer.tool;

import java.util.Objects;

/** Captures the stable replay contract expected from one committed FinGrind Jazzer seed. */
public record ReplayExpectation(String outcomeKind, ReplayDetails details) {
  public ReplayExpectation {
    Objects.requireNonNull(outcomeKind, "outcomeKind must not be null");
    if (outcomeKind.isBlank()) {
      throw new IllegalArgumentException("outcomeKind must not be blank");
    }
    Objects.requireNonNull(details, "details must not be null");
  }
}
