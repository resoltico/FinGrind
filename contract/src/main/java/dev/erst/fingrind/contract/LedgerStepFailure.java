package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;
import java.util.Objects;

/** Structured deterministic failure recorded for one ledger-plan step. */
public record LedgerStepFailure(String code, String message, List<LedgerFact> facts) {
  /** Validates one step failure. */
  public LedgerStepFailure(String code, String message, List<LedgerFact> facts) {
    this.code = Objects.requireNonNull(code, "code");
    this.message = Objects.requireNonNull(message, "message");
    this.facts = ContractDescriptorValidation.copyList(facts, "facts");
    if (code.isBlank()) {
      throw new IllegalArgumentException("Ledger step failure code must not be blank.");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Ledger step failure message must not be blank.");
    }
  }
}
