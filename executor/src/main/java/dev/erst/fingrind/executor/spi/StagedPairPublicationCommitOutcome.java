package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import java.util.Objects;

/** Internal result of one staged protected-book pair commit attempt. */
public sealed interface StagedPairPublicationCommitOutcome
    permits StagedPairPublicationCommitOutcome.Published,
        ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired,
        ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked,
        ProtectedBookPairPublicationFailureOutcome.CompletionUncertain {

  /** Both final members were published, durable, and bound to their retained private stages. */
  record Published(ProtectedBookPairPublicationRetention retention)
      implements StagedPairPublicationCommitOutcome {
    public Published {
      Objects.requireNonNull(retention, "retention");
    }
  }
}
