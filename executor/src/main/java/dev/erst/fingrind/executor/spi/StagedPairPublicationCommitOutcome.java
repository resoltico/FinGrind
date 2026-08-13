package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.core.PublicationTransactionResult;
import java.nio.file.Path;
import java.util.Objects;

/** Internal result of one staged protected-book pair commit attempt. */
public sealed interface StagedPairPublicationCommitOutcome
    permits StagedPairPublicationCommitOutcome.Published,
        StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete,
        ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked {

  /** Both final members were published through one current journal transaction. */
  record Published(ProtectedBookPairPublication publication)
      implements StagedPairPublicationCommitOutcome {
    public Published {
      Objects.requireNonNull(publication, "publication");
    }

    /** Returns the current final-only proof. */
    public ProtectedBookPairPublication requirePublication() {
      return publication;
    }
  }

  /** Reports a journal-owned pair that did not complete and may be recovered only by its ID. */
  record PublicationTransactionIncomplete(
      Path candidateArtifactPath, PublicationTransactionResult transactionResult)
      implements StagedPairPublicationCommitOutcome {
    public PublicationTransactionIncomplete {
      candidateArtifactPath =
          Objects.requireNonNull(candidateArtifactPath, "candidateArtifactPath")
              .toAbsolutePath()
              .normalize();
      Objects.requireNonNull(transactionResult, "transactionResult");
      if (transactionResult.successful()) {
        throw new IllegalArgumentException(
            "A complete publication transaction cannot be reported as incomplete.");
      }
    }
  }
}
