package dev.erst.fingrind.core;

import java.util.Objects;

/** The two-axis result of one publication transaction. */
public record PublicationTransactionOutcome(
    PublicationCommitOutcome commit, PublicationCleanupOutcome cleanup) {
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
