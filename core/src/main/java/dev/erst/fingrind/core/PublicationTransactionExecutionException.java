package dev.erst.fingrind.core;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

/** Reports a non-success publication attempt together with its safe transaction recovery handle. */
public final class PublicationTransactionExecutionException extends IOException {
  private static final long serialVersionUID = 1L;

  private final transient PublicationTransactionResult result;
  private final SerializedResult serializedResult;

  /** Retains only the journal identifier and durable outcome, never a staged-secret pathname. */
  public PublicationTransactionExecutionException(
      PublicationTransactionResult result, Throwable cause) {
    super(
        "Publication transaction "
            + Objects.requireNonNull(result, "result").transactionId().value()
            + " did not complete.",
        cause);
    this.result = result;
    this.serializedResult = new SerializedResult(result);
  }

  /** Returns the ID-only recovery handle and durable two-axis outcome. */
  public PublicationTransactionResult result() {
    return result == null ? serializedResult.restore() : result;
  }

  /** Serializable value form because exception serialization cannot rely on record analysis. */
  private record SerializedResult(
      String transactionId,
      PublicationTransactionState state,
      PublicationCommitOutcome commitOutcome,
      PublicationCleanupOutcome cleanupOutcome)
      implements Serializable {
    private static final long serialVersionUID = 1L;

    SerializedResult {
      Objects.requireNonNull(transactionId, "transactionId");
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(commitOutcome, "commitOutcome");
      Objects.requireNonNull(cleanupOutcome, "cleanupOutcome");
    }

    SerializedResult(PublicationTransactionResult result) {
      this(
          Objects.requireNonNull(result, "result").transactionId().value(),
          result.state(),
          result.outcome().commit(),
          result.outcome().cleanup());
    }

    PublicationTransactionResult restore() {
      return new PublicationTransactionResult(
          new PublicationTransactionId(transactionId),
          state,
          new PublicationTransactionOutcome(commitOutcome, cleanupOutcome));
    }
  }
}
