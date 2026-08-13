package dev.erst.fingrind.core;

import java.io.Serializable;
import java.util.Objects;

/** Reports the journal identifier and independent durable outcomes of one publication attempt. */
public record PublicationTransactionResult(
    PublicationTransactionId transactionId,
    PublicationTransactionState state,
    PublicationTransactionOutcome outcome)
    implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Requires the journal's authenticated identifier, current state, and two-axis outcome. */
  public PublicationTransactionResult {
    Objects.requireNonNull(transactionId, "transactionId");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(outcome, "outcome");
    if (state == PublicationTransactionState.COMPLETE && !outcome.successful()) {
      throw new IllegalArgumentException(
          "A complete publication transaction result must be successful.");
    }
  }

  /** Returns true only after all finals commit and every secret stage is cleaned. */
  public boolean successful() {
    return state == PublicationTransactionState.COMPLETE;
  }
}
