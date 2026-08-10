package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionOutcome;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionState;
import java.nio.file.Path;

/** Creates successful transaction-owned artifacts for contract value tests. */
final class ContractPublicationTransactionFixtures {
  private ContractPublicationTransactionFixtures() {}

  static PublicationTransactionArtifact completedArtifact(Path path) {
    return new PublicationTransactionArtifact(
        path,
        new PublicationTransactionResult(
            new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
            PublicationTransactionState.COMPLETE,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE)));
  }
}
