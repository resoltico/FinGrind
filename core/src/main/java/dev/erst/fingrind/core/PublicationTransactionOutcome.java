package dev.erst.fingrind.core;

import java.io.Serializable;
import java.util.Objects;

/** The two-axis result of one publication transaction. */
public record PublicationTransactionOutcome(
    PublicationCommitOutcome commit, PublicationCleanupOutcome cleanup) implements Serializable {
  private static final long serialVersionUID = 1L;

  public PublicationTransactionOutcome {
    Objects.requireNonNull(commit, "commit");
    Objects.requireNonNull(cleanup, "cleanup");
  }

  /** Returns whether every final member committed and no secret stage remains materialized. */
  public boolean successful() {
    return commit == PublicationCommitOutcome.ALL_COMMITTED
        && cleanup == PublicationCleanupOutcome.COMPLETE;
  }
}
