package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PublicationTransactionOutcomeTest {
  @Test
  void reportsSuccessOnlyForAllCommittedAndCompleteCleanup() {
    for (PublicationCommitOutcome commit : PublicationCommitOutcome.values()) {
      for (PublicationCleanupOutcome cleanup : PublicationCleanupOutcome.values()) {
        assertEquals(
            commit == PublicationCommitOutcome.ALL_COMMITTED
                && cleanup == PublicationCleanupOutcome.COMPLETE,
            new PublicationTransactionOutcome(commit, cleanup).successful());
      }
    }
  }
}
