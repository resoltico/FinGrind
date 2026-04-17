package dev.erst.fingrind.contract;

import java.util.Objects;

/** Explicit execution rules for one AI-agent-authored ledger plan. */
public record LedgerExecutionPolicy(
    LedgerJournalLevel journalLevel,
    LedgerFailurePolicy failurePolicy,
    LedgerTransactionMode transactionMode) {
  /** Validates one execution policy. */
  public LedgerExecutionPolicy {
    Objects.requireNonNull(journalLevel, "journalLevel");
    Objects.requireNonNull(failurePolicy, "failurePolicy");
    Objects.requireNonNull(transactionMode, "transactionMode");
  }
}
