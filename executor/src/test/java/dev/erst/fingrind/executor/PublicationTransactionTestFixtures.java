package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionOutcome;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionState;
import java.nio.file.Path;

/** Creates transaction results for executor tests without exposing transaction storage. */
final class PublicationTransactionTestFixtures {
  private PublicationTransactionTestFixtures() {}

  static PublicationTransactionArtifact completedArtifact(Path path) {
    return new PublicationTransactionArtifact(path, completedResult());
  }

  static PublicationTransactionResult completedResult() {
    return result(
        PublicationTransactionState.COMPLETE,
        PublicationCommitOutcome.ALL_COMMITTED,
        PublicationCleanupOutcome.COMPLETE);
  }

  static PublicationTransactionResult incompleteResult() {
    return result(
        PublicationTransactionState.BLOCKED,
        PublicationCommitOutcome.NONE_COMMITTED,
        PublicationCleanupOutcome.INCOMPLETE);
  }

  private static PublicationTransactionResult result(
      PublicationTransactionState state,
      PublicationCommitOutcome commit,
      PublicationCleanupOutcome cleanup) {
    return new PublicationTransactionResult(
        new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
        state,
        new PublicationTransactionOutcome(commit, cleanup));
  }
}
