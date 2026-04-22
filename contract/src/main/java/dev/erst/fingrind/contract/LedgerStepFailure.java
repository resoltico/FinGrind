package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Structured deterministic failure recorded for one ledger-plan step. */
public record LedgerStepFailure(String code, String message, List<LedgerFact> facts) {
  /** Validates one step failure. */
  public LedgerStepFailure(String code, String message, @Nullable List<LedgerFact> facts) {
    this.code = Objects.requireNonNull(code, "code");
    this.message = Objects.requireNonNull(message, "message");
    this.facts = facts == null ? List.of() : List.copyOf(facts);
    if (code.isBlank()) {
      throw new IllegalArgumentException("Ledger step failure code must not be blank.");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Ledger step failure message must not be blank.");
    }
  }
}
