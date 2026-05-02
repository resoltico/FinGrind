package dev.erst.fingrind.core;

import java.util.List;
import java.util.Objects;

/** Aggregated validation failure for one malformed journal entry request. */
public final class JournalEntryValidationException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final List<String> violations;

  /** Creates one exception that carries every detected journal-entry violation. */
  public JournalEntryValidationException(List<String> violations) {
    super(messageFor(violations));
    this.violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
  }

  /** Returns every detected journal-entry violation in deterministic discovery order. */
  public List<String> violations() {
    return violations;
  }

  private static String messageFor(List<String> violations) {
    Objects.requireNonNull(violations, "violations");
    if (violations.isEmpty()) {
      throw new IllegalArgumentException("violations must not be empty.");
    }
    return "Journal entry is invalid: " + String.join(" ", violations);
  }
}
