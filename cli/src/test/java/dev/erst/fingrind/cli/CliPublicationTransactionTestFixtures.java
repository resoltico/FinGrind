package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionOutcome;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionState;
import java.nio.file.Path;

/** Creates explicit successful public transaction artifacts for CLI projection tests. */
final class CliPublicationTransactionTestFixtures {
  private CliPublicationTransactionTestFixtures() {}

  static PublicationTransactionArtifact completedArtifact(Path publishedArtifactPath) {
    return new PublicationTransactionArtifact(
        publishedArtifactPath,
        new PublicationTransactionResult(
            new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
            PublicationTransactionState.COMPLETE,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE)));
  }
}
