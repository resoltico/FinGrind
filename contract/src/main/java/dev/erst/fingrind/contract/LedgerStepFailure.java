package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;

/** Structured deterministic failure recorded for one ledger-plan step. */
public record LedgerStepFailure(String code, String message, List<LedgerFact> facts) {
  /** Validates one step failure. */
  public LedgerStepFailure {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
    if (code.isBlank()) {
      throw new IllegalArgumentException("Ledger step failure code must not be blank.");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Ledger step failure message must not be blank.");
    }
  }
}
