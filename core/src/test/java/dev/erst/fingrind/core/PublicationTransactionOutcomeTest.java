package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies that only the documented two-axis completion combination reports success. */
class PublicationTransactionOutcomeTest {
  @Test
  void reportsSuccessOnlyForAllCommittedAndCompleteCleanup() {
    List<PublicationTransactionOutcome> outcomes =
        List.of(
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.COMPLETE),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.INCOMPLETE),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.UNCERTAIN),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.INCOMPLETE),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.UNCERTAIN),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.PARTIALLY_COMMITTED, PublicationCleanupOutcome.COMPLETE),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.PARTIALLY_COMMITTED, PublicationCleanupOutcome.INCOMPLETE),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.PARTIALLY_COMMITTED, PublicationCleanupOutcome.UNCERTAIN),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.COMMIT_UNCERTAIN, PublicationCleanupOutcome.COMPLETE),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.COMMIT_UNCERTAIN, PublicationCleanupOutcome.INCOMPLETE),
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.COMMIT_UNCERTAIN, PublicationCleanupOutcome.UNCERTAIN));

    for (PublicationTransactionOutcome outcome : outcomes) {
      assertEquals(
          outcome.commit() == PublicationCommitOutcome.ALL_COMMITTED
              && outcome.cleanup() == PublicationCleanupOutcome.COMPLETE,
          outcome.successful());
    }
  }
}
